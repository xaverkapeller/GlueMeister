package com.github.wrdlbrnft.gluemeister.modules.factories;

import com.github.wrdlbrnft.codebuilder.annotations.Annotations;
import com.github.wrdlbrnft.codebuilder.code.Block;
import com.github.wrdlbrnft.codebuilder.code.CodeElement;
import com.github.wrdlbrnft.codebuilder.executables.Constructor;
import com.github.wrdlbrnft.codebuilder.executables.ExecutableBuilder;
import com.github.wrdlbrnft.codebuilder.executables.Method;
import com.github.wrdlbrnft.codebuilder.executables.Methods;
import com.github.wrdlbrnft.codebuilder.implementations.Implementation;
import com.github.wrdlbrnft.codebuilder.types.Type;
import com.github.wrdlbrnft.codebuilder.types.Types;
import com.github.wrdlbrnft.codebuilder.variables.Field;
import com.github.wrdlbrnft.codebuilder.variables.Variable;
import com.github.wrdlbrnft.codebuilder.variables.Variables;
import com.github.wrdlbrnft.gluemeister.GlueInject;
import com.github.wrdlbrnft.gluemeister.GlueMeisterException;
import com.github.wrdlbrnft.gluemeister.glueable.GlueableInfo;
import com.github.wrdlbrnft.gluemeister.modules.exceptions.GlueModuleConstructorNotSatisfiedException;
import com.github.wrdlbrnft.gluemeister.modules.exceptions.GlueModuleFactoryException;
import com.github.wrdlbrnft.gluemeister.utils.ElementUtils;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

class ClassResolver {

    private final Map<GlueableInfo, CodeElement> mResolvedElementsMap;

    private final Map<String, GlueableInfo> mTypeParameterMap = new HashMap<String, GlueableInfo>();
    private final List<FieldInfo> mFields;

    private final ProcessingEnvironment mProcessingEnvironment;
    private final Implementation.Builder mBuilder;

    private final TypeElement mClassElement;
    private DeclaredType mDeclaredType;

    ClassResolver(ProcessingEnvironment processingEnvironment, Map<GlueableInfo, CodeElement> resolvedElementsMap, List<FieldInfo> fields, Implementation.Builder builder, TypeElement classElement) {
        mResolvedElementsMap = resolvedElementsMap;
        mProcessingEnvironment = processingEnvironment;
        mFields = fields;
        mBuilder = builder;
        mClassElement = classElement;
    }

    ResolveResult resolveClass(List<GlueableInfo> glueables) {
        final List<? extends TypeParameterElement> typeParameters = mClassElement.getTypeParameters();
        final TypeMirror[] args = new TypeMirror[typeParameters.size()];
        for (int i = 0, size = typeParameters.size(); i < size; i++) {
            final TypeParameterElement typeParameter = typeParameters.get(i);
            final GlueableInfo info = findGlueableForTypeParameter(typeParameter, glueables);
            args[i] = getTypeMirrorOfGlueable(info);
            mTypeParameterMap.put(typeParameter.getSimpleName().toString(), info);
        }

        mDeclaredType = mProcessingEnvironment.getTypeUtils().getDeclaredType(mClassElement, args);

        final List<ExecutableElement> abstractMethods = ElementUtils.determineAbstractMethods(mProcessingEnvironment, mClassElement);
        final Type classType = typeParameters.isEmpty()
                ? Types.of(mClassElement)
                : Types.generic(Types.of(mClassElement), resolveTypeParameters(typeParameters));

        if (abstractMethods.isEmpty()) {
            return new ResolveResultImpl(classType, mClassElement.asType(), classType.newInstance(resolveTypeConstructorParameters(mClassElement, glueables)));
        }

        return resolveAbstractClass(mClassElement, glueables, abstractMethods, classType);
    }

    private ResolveResult resolveAbstractClass(TypeElement classElement, List<GlueableInfo> glueables, List<ExecutableElement> abstractMethods, Type classType) {
        final Implementation.Builder resolvedClassBuilder = new Implementation.Builder();
        resolvedClassBuilder.setModifiers(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC));
        if (classElement.getKind() == ElementKind.INTERFACE) {
            resolvedClassBuilder.addImplementedType(classType);
        } else if (classElement.getKind() == ElementKind.CLASS) {
            resolvedClassBuilder.setExtendedType(classType);

            final List<Constructor> constructors = createConstructorsForSubclass(classElement);
            constructors.forEach(resolvedClassBuilder::addConstructor);
        }

        final List<Method> missingMethods = implementAbstractMethods(abstractMethods, glueables);
        missingMethods.forEach(resolvedClassBuilder::addMethod);

        final Implementation implementation = resolvedClassBuilder.build();
        mBuilder.addNestedImplementation(implementation);

        return new ResolveResultImpl(
                implementation,
                classElement.asType(),
                implementation.newInstance(resolveTypeConstructorParameters(classElement, glueables))
        );
    }

    private List<Method> implementAbstractMethods(List<ExecutableElement> abstractMethods, List<GlueableInfo> glueables) {
        return abstractMethods.stream()
                .map(method -> {
                    final ExecutableType executableType = (ExecutableType) mProcessingEnvironment.getTypeUtils().asMemberOf(mDeclaredType, method);
                    final Element returnTypeElement = mProcessingEnvironment.getTypeUtils().asElement(executableType.getReturnType());
                    final GlueableInfo glueableInfo = findGlueableForElement(returnTypeElement, glueables);
                    final CodeElement resolvedMethodValue = resolveGlueableCodeElement(glueableInfo, glueables);

                    return new Method.Builder()
                            .setName(method.getSimpleName().toString())
                            .addAnnotation(Annotations.forType(Override.class))
                            .setReturnType(Types.of(executableType.getReturnType()))
                            .setModifiers(method.getModifiers().stream()
                                    .filter(modifier -> modifier != Modifier.ABSTRACT)
                                    .collect(Collectors.toSet()))
                            .setCode(new ExecutableBuilder() {
                                @Override
                                protected List<Variable> createParameters() {
                                    return Collections.emptyList();
                                }

                                @Override
                                protected void write(Block block) {
                                    block.append("return ").append(resolvedMethodValue).append(";");
                                }
                            })
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<Constructor> createConstructorsForSubclass(TypeElement classElement) {
        return classElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast)
                .map(constructor -> {
                    final List<? extends VariableElement> constructorParameters = constructor.getParameters();
                    final List<Variable> parameters = constructorParameters.stream()
                            .map(p -> Variables.of(Types.of(resolveTypeMirror(p.asType()))))
                            .collect(Collectors.toList());
                    return new Constructor.Builder()
                            .setModifiers(constructor.getModifiers())
                            .setCode(new ExecutableBuilder() {
                                @Override
                                protected List<Variable> createParameters() {
                                    return parameters;
                                }

                                @Override
                                protected void write(Block block) {
                                    block.append(Methods.SUPER.call(parameters.toArray(new CodeElement[parameters.size()]))).append(";");
                                }
                            })
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Type[] resolveTypeParameters(List<? extends TypeParameterElement> typeParameters) {
        return typeParameters.stream()
                .map(Element::asType)
                .map(this::resolveTypeMirror)
                .map(Types::of)
                .toArray(Type[]::new);
    }

    private TypeMirror resolveTypeMirror(TypeMirror mirror) {
        switch (mirror.getKind()) {

            case TYPEVAR:
                final GlueableInfo typeVarInfo = mTypeParameterMap.get(mirror.toString());
                if (typeVarInfo == null) {
                    throw new GlueModuleFactoryException("Failed to resolve Type Parameter: " + mirror, null);
                }
                return resolveTypeMirror(typeVarInfo.getElement().asType());

            default:
                return mirror;
        }
    }

    private CodeElement[] resolveTypeConstructorParameters(TypeElement entityElement, List<GlueableInfo> glueables) {
        if (entityElement.getKind() == ElementKind.INTERFACE) {
            return new CodeElement[0];
        }

        final List<ExecutableElement> constructors = entityElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast)
                .sorted((a, b) -> Integer.signum(b.getParameters().size() - a.getParameters().size()))
                .collect(Collectors.toList());

        for (ExecutableElement constructor : constructors) {
            if (!ElementUtils.hasPublicOrPackageLocalVisibility(constructor)) {
                continue;
            }

            try {
                return constructor.getParameters().stream()
                        .map(parameter -> findGlueableForElement(parameter, glueables))
                        .map(info -> resolveGlueableCodeElement(info, glueables))
                        .toArray(CodeElement[]::new);
            } catch (GlueModuleConstructorNotSatisfiedException e) {
                mProcessingEnvironment.getMessager().printMessage(
                        Diagnostic.Kind.NOTE,
                        e.getMessage(),
                        e.getElement()
                );
            }
        }

        throw new GlueModuleFactoryException("GlueMeister cannot create instances of " + entityElement.getSimpleName() + " because there is no constructor whose parameters can be satisfied. Look at the previous warnings to figure out why.", entityElement);
    }

    private GlueableInfo findGlueableForElement(Element parameter, List<GlueableInfo> glueables) {
        final GlueInject glueInject = parameter.getAnnotation(GlueInject.class);
        if (glueInject != null) {
            final String key = glueInject.value();
            if (!key.trim().isEmpty()) {
                return glueables.stream()
                        .filter(info -> key.equals(info.getKey()))
                        .findAny().orElseThrow(() -> new GlueModuleConstructorNotSatisfiedException("Parameter " + parameter.getSimpleName() + " cannot be injected by GlueMeister. No component annotated with @Glueable matches the key \"" + key + "\". Make sure there is an @Glueable component which can be used to satisfy it.", parameter));
            }
        }

        final TypeMirror parameterTypeMirror = parameter.asType();
        return glueables.stream()
                .filter(info -> {
                    final Element element = info.getElement();
                    final TypeMirror glueableTypeMirror = element.getKind() == ElementKind.METHOD
                            ? ((ExecutableElement) element).getReturnType()
                            : element.asType();
                    return isAssignable(glueableTypeMirror, parameterTypeMirror);
                })
                .findAny().orElseThrow(() -> new GlueModuleConstructorNotSatisfiedException("Parameter " + parameter.getSimpleName() + " cannot be injected by GlueMeister. Make sure there is an @Glueable component which can be used to satisfy it.", parameter));
    }

    private CodeElement resolveGlueableCodeElement(GlueableInfo glueableInfo, List<GlueableInfo> glueables) {
        if (mResolvedElementsMap.containsKey(glueableInfo)) {
            return mResolvedElementsMap.get(glueableInfo);
        }

        final CodeElement codeElement = translateGlueableInfoToCodeElement(glueableInfo, glueables);
        mResolvedElementsMap.put(glueableInfo, codeElement);
        return codeElement;
    }

    private CodeElement translateGlueableInfoToCodeElement(GlueableInfo glueableInfo, List<GlueableInfo> glueables) {
        switch (glueableInfo.getKind()) {

            case STATIC_FIELD:
                final VariableElement fieldElement = (VariableElement) glueableInfo.getElement();
                return resolveField(fieldElement);

            case CLASS:
            case ABSTRACT_CLASS:
            case INTERFACE:
                final TypeElement classElement = (TypeElement) glueableInfo.getElement();
                return getFieldFor(classElement.asType(), () -> {
                    final ClassResolver subClassResolver = new ClassResolver(mProcessingEnvironment, mResolvedElementsMap, mFields, mBuilder, classElement);
                    final ResolveResult resolvedClass = subClassResolver.resolveClass(glueables);
                    return new Field.Builder()
                            .setModifiers(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL))
                            .setType(resolvedClass.getType())
                            .setInitialValue(resolvedClass.getValue())
                            .build();
                });

            case INSTANCE_METHOD:
                final ExecutableElement instanceMethodElement = (ExecutableElement) glueableInfo.getElement();
                return resolveInstanceMethod(instanceMethodElement, glueables);

            case STATIC_METHOD:
                throw new GlueModuleConstructorNotSatisfiedException("Injecting this component is not yet supported. Look for an update!", glueableInfo.getElement());

            default:
                throw new GlueMeisterException("Encountered unknown GlueableType: " + glueableInfo.getKind(), glueableInfo.getElement());
        }
    }

    private Field getFieldFor(TypeMirror typeMirror, Supplier<Field> fieldSupplier) {
        for (FieldInfo field : mFields) {
            final TypeMirror fieldType = field.getTypeMirror();
            if (isAssignable(typeMirror, fieldType)) {
                return field.getField();
            }
        }
        final Field field = fieldSupplier.get();
        mBuilder.addField(field);
        mFields.add(new FieldInfo() {
            @Override
            public TypeMirror getTypeMirror() {
                return typeMirror;
            }

            @Override
            public Field getField() {
                return field;
            }
        });
        return field;
    }

    private CodeElement resolveInstanceMethod(ExecutableElement methodElement, List<GlueableInfo> glueables) {
        final TypeElement parent = (TypeElement) methodElement.getEnclosingElement();
        final ClassResolver parentResolver = new ClassResolver(mProcessingEnvironment, mResolvedElementsMap, mFields, mBuilder, parent);
        final ResolveResult result = parentResolver.resolveClass(glueables);
        final Field field = getFieldFor(result.getBaseTypeMirror(), () -> new Field.Builder()
                .setModifiers(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL))
                .setType(result.getType())
                .setInitialValue(result.getValue())
                .build());
        return Methods.call(methodElement, field);
    }

    private CodeElement resolveField(VariableElement fieldElement) {
        final TypeElement enclosingElement = (TypeElement) fieldElement.getEnclosingElement();
        return new Block().append(Types.of(enclosingElement)).append(".").append(fieldElement.getSimpleName().toString());
    }

    private GlueableInfo findGlueableForTypeParameter(TypeParameterElement parameter, List<GlueableInfo> glueables) {
        return glueables.stream()
                .filter(info -> {
                    final TypeMirror glueableTypeMirror = getTypeMirrorOfGlueable(info);
                    for (TypeMirror boundType : parameter.getBounds()) {
                        if (!isAssignable(glueableTypeMirror, boundType)) {
                            return false;
                        }
                    }
                    return true;
                })
                .findAny().orElseThrow(() -> new GlueModuleConstructorNotSatisfiedException("Type Parameter " + parameter.getSimpleName() + " cannot be injected by GlueMeister. Make sure there is an @Glueable component which can be used to satisfy it.", parameter));
    }

    private TypeMirror getTypeMirrorOfGlueable(GlueableInfo info) {
        switch (info.getKind()) {

            case STATIC_FIELD:
                final VariableElement fieldElement = (VariableElement) info.getElement();
                return fieldElement.asType();

            case INTERFACE:
            case ABSTRACT_CLASS:
            case CLASS:
                final TypeElement classElement = (TypeElement) info.getElement();
                return classElement.asType();

            case INSTANCE_METHOD:
            case STATIC_METHOD:
                final ExecutableElement executableElement = (ExecutableElement) info.getElement();
                return executableElement.getReturnType();

            default:
                throw new GlueMeisterException("Encountered unknown Glueable Kind: " + info.getKind(), null);
        }
    }

    private boolean isAssignable(TypeMirror a, TypeMirror b) {
        final TypeMirror resolvedA = resolveTypeMirror(a);
        final TypeMirror resolvedB = resolveTypeMirror(b);
        return mProcessingEnvironment.getTypeUtils().isAssignable(
                mProcessingEnvironment.getTypeUtils().erasure(resolvedA),
                mProcessingEnvironment.getTypeUtils().erasure(resolvedB)
        );
    }

    private static class ResolveResultImpl implements ResolveResult {

        private final Type mType;
        private final TypeMirror mBaseTypeMirror;
        private final CodeElement mValue;

        private ResolveResultImpl(Type type, TypeMirror baseTypeMirror, CodeElement value) {
            mType = type;
            mBaseTypeMirror = baseTypeMirror;
            mValue = value;
        }

        @Override
        public Type getType() {
            return mType;
        }

        @Override
        public TypeMirror getBaseTypeMirror() {
            return mBaseTypeMirror;
        }

        @Override
        public CodeElement getValue() {
            return mValue;
        }
    }
}
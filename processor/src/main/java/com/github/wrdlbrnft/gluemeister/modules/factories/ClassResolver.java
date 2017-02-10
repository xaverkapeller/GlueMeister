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
import com.github.wrdlbrnft.codebuilder.util.Utils;
import com.github.wrdlbrnft.codebuilder.variables.Field;
import com.github.wrdlbrnft.codebuilder.variables.Variable;
import com.github.wrdlbrnft.codebuilder.variables.Variables;
import com.github.wrdlbrnft.gluemeister.GlueCollect;
import com.github.wrdlbrnft.gluemeister.GlueFactory;
import com.github.wrdlbrnft.gluemeister.GlueInject;
import com.github.wrdlbrnft.gluemeister.GlueMeisterException;
import com.github.wrdlbrnft.gluemeister.glueable.GlueableInfo;
import com.github.wrdlbrnft.gluemeister.modules.exceptions.GlueModuleConstructorNotSatisfiedException;
import com.github.wrdlbrnft.gluemeister.modules.exceptions.GlueModuleFactoryException;
import com.github.wrdlbrnft.gluemeister.utils.ElementUtils;

import java.util.ArrayList;
import java.util.Arrays;
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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

class ClassResolver {

    private static final Type TYPE_ARRAYS = Types.of("java.util", "Arrays");
    private static final Method METHOD_AS_LIST = Methods.stub("asList");
    private static final Method METHOD_EMPTY_LIST = Methods.stub("emptyList");

    private final Map<GlueableInfo, CodeElement> mResolvedElementsMap;

    private final Map<String, GlueableInfo> mTypeParameterMap = new HashMap<>();

    private final List<FieldInfo> mNestedClassFields = new ArrayList<>();
    private Implementation.Builder mNestedClassBuilder;

    private final List<FieldInfo> mFactoryFields;

    private final ProcessingEnvironment mProcessingEnvironment;
    private final Implementation.Builder mFactoryBuilder;

    private final TypeElement mClassElement;

    private final TypeElement mListTypeElement;

    private DeclaredType mDeclaredType;


    ClassResolver(ProcessingEnvironment processingEnvironment, Map<GlueableInfo, CodeElement> resolvedElementsMap, List<FieldInfo> factoryFields, Implementation.Builder factoryBuilder, TypeElement classElement) {
        mResolvedElementsMap = resolvedElementsMap;
        mProcessingEnvironment = processingEnvironment;
        mFactoryFields = factoryFields;
        mFactoryBuilder = factoryBuilder;
        mClassElement = classElement;
        mListTypeElement = mProcessingEnvironment.getElementUtils().getTypeElement("java.util.List");
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
            return new ResolveResultImpl(
                    Types.of(mDeclaredType),
                    mDeclaredType,
                    classType.newInstance(resolveTypeConstructorParameters(mClassElement, glueables))
            );
        }

        return resolveAbstractClass(mClassElement, glueables, abstractMethods, classType);
    }

    private ResolveResult resolveAbstractClass(TypeElement classElement, List<GlueableInfo> glueables, List<ExecutableElement> abstractMethods, Type classType) {
        mNestedClassBuilder = new Implementation.Builder();
        mNestedClassBuilder.setModifiers(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC));
        if (classElement.getKind() == ElementKind.INTERFACE) {
            mNestedClassBuilder.addImplementedType(classType);
        } else if (classElement.getKind() == ElementKind.CLASS) {
            mNestedClassBuilder.setExtendedType(classType);

            final List<Constructor> constructors = createConstructorsForSubclass(classElement);
            constructors.forEach(mNestedClassBuilder::addConstructor);
        }

        final List<Method> missingMethods = implementAbstractMethods(abstractMethods, glueables);
        missingMethods.forEach(mNestedClassBuilder::addMethod);

        final Implementation implementation = mNestedClassBuilder.build();
        mFactoryBuilder.addNestedImplementation(implementation);

        return new ResolveResultImpl(
                Types.of(mDeclaredType),
                mDeclaredType,
                implementation.newInstance(resolveTypeConstructorParameters(classElement, glueables))
        );
    }

    private List<Method> implementAbstractMethods(List<ExecutableElement> abstractMethods, List<GlueableInfo> glueables) {
        return abstractMethods.stream()
                .map(method -> {
                    final ExecutableType executableType = (ExecutableType) mProcessingEnvironment.getTypeUtils().asMemberOf(mDeclaredType, method);
                    final CodeElement resolvedMethodValue = resolveMethodReturnValue(glueables, method, executableType);

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

    private CodeElement resolveMethodReturnValue(List<GlueableInfo> glueables, ExecutableElement method, ExecutableType executableType) {
        final GlueCollect glueCollectAnnotation = method.getAnnotation(GlueCollect.class);
        final GlueFactory glueFactoryAnnotation = method.getAnnotation(GlueFactory.class);
        final boolean isFactory = glueFactoryAnnotation != null;
        if (glueCollectAnnotation != null) {
            final TypeMirror returnType = executableType.getReturnType();
            final TypeMirror erasedReturnType = mProcessingEnvironment.getTypeUtils().erasure(returnType);
            if (!mProcessingEnvironment.getTypeUtils().isAssignable(erasedReturnType, mListTypeElement.asType())) {
                throw new GlueModuleFactoryException("You can use GlueCollect only on methods which return a List! " + method + " does not.", method);
            }

            final List<TypeMirror> typeParameters = Utils.getTypeParameters(returnType);
            System.out.println("LOL: " + typeParameters);
            final TypeElement typeParameterElement = (TypeElement) mProcessingEnvironment.getTypeUtils().asElement(typeParameters.get(0));
            final List<GlueableInfo> matchingGlueables = findAllGlueablesForElement(typeParameterElement, glueables);
            System.out.println("LEL: " + matchingGlueables);

            if (matchingGlueables.isEmpty()) {
                return METHOD_EMPTY_LIST.callOnTarget(Types.COLLECTIONS);
            }

            final CodeElement[] resolvedGlueables = matchingGlueables.stream()
                    .map(info -> resolveGlueableCodeElement(info, glueables, isFactory))
                    .toArray(CodeElement[]::new);
            System.out.println("LIL: " + Arrays.toString(resolvedGlueables));
            return METHOD_AS_LIST.callOnTarget(TYPE_ARRAYS, resolvedGlueables);
        }

        final Element returnTypeElement = mProcessingEnvironment.getTypeUtils().asElement(executableType.getReturnType());
        final GlueableInfo glueableInfo = findGlueableForElement(returnTypeElement, glueables);
        return resolveGlueableCodeElement(glueableInfo, glueables, isFactory);
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
                        .map(info -> resolveGlueableCodeElement(info, glueables, false))
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
                .sorted((a, b) -> {
                    
                    final Element aElement = a.getElement();
                    final TypeMirror aType = aElement.getKind() == ElementKind.METHOD
                            ? ((ExecutableElement) aElement).getReturnType()
                            : aElement.asType();
                    
                    final Element bElement = b.getElement();
                    final TypeMirror bType = bElement.getKind() == ElementKind.METHOD
                            ? ((ExecutableElement) bElement).getReturnType()
                            : bElement.asType();

                    final boolean aToB = isAssignable(aType, bType);
                    final boolean bToA = isAssignable(bType, aType);

                    if (aToB && bToA) {
                        return 0;
                    }

                    if (aToB) {
                        return -1;
                    }

                    if (bToA) {
                        return 1;
                    }

                    return 0;
                })
                .findFirst().orElseThrow(() -> new GlueModuleConstructorNotSatisfiedException("Parameter " + parameter.getSimpleName() + " cannot be injected by GlueMeister. Make sure there is an @Glueable component which can be used to satisfy it.", parameter));
    }

    private List<GlueableInfo> findAllGlueablesForElement(Element parameter, List<GlueableInfo> glueables) {
        final GlueInject glueInject = parameter.getAnnotation(GlueInject.class);
        if (glueInject != null) {
            final String key = glueInject.value();
            if (!key.trim().isEmpty()) {
                return glueables.stream()
                        .filter(info -> key.equals(info.getKey()))
                        .collect(Collectors.toList());
            }
        }

        final TypeMirror parameterTypeMirror = parameter.asType();
        System.out.println("KEK: " + parameterTypeMirror);
        return glueables.stream()
                .filter(info -> {
                    final Element element = info.getElement();
                    final TypeMirror glueableTypeMirror = element.getKind() == ElementKind.METHOD
                            ? ((ExecutableElement) element).getReturnType()
                            : element.asType();
                    System.out.println("TOP: " + glueableTypeMirror);
                    return isAssignable(glueableTypeMirror, parameterTypeMirror);
                })
                .collect(Collectors.toList());
    }

    private CodeElement resolveGlueableCodeElement(GlueableInfo glueableInfo, List<GlueableInfo> glueables, boolean factory) {
        if (mResolvedElementsMap.containsKey(glueableInfo)) {
            return mResolvedElementsMap.get(glueableInfo);
        }

        final CodeElement codeElement = translateGlueableInfoToCodeElement(glueableInfo, glueables, factory);
        mResolvedElementsMap.put(glueableInfo, codeElement);
        return codeElement;
    }

    private CodeElement translateGlueableInfoToCodeElement(GlueableInfo glueableInfo, List<GlueableInfo> glueables, boolean factory) {
        switch (glueableInfo.getKind()) {

            case STATIC_FIELD:
                final VariableElement fieldElement = (VariableElement) glueableInfo.getElement();
                return resolveField(fieldElement);

            case CLASS:
            case ABSTRACT_CLASS:
            case INTERFACE:
                final TypeElement classElement = (TypeElement) glueableInfo.getElement();
                if (factory) {
                    final ClassResolver subClassResolver = new ClassResolver(mProcessingEnvironment, mResolvedElementsMap, mFactoryFields, mFactoryBuilder, classElement);
                    final ResolveResult resolvedClass = subClassResolver.resolveClass(glueables);
                    return resolvedClass.getValue();
                }
                return getFieldInFactoryFor(classElement.asType(), () -> {
                    final ClassResolver subClassResolver = new ClassResolver(mProcessingEnvironment, mResolvedElementsMap, mFactoryFields, mFactoryBuilder, classElement);
                    final ResolveResult resolvedClass = subClassResolver.resolveClass(glueables);
                    return new Field.Builder()
                            .setModifiers(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL))
                            .setType(resolvedClass.getType())
                            .setInitialValue(resolvedClass.getValue())
                            .build();
                });

            case INSTANCE_METHOD:
                final ExecutableElement instanceMethodElement = (ExecutableElement) glueableInfo.getElement();
                return resolveInstanceMethod(instanceMethodElement, glueables, factory);

            case STATIC_METHOD:
                throw new GlueModuleConstructorNotSatisfiedException("Injecting this component is not yet supported. Look for an update!", glueableInfo.getElement());

            default:
                throw new GlueMeisterException("Encountered unknown GlueableType: " + glueableInfo.getKind(), glueableInfo.getElement());
        }
    }

    private Field getFieldInFactoryFor(TypeMirror typeMirror, Supplier<Field> fieldSupplier) {
        for (FieldInfo field : mFactoryFields) {
            final TypeMirror fieldType = field.getTypeMirror();
            if (isAssignable(typeMirror, fieldType)) {
                return field.getField();
            }
        }
        final Field field = fieldSupplier.get();
        mFactoryBuilder.addField(field);
        mFactoryFields.add(new FieldInfoImpl(typeMirror, field));
        return field;
    }

    private Field getFieldInNestedClassFor(TypeMirror typeMirror, Supplier<Field> fieldSupplier) {
        for (FieldInfo field : mNestedClassFields) {
            final TypeMirror fieldType = field.getTypeMirror();
            if (isAssignable(typeMirror, fieldType)) {
                return field.getField();
            }
        }
        final Field field = fieldSupplier.get();
        mNestedClassBuilder.addField(field);
        mNestedClassFields.add(new FieldInfoImpl(typeMirror, field));
        return field;
    }

    private CodeElement resolveInstanceMethod(ExecutableElement method, List<GlueableInfo> glueables, boolean factory) {
        final TypeElement parent = (TypeElement) method.getEnclosingElement();
        final ClassResolver parentResolver = new ClassResolver(mProcessingEnvironment, mResolvedElementsMap, mFactoryFields, mFactoryBuilder, parent);
        final ResolveResult result = parentResolver.resolveClass(glueables);
        final Field parentField = getFieldInFactoryFor(result.getBaseType(), () -> new Field.Builder()
                .setModifiers(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL))
                .setType(result.getType())
                .setInitialValue(result.getValue())
                .build());

        final TypeMirror returnType = resolveReturnType(method);
        final CodeElement resultElement = Methods.call(method, parentField);
        if (factory) {
            return resultElement;
        }

        return getFieldInNestedClassFor(returnType, () -> new Field.Builder()
                .setModifiers(EnumSet.of(Modifier.PRIVATE, Modifier.FINAL))
                .setType(Types.of(returnType))
                .setInitialValue(resultElement)
                .build());
    }

    private TypeMirror resolveReturnType(ExecutableElement method) {

        if (method.getReturnType().getKind() == TypeKind.TYPEVAR) {
            final ExecutableType executableType = (ExecutableType) mProcessingEnvironment.getTypeUtils().asMemberOf(mDeclaredType, method);
            return executableType.getReturnType();
        }

        return method.getReturnType();
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
        private final DeclaredType mBaseType;
        private final CodeElement mValue;

        private ResolveResultImpl(Type type, DeclaredType baseType, CodeElement value) {
            mType = type;
            mBaseType = baseType;
            mValue = value;
        }

        @Override
        public Type getType() {
            return mType;
        }

        public DeclaredType getBaseType() {
            return mBaseType;
        }

        @Override
        public CodeElement getValue() {
            return mValue;
        }
    }

    private static class FieldInfoImpl implements FieldInfo {

        private final TypeMirror mTypeMirror;
        private final Field mField;

        private FieldInfoImpl(TypeMirror typeMirror, Field field) {
            mTypeMirror = typeMirror;
            mField = field;
        }

        @Override
        public TypeMirror getTypeMirror() {
            return mTypeMirror;
        }

        @Override
        public Field getField() {
            return mField;
        }
    }
}

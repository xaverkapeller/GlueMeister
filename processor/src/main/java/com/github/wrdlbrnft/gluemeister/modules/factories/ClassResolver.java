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
import com.github.wrdlbrnft.gluemeister.CacheSetting;
import com.github.wrdlbrnft.gluemeister.GlueMeisterException;
import com.github.wrdlbrnft.gluemeister.GlueSettings;
import com.github.wrdlbrnft.gluemeister.ResolveSetting;
import com.github.wrdlbrnft.gluemeister.glueable.GlueableInfo;
import com.github.wrdlbrnft.gluemeister.modules.exceptions.GlueModuleConstructorNotSatisfiedException;
import com.github.wrdlbrnft.gluemeister.modules.exceptions.GlueModuleFactoryException;
import com.github.wrdlbrnft.gluemeister.utils.ElementUtils;
import com.github.wrdlbrnft.gluemeister.utils.EndlessIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
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

    private interface PotentialGlueableMatch extends MatchedGlueable {
        boolean isMatching();
    }

    private static final Method METHOD_EMPTY_LIST = Methods.stub("emptyList");
    private static final Method METHOD_ADD = Methods.stub("add");
    private static final Method METHOD_UNMODIFIABLE_LIST = Methods.stub("unmodifiableList");

    private final Map<MatchedGlueable, CodeElement> mResolvedElementsMap;

    private final List<FieldInfo> mNestedClassFields = new ArrayList<>();
    private Implementation.Builder mNestedClassBuilder;

    private final List<FieldInfo> mFactoryFields;

    private final ProcessingEnvironment mProcessingEnvironment;
    private final Implementation.Builder mFactoryBuilder;

    private final TypeElement mListTypeElement;

    private final TypeElement mClassElement;
    private final DeclaredType mDeclaredType;


    ClassResolver(ProcessingEnvironment processingEnvironment, Map<MatchedGlueable, CodeElement> resolvedElementsMap, List<FieldInfo> factoryFields, Implementation.Builder factoryBuilder, DeclaredType classType) {
        mResolvedElementsMap = resolvedElementsMap;
        mProcessingEnvironment = processingEnvironment;
        mFactoryFields = factoryFields;
        mFactoryBuilder = factoryBuilder;
        mListTypeElement = mProcessingEnvironment.getElementUtils().getTypeElement("java.util.List");
        mDeclaredType = classType;
        mClassElement = (TypeElement) mProcessingEnvironment.getTypeUtils().asElement(classType);
    }

    ResolveResult resolveClass(List<GlueableInfo> glueables) {
        final List<ExecutableElement> abstractMethods = ElementUtils.determineAbstractMethods(mProcessingEnvironment, mClassElement);
        final Type classType = Types.of(mDeclaredType);

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
                    final CodeElement resolvedMethodValue = resolveMethodReturnValue(method, executableType, glueables);

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

    private CodeElement resolveMethodReturnValue(ExecutableElement method, ExecutableType executableType, List<GlueableInfo> glueables) {
        final GlueSettings glueSettingsAnnotation = method.getAnnotation(GlueSettings.class);
        final CacheSetting cacheSetting = glueSettingsAnnotation != null ? glueSettingsAnnotation.cache() : CacheSetting.SINGLETON;
        final List<ResolveSetting> resolveSettings = glueSettingsAnnotation != null ? Arrays.asList(glueSettingsAnnotation.resolve()) : new ArrayList<>();
        if (resolveSettings.contains(ResolveSetting.COLLECT)) {
            return resolveCollect(method, executableType, glueables, cacheSetting);
        }

        final List<MatchedGlueable> matchingGlueables = findAllGlueablesForElement(executableType.getReturnType(), glueables);
        for (MatchedGlueable matchedGlueable : matchingGlueables) {
            try {
                return resolveGlueableCodeElement(matchedGlueable, glueables, cacheSetting);
            } catch (GlueModuleConstructorNotSatisfiedException e) {
                mProcessingEnvironment.getMessager().printMessage(
                        Diagnostic.Kind.NOTE,
                        e.getMessage(),
                        e.getElement()
                );
            } catch (Exception e) {
                mProcessingEnvironment.getMessager().printMessage(
                        Diagnostic.Kind.NOTE,
                        "Ran into an issue while following a resolution path: " + e.getMessage(),
                        null
                );
            }
        }

        throw new GlueModuleConstructorNotSatisfiedException("Return type of method " + method + " cannot be injected by GlueMeister. Make sure there is an @Glueable component which can be used to satisfy it.", method);
    }

    private CodeElement resolveCollect(ExecutableElement method, ExecutableType executableType, List<GlueableInfo> glueables, CacheSetting cacheSetting) {
        final TypeMirror returnType = executableType.getReturnType();
        final TypeMirror erasedReturnType = mProcessingEnvironment.getTypeUtils().erasure(returnType);
        if (!mProcessingEnvironment.getTypeUtils().isAssignable(erasedReturnType, mListTypeElement.asType())) {
            throw new GlueModuleFactoryException("You can use GlueCollect only on methods which return a List! " + method + " does not.", method);
        }

        final List<TypeMirror> typeParameters = Utils.getTypeParameters(returnType);
        final TypeMirror collectedTypeMirror = typeParameters.get(0);
        final List<MatchedGlueable> matchingGlueables = findAllGlueablesForElement(collectedTypeMirror, glueables);

        if (matchingGlueables.isEmpty()) {
            return METHOD_EMPTY_LIST.callOnTarget(Types.COLLECTIONS);
        }

        final List<CodeElement> items = matchingGlueables.stream()
                .map(type -> resolveGlueableCodeElement(type, glueables, cacheSetting))
                .collect(Collectors.toList());

        final Type collectedType = Types.of(collectedTypeMirror);
        final Method aggregatorMethod = new Method.Builder()
                .setModifiers(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC))
                .setReturnType(Types.generic(Types.LIST, collectedType))
                .setCode(new ExecutableBuilder() {
                    @Override
                    protected List<Variable> createParameters() {
                        return Collections.emptyList();
                    }

                    @Override
                    protected void write(Block block) {
                        final Variable varList = Variables.of(Types.generic(Types.LIST, collectedType), Modifier.FINAL);
                        block.set(varList, Types.generic(Types.ARRAY_LIST, collectedType).newInstance()).append(";").newLine();
                        for (CodeElement item : items) {
                            block.append(METHOD_ADD.callOnTarget(varList, item)).append(";").newLine();
                        }
                        block.append("return ").append(METHOD_UNMODIFIABLE_LIST.callOnTarget(Types.COLLECTIONS, varList)).append(";");
                    }
                })
                .build();
        mFactoryBuilder.addMethod(aggregatorMethod);

        return aggregatorMethod.call();
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

    private TypeMirror resolveTypeMirror(TypeMirror mirror) {
        switch (mirror.getKind()) {

            case TYPEVAR:
                throw new GlueModuleFactoryException("Failed to resolve Type Mirror: " + mirror, null);

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
            if (!ElementUtils.hasPublicVisibility(constructor)) {
                continue;
            }

            try {
                return constructor.getParameters().stream()
                        .map(parameter -> {
                            final GlueSettings glueSettingsAnnotation = parameter.getAnnotation(GlueSettings.class);
                            final CacheSetting cacheSetting = glueSettingsAnnotation != null ? glueSettingsAnnotation.cache() : CacheSetting.SINGLETON;
                            final List<MatchedGlueable> matchingGlueables = findAllGlueablesForElement(parameter.asType(), glueables);
                            for (MatchedGlueable matchedGlueable : matchingGlueables) {
                                try {
                                    return resolveGlueableCodeElement(matchedGlueable, glueables, cacheSetting);
                                } catch (GlueModuleConstructorNotSatisfiedException e) {
                                    mProcessingEnvironment.getMessager().printMessage(
                                            Diagnostic.Kind.NOTE,
                                            e.getMessage(),
                                            e.getElement()
                                    );
                                } catch (Exception e) {
                                    mProcessingEnvironment.getMessager().printMessage(
                                            Diagnostic.Kind.NOTE,
                                            "Ran into this issue while following a resolution path: " + e.getMessage(),
                                            null
                                    );
                                }
                            }

                            throw new GlueModuleConstructorNotSatisfiedException("Parameter " + parameter.getSimpleName() + " cannot be injected by GlueMeister. Make sure there is an @Glueable component which can be used to satisfy it.", parameter);
                        })
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

    private List<MatchedGlueable> findAllGlueablesForElement(TypeMirror parameter, List<GlueableInfo> glueables) {
        final GlueSettings glueSettings = parameter.getAnnotation(GlueSettings.class);
        final String key = glueSettings != null ? glueSettings.key() : "";
        return glueables.stream()
                .filter(info -> key.trim().isEmpty() || key.equals(info.getKey()))
                .map(info -> matchGlueable(parameter, info))
                .filter(PotentialGlueableMatch::isMatching)
                .collect(Collectors.toList());
    }

    private PotentialGlueableMatch matchGlueable(TypeMirror parameter, GlueableInfo info) {
        final Element glueableElement = info.getElement();
        switch (info.getKind()) {

            case INSTANCE_METHOD:
            case STATIC_METHOD:
                final ExecutableElement methodElement = (ExecutableElement) glueableElement;
                final DeclaredType returnType = (DeclaredType) methodElement.getReturnType();
                return new PotentialGlueableMatchImpl(
                        isAssignable(returnType, parameter),
                        info,
                        returnType
                );

            case STATIC_FIELD:
                final DeclaredType fieldType = (DeclaredType) glueableElement.asType();
                return new PotentialGlueableMatchImpl(
                        isAssignable(fieldType, parameter),
                        info,
                        fieldType
                );

            case ABSTRACT_CLASS:
            case CLASS:
            case INTERFACE:
                final DeclaredType classType = (DeclaredType) glueableElement.asType();
                if (isAssignable(classType, parameter)) {
                    return new PotentialGlueableMatchImpl(true, info, classType);
                }

                final DeclaredType parameterType = (DeclaredType) parameter;
                final List<? extends TypeMirror> typeArguments = parameterType.getTypeArguments();
                if (typeArguments.isEmpty()) {
                    return new PotentialGlueableMatchImpl(false, info, classType);
                }

                final TypeElement glueableTypeElement = (TypeElement) glueableElement;
                try {
                    final DeclaredType declaredType = mProcessingEnvironment.getTypeUtils().getDeclaredType(
                            glueableTypeElement,
                            typeArguments.stream().toArray(TypeMirror[]::new)
                    );

                    return new PotentialGlueableMatchImpl(
                            isAssignable(declaredType, parameter),
                            info,
                            declaredType
                    );
                } catch (Exception ignored) {
                    return new PotentialGlueableMatchImpl(false, info, classType);
                }

            default:
                throw new GlueMeisterException("Encountered unknown kind of Glueable: " + info.getKind(), glueableElement);
        }
    }

    private CodeElement resolveGlueableCodeElement(MatchedGlueable matchedGlueable, List<GlueableInfo> glueables, CacheSetting cacheSetting) {
        if (mResolvedElementsMap.containsKey(matchedGlueable)) {
            return mResolvedElementsMap.get(matchedGlueable);
        }

        final CodeElement codeElement = translateGlueableInfoToCodeElement(matchedGlueable, glueables, cacheSetting);
        mResolvedElementsMap.put(matchedGlueable, codeElement);
        return codeElement;
    }

    private CodeElement translateGlueableInfoToCodeElement(MatchedGlueable matchedGlueable, List<GlueableInfo> glueables, CacheSetting cacheSetting) {
        final GlueableInfo info = matchedGlueable.getInfo();
        switch (info.getKind()) {

            case STATIC_FIELD:
                return resolveField(info);

            case CLASS:
            case ABSTRACT_CLASS:
            case INTERFACE:
                return resolveType(matchedGlueable, glueables, cacheSetting);

            case INSTANCE_METHOD:
                return resolveInstanceMethod(info, glueables, cacheSetting);

            case STATIC_METHOD:
                return resolveStaticMethod(info, cacheSetting);

            default:
                throw new GlueMeisterException("Encountered unknown GlueableType: " + info.getKind(), info.getElement());
        }
    }

    private CodeElement resolveType(MatchedGlueable matchedGlueable, List<GlueableInfo> glueables, CacheSetting cacheSetting) {
        final GlueableInfo info = matchedGlueable.getInfo();
        final TypeElement classElement = (TypeElement) info.getElement();
        final List<DeclaredType> declaredTypes = resolveDeclaredTypesForAmbiguousType(matchedGlueable.getDeclaredType(), glueables);
        for (DeclaredType declaredType : declaredTypes) {
            final ClassResolver subClassResolver = new ClassResolver(mProcessingEnvironment, mResolvedElementsMap, mFactoryFields, mFactoryBuilder, declaredType);
            final ResolveResult resolvedClass = subClassResolver.resolveClass(glueables);
            final CodeElement value = resolvedClass.getValue();
            try {
                switch (cacheSetting) {

                    case SINGLETON:
                        return getFieldInFactoryFor(declaredType, resolvedClass::getType, resolvedClass::getValue);

                    case REUSE_SAME_INSTANCE:
                        return getFieldInNestedClassFor(declaredType, resolvedClass::getType, resolvedClass::getValue);

                    case CREATE_NEW_INSTANCE:
                        return value;

                    default:
                        throw new GlueMeisterException("Encountered unkown cache setting: " + cacheSetting, null);
                }
            } catch (Exception e) {
                mProcessingEnvironment.getMessager().printMessage(
                        Diagnostic.Kind.NOTE,
                        "Ran into an issue while following a resolution path for a class: " + e.getMessage(),
                        null
                );
            }
        }

        throw new GlueModuleFactoryException("GlueMeister failed to inject type " + matchedGlueable.getDeclaredType() + ". Make sure there is an @Glueable component which can be used to satisfy it.", classElement);
    }

    private CodeElement resolveStaticMethod(GlueableInfo info, CacheSetting cacheSetting) {
        final ExecutableElement staticMethodElement = (ExecutableElement) info.getElement();
        final TypeElement enclosingElement = (TypeElement) staticMethodElement.getEnclosingElement();
        switch (cacheSetting) {

            case SINGLETON:
                return getFieldInFactoryFor(staticMethodElement.getReturnType(),
                        () -> Types.of(staticMethodElement.getReturnType()),
                        () -> Methods.from(staticMethodElement).callOnTarget(Types.of(enclosingElement))
                );

            case REUSE_SAME_INSTANCE:
                return getFieldInNestedClassFor(staticMethodElement.getReturnType(),
                        () -> Types.of(staticMethodElement.getReturnType()),
                        () -> Methods.from(staticMethodElement).callOnTarget(Types.of(enclosingElement))
                );

            case CREATE_NEW_INSTANCE:
                return Methods.from(staticMethodElement).callOnTarget(Types.of(enclosingElement));

            default:
                throw new GlueMeisterException("Encountered unknown cache setting: " + cacheSetting, null);
        }
    }

    private Field getFieldInFactoryFor(TypeMirror typeMirror, Supplier<Type> typeSupplier, Supplier<CodeElement> valueSupplier) {
        for (FieldInfo info : mFactoryFields) {
            final TypeMirror fieldType = info.getTypeMirror();
            if (isAssignable(typeMirror, fieldType)) {
                return info.getField();
            }
        }
        final Field field = new Field.Builder()
                .setModifiers(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL))
                .setType(typeSupplier.get())
                .setInitialValue(valueSupplier.get())
                .build();
        mFactoryBuilder.addField(field);
        mFactoryFields.add(new FieldInfoImpl(typeMirror, field));
        return field;
    }

    private Field getFieldInNestedClassFor(TypeMirror typeMirror, Supplier<Type> typeSupplier, Supplier<CodeElement> valueSupplier) {
        for (FieldInfo info : mNestedClassFields) {
            final TypeMirror fieldType = info.getTypeMirror();
            if (isAssignable(typeMirror, fieldType)) {
                return info.getField();
            }
        }
        final Field field = new Field.Builder()
                .setModifiers(EnumSet.of(Modifier.PRIVATE, Modifier.FINAL))
                .setType(typeSupplier.get())
                .setInitialValue(valueSupplier.get())
                .build();
        mNestedClassBuilder.addField(field);
        mNestedClassFields.add(new FieldInfoImpl(typeMirror, field));
        return field;
    }

    private CodeElement resolveInstanceMethod(GlueableInfo info, List<GlueableInfo> glueables, CacheSetting cacheSetting) {
        final ExecutableElement method = (ExecutableElement) info.getElement();
        final TypeElement parent = (TypeElement) method.getEnclosingElement();
        final List<DeclaredType> possibleTypes = resolveDeclaredTypesForAmbiguousType((DeclaredType) parent.asType(), glueables);
        final TypeMirror returnType = resolveReturnType(method);
        for (DeclaredType parentType : possibleTypes) {
            try {
                switch (cacheSetting) {

                    case SINGLETON:
                        return getFieldInFactoryFor(returnType,
                                () -> Types.of(returnType),
                                () -> resolveInstanceMethodCallOnParent(parentType, method, glueables)
                        );

                    case REUSE_SAME_INSTANCE:
                        return getFieldInNestedClassFor(returnType,
                                () -> Types.of(returnType),
                                () -> resolveInstanceMethodCallOnParent(parentType, method, glueables)
                        );

                    case CREATE_NEW_INSTANCE:
                        return resolveInstanceMethodCallOnParent(parentType, method, glueables);

                    default:
                        throw new GlueMeisterException("Encountered unknown cache setting: " + cacheSetting, null);
                }
            } catch (Exception e) {
                mProcessingEnvironment.getMessager().printMessage(
                        Diagnostic.Kind.NOTE,
                        "Ran into an issue while following a resolution path for the parent of an instance method: " + e.getMessage(),
                        null
                );
            }
        }

        throw new GlueModuleFactoryException("Failed to resolve type of " + parent.getSimpleName() + " which is required to use instance method " + method, method);
    }

    private CodeElement resolveInstanceMethodCallOnParent(DeclaredType parentType, ExecutableElement method, List<GlueableInfo> glueables) {
        final ClassResolver parentResolver = new ClassResolver(mProcessingEnvironment, mResolvedElementsMap, mFactoryFields, mFactoryBuilder, parentType);
        final ResolveResult result = parentResolver.resolveClass(glueables);
        final Field parentField = getFieldInFactoryFor(result.getBaseType(), result::getType, result::getValue);
        return Methods.call(method, parentField);
    }

    private TypeMirror resolveReturnType(ExecutableElement method) {
        if (method.getReturnType().getKind() == TypeKind.TYPEVAR) {
            final ExecutableType executableType = (ExecutableType) mProcessingEnvironment.getTypeUtils().asMemberOf(mDeclaredType, method);
            return executableType.getReturnType();
        }
        return method.getReturnType();
    }

    private List<DeclaredType> resolveDeclaredTypesForAmbiguousType(DeclaredType type, List<GlueableInfo> glueables) {
        if (type.getTypeArguments().stream().noneMatch(argument -> argument.getKind() == TypeKind.WILDCARD || argument.getKind() == TypeKind.TYPEVAR)) {
            return Collections.singletonList(type);
        }

        final List<DeclaredType> result = new ArrayList<>();
        final TypeElement element = (TypeElement) mProcessingEnvironment.getTypeUtils().asElement(type);
        final Map<? extends TypeParameterElement, EndlessIterator<MatchedGlueable>> typeParameterMap = element.getTypeParameters().stream()
                .collect(Collectors.<TypeParameterElement, TypeParameterElement, EndlessIterator<MatchedGlueable>>toMap(
                        parameter -> parameter,
                        parameter -> {
                            final List<MatchedGlueable> glueablesForTypeParameter = findGlueablesForTypeParameter(parameter, glueables);
                            return new EndlessIterator<>(glueablesForTypeParameter);
                        }
                ));

        if (typeParameterMap.values().isEmpty()) {
            final DeclaredType declaredType = (DeclaredType) element.asType();
            result.add(declaredType);
            return result;
        }

        final boolean unmatchableParameters = typeParameterMap.values().stream()
                .filter(EndlessIterator::isEmpty)
                .findAny().isPresent();

        if (unmatchableParameters) {
            throw new GlueModuleFactoryException("GlueMeister failed to inject type parameters of " + element.getSimpleName(), element);
        }

        final int count = typeParameterMap.values().stream()
                .mapToInt(EndlessIterator::getItemCount)
                .reduce((a, b) -> a * b)
                .orElse(0);

        for (int i = 0; i < count; i++) {
            try {
                final TypeMirror[] matchedGlueables = typeParameterMap.keySet().stream()
                        .map(typeParameterMap::get)
                        .map(EndlessIterator::next)
                        .map(MatchedGlueable::getDeclaredType)
                        .toArray(TypeMirror[]::new);

                final DeclaredType declaredType = mProcessingEnvironment.getTypeUtils().getDeclaredType(element, matchedGlueables);
                result.add(declaredType);
            } catch (Exception e) {
                mProcessingEnvironment.getMessager().printMessage(
                        Diagnostic.Kind.NOTE,
                        "Ran into an issue while following a resolution path for the type parameters of an ambiguous type: " + e.getMessage(),
                        null
                );
            }
        }

        return result;
    }

    private List<MatchedGlueable> findGlueablesForTypeParameter(TypeParameterElement typeParameter, List<GlueableInfo> glueables) {
        final GlueSettings glueSettings = typeParameter.getAnnotation(GlueSettings.class);
        final String key = glueSettings != null ? glueSettings.key() : "";
        return glueables.stream()
                .filter(info -> key.trim().isEmpty() || key.equals(info.getKey()))
                .map(info -> typeParameter.getBounds().stream()
                        .map(parameter -> matchGlueable(parameter, info))
                        .reduce((a, b) -> new PotentialGlueableMatchImpl(a.isMatching() && b.isMatching(), a.getInfo(), a.getDeclaredType()))
                        .orElse(new PotentialGlueableMatchImpl(false, null, null)))
                .filter(PotentialGlueableMatch::isMatching)
                .collect(Collectors.toList());
    }

    private CodeElement resolveField(GlueableInfo info) {
        final VariableElement fieldElement = (VariableElement) info.getElement();
        final TypeElement enclosingElement = (TypeElement) fieldElement.getEnclosingElement();
        return new Block().append(Types.of(enclosingElement)).append(".").append(fieldElement.getSimpleName().toString());
    }

    private boolean isAssignable(TypeMirror a, TypeMirror b) {
        final javax.lang.model.util.Types typeUtils = mProcessingEnvironment.getTypeUtils();

        final TypeMirror resolvedA = resolveTypeMirror(a);
        final TypeMirror resolvedB = resolveTypeMirror(b);

        return typeUtils.isAssignable(resolvedA, resolvedB);
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

    private static class PotentialGlueableMatchImpl implements PotentialGlueableMatch {

        private final boolean mMatching;
        private final GlueableInfo mInfo;
        private final DeclaredType mDeclaredType;

        private PotentialGlueableMatchImpl(boolean matching, GlueableInfo info, DeclaredType declaredType) {
            mMatching = matching;
            mInfo = info;
            mDeclaredType = declaredType;
        }

        @Override
        public boolean isMatching() {
            return mMatching;
        }

        @Override
        public GlueableInfo getInfo() {
            return mInfo;
        }

        @Override
        public DeclaredType getDeclaredType() {
            return mDeclaredType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PotentialGlueableMatchImpl that = (PotentialGlueableMatchImpl) o;

            if (mInfo != null ? !mInfo.equals(that.mInfo) : that.mInfo != null) return false;
            return mDeclaredType != null ? mDeclaredType.equals(that.mDeclaredType) : that.mDeclaredType == null;

        }

        @Override
        public int hashCode() {
            int result = mInfo != null ? mInfo.hashCode() : 0;
            result = 31 * result + (mDeclaredType != null ? mDeclaredType.hashCode() : 0);
            return result;
        }
    }
}

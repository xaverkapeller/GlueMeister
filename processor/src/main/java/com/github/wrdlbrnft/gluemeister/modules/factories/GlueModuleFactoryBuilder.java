package com.github.wrdlbrnft.gluemeister.modules.factories;

import com.github.wrdlbrnft.codebuilder.annotations.Annotations;
import com.github.wrdlbrnft.codebuilder.code.Block;
import com.github.wrdlbrnft.codebuilder.code.BlockWriter;
import com.github.wrdlbrnft.codebuilder.code.CodeElement;
import com.github.wrdlbrnft.codebuilder.executables.Constructor;
import com.github.wrdlbrnft.codebuilder.executables.ExecutableBuilder;
import com.github.wrdlbrnft.codebuilder.executables.Method;
import com.github.wrdlbrnft.codebuilder.executables.Methods;
import com.github.wrdlbrnft.codebuilder.implementations.Implementation;
import com.github.wrdlbrnft.codebuilder.types.DefinedType;
import com.github.wrdlbrnft.codebuilder.types.Type;
import com.github.wrdlbrnft.codebuilder.types.Types;
import com.github.wrdlbrnft.codebuilder.variables.Field;
import com.github.wrdlbrnft.codebuilder.variables.Variable;
import com.github.wrdlbrnft.codebuilder.variables.Variables;
import com.github.wrdlbrnft.gluemeister.GlueInject;
import com.github.wrdlbrnft.gluemeister.GlueMeisterException;
import com.github.wrdlbrnft.gluemeister.glueable.GlueableInfo;
import com.github.wrdlbrnft.gluemeister.modules.GlueModuleInfo;
import com.github.wrdlbrnft.gluemeister.modules.exceptions.GlueModuleConstructorNotSatisfiedException;
import com.github.wrdlbrnft.gluemeister.modules.exceptions.GlueModuleFactoryException;
import com.github.wrdlbrnft.gluemeister.utils.ElementUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */

public class GlueModuleFactoryBuilder {

    private final Map<GlueableInfo, CodeElement> mResolvedElementsMap = new HashMap<>();

    private final ProcessingEnvironment mProcessingEnvironment;

    private Implementation.Builder mBuilder;

    public GlueModuleFactoryBuilder(ProcessingEnvironment processingEnv) {
        mProcessingEnvironment = processingEnv;
    }

    public GlueModuleFactoryInfo build(GlueModuleInfo entityInfo, List<GlueableInfo> allGlueables) {
        final TypeElement entityElement = entityInfo.getEntityElement();
        final DefinedType entityType = Types.of(entityElement);

        mBuilder = new Implementation.Builder();
        mBuilder.setModifiers(EnumSet.of(Modifier.PUBLIC, Modifier.FINAL));
        mBuilder.setName(entityInfo.getFactoryName());

        final CodeElement instanceElement = resolveClass(entityElement, allGlueables);

        mBuilder.addMethod(new Method.Builder()
                .setModifiers(EnumSet.of(Modifier.PUBLIC, Modifier.STATIC))
                .setReturnType(entityType)
                .setName("newInstance")
                .setCode(new ExecutableBuilder() {
                    @Override
                    protected List<Variable> createParameters() {
                        return new ArrayList<>();
                    }

                    @Override
                    protected void write(Block block) {
                        block.append("return ").append(instanceElement).append(";");
                    }
                })
                .build());

        return new GlueModuleFactoryInfoImpl(
                determineFactoryPackageName(entityElement),
                mBuilder.build()
        );
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
                    final TypeMirror glueableTypeMirror = element.asType();
                    return mProcessingEnvironment.getTypeUtils().isAssignable(glueableTypeMirror, parameterTypeMirror);
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
        switch (glueableInfo.getType()) {

            case STATIC_FIELD:
                final VariableElement fieldElement = (VariableElement) glueableInfo.getElement();
                return resolveField(fieldElement);

            case CLASS:
            case ABSTRACT_CLASS:
            case INTERFACE:
                final TypeElement classElement = (TypeElement) glueableInfo.getElement();
                final CodeElement resolvedClass = resolveClass(classElement, glueables);
                final Field field = new Field.Builder()
                        .setModifiers(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL))
                        .setType(Types.of(glueableInfo.getElement().asType()))
                        .setInitialValue(resolvedClass)
                        .build();
                mBuilder.addField(field);
                return field;

            case STATIC_METHOD:
                throw new GlueModuleConstructorNotSatisfiedException("Injecting this component is not yet supported. Look for an update!", glueableInfo.getElement());

            default:
                throw new GlueMeisterException("Encountered unknown GlueableType: " + glueableInfo.getType(), glueableInfo.getElement());
        }
    }

    private CodeElement resolveField(VariableElement fieldElement) {
        final TypeElement enclosingElement = (TypeElement) fieldElement.getEnclosingElement();
        return new BlockWriter() {
            @Override
            protected void write(Block block) {
                block.append(Types.of(enclosingElement)).append(".").append(fieldElement.getSimpleName().toString());
            }
        };
    }

    private CodeElement resolveClass(TypeElement classElement, List<GlueableInfo> glueables) {
        final List<ExecutableElement> unimplementedMethods = ElementUtils.determineUnimplementedMethods(classElement);
        final Type classType = Types.of(classElement);
        if (unimplementedMethods.isEmpty()) {
            return new BlockWriter() {
                @Override
                protected void write(Block block) {
                    block.append(classType.newInstance(resolveTypeConstructorParameters(classElement, glueables)));
                }
            };
        } else {
            final Implementation.Builder resolvedClassBuilder = new Implementation.Builder();
            resolvedClassBuilder.setModifiers(EnumSet.of(Modifier.PRIVATE, Modifier.STATIC));
            if (classElement.getKind() == ElementKind.INTERFACE) {
                resolvedClassBuilder.addImplementedType(classType);
            } else if (classElement.getKind() == ElementKind.CLASS) {
                resolvedClassBuilder.setExtendedType(classType);

                classElement.getEnclosedElements().stream()
                        .filter(element -> element.getKind() == ElementKind.CONSTRUCTOR)
                        .map(ExecutableElement.class::cast)
                        .map(constructor -> {
                            final List<? extends VariableElement> constructorParameters = constructor.getParameters();
                            final List<Variable> parameters = constructorParameters.stream()
                                    .map(p -> Variables.of(Types.of(p.asType())))
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
                        .forEach(resolvedClassBuilder::addConstructor);
            }

            for (ExecutableElement method : unimplementedMethods) {
                final TypeMirror returnType = method.getReturnType();
                final Element returnTypeElement = mProcessingEnvironment.getTypeUtils().asElement(returnType);
                final GlueableInfo glueableInfo = findGlueableForElement(returnTypeElement, glueables);
                final CodeElement resolvedMethodValue = resolveGlueableCodeElement(glueableInfo, glueables);

                resolvedClassBuilder.addMethod(new Method.Builder()
                        .setName(method.getSimpleName().toString())
                        .addAnnotation(Annotations.forType(Override.class))
                        .setReturnType(Types.of(method.getReturnType()))
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
                        .build());
            }

            final Implementation implementation = resolvedClassBuilder.build();
            mBuilder.addNestedImplementation(implementation);

            return new BlockWriter() {
                @Override
                protected void write(Block block) {
                    block.append(implementation.newInstance(resolveTypeConstructorParameters(classElement, glueables)));
                }
            };
        }
    }

    private String determineFactoryPackageName(TypeElement entityElement) {
        Element enclosingElement = entityElement.getEnclosingElement();
        if (enclosingElement == null) {
            return "";
        }

        while (enclosingElement.getKind() != ElementKind.PACKAGE) {
            enclosingElement = enclosingElement.getEnclosingElement();
            if (enclosingElement == null) {
                return "";
            }
        }

        final PackageElement packageElement = (PackageElement) enclosingElement;
        return packageElement.getQualifiedName().toString();
    }

    private static class GlueModuleFactoryInfoImpl implements GlueModuleFactoryInfo {

        private final String mPackageName;
        private final Implementation mImplementation;

        private GlueModuleFactoryInfoImpl(String packageName, Implementation implementation) {
            mPackageName = packageName;
            mImplementation = implementation;
        }

        @Override
        public String getPackageName() {
            return mPackageName;
        }

        @Override
        public Implementation getImplementation() {
            return mImplementation;
        }
    }
}

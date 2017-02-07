package com.github.wrdlbrnft.gluemeister.entities.factories;

import com.github.wrdlbrnft.codebuilder.code.Block;
import com.github.wrdlbrnft.codebuilder.code.BlockWriter;
import com.github.wrdlbrnft.codebuilder.code.CodeElement;
import com.github.wrdlbrnft.codebuilder.executables.ExecutableBuilder;
import com.github.wrdlbrnft.codebuilder.executables.Method;
import com.github.wrdlbrnft.codebuilder.implementations.Implementation;
import com.github.wrdlbrnft.codebuilder.types.DefinedType;
import com.github.wrdlbrnft.codebuilder.types.Type;
import com.github.wrdlbrnft.codebuilder.types.Types;
import com.github.wrdlbrnft.codebuilder.variables.Variable;
import com.github.wrdlbrnft.gluemeister.GlueInject;
import com.github.wrdlbrnft.gluemeister.GlueMeisterException;
import com.github.wrdlbrnft.gluemeister.entities.GlueEntityInfo;
import com.github.wrdlbrnft.gluemeister.entities.exceptions.GlueEntityConstructorNotSatisfiedException;
import com.github.wrdlbrnft.gluemeister.entities.exceptions.GlueEntityFactoryException;
import com.github.wrdlbrnft.gluemeister.glueable.GlueableInfo;
import com.github.wrdlbrnft.gluemeister.utils.ElementUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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

public class GlueEntityFactoryBuilder {

    private final ProcessingEnvironment mProcessingEnvironment;

    public GlueEntityFactoryBuilder(ProcessingEnvironment processingEnv) {
        mProcessingEnvironment = processingEnv;
    }

    public GlueEntityFactoryInfo build(GlueEntityInfo entityInfo, List<GlueableInfo> allGlueables) {
        final TypeElement entityElement = entityInfo.getEntityElement();
        final DefinedType entityType = Types.of(entityElement);

        final Implementation.Builder builder = new Implementation.Builder();
        builder.setModifiers(EnumSet.of(Modifier.PUBLIC, Modifier.FINAL));
        builder.setName(entityInfo.getFactoryName());

        builder.addMethod(new Method.Builder()
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
                        block.append("return ").append(resolveClass(entityElement, allGlueables)).append(";");
                    }
                })
                .build());

        return new GlueEntityFactoryInfoImpl(
                determineFactoryPackageName(entityElement),
                builder.build()
        );
    }

    private CodeElement[] resolveTypeConstructorParameters(TypeElement entityElement, List<GlueableInfo> glueables) {
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
                        .map(parameter -> findGlueableForParameter(glueables, parameter))
                        .map(info -> resolveGlueableCodeElement(info, glueables))
                        .toArray(CodeElement[]::new);
            } catch (GlueEntityConstructorNotSatisfiedException e) {
                mProcessingEnvironment.getMessager().printMessage(
                        Diagnostic.Kind.NOTE,
                        e.getMessage(),
                        e.getElement()
                );
            }
        }

        throw new GlueEntityFactoryException("GlueMeister cannot create instances of " + entityElement.getSimpleName() + " because there is no constructor whose parameters can be satisfied. Look at the previous warnings to figure out why.", entityElement);
    }

    private GlueableInfo findGlueableForParameter(List<GlueableInfo> glueables, VariableElement parameter) {
        final GlueInject glueInject = parameter.getAnnotation(GlueInject.class);
        if (glueInject != null) {
            final String key = glueInject.value();
            if (!key.trim().isEmpty()) {
                return glueables.stream()
                        .filter(info -> key.equals(info.getKey()))
                        .findAny().orElseThrow(() -> new GlueEntityConstructorNotSatisfiedException("Parameter " + parameter.getSimpleName() + " cannot be injected by GlueMeister. No component annotated with @Glueable matches the key \"" + key + "\". Make sure there is an @Glueable component which can be used to satisfy it.", parameter));
            }
        }

        final TypeMirror parameterTypeMirror = parameter.asType();
        return glueables.stream()
                .filter(info -> {
                    final Element element = info.getElement();
                    final TypeMirror glueableTypeMirror = element.asType();
                    return mProcessingEnvironment.getTypeUtils().isAssignable(glueableTypeMirror, parameterTypeMirror);
                })
                .findAny().orElseThrow(() -> new GlueEntityConstructorNotSatisfiedException("Parameter " + parameter.getSimpleName() + " cannot be injected by GlueMeister. Make sure there is an @Glueable component which can be used to satisfy it.", parameter));
    }

    private CodeElement resolveGlueableCodeElement(GlueableInfo glueableInfo, List<GlueableInfo> glueables) {
        switch (glueableInfo.getType()) {

            case STATIC_FIELD:
                final VariableElement fieldElement = (VariableElement) glueableInfo.getElement();
                return resolveField(fieldElement);

            case CLASS:
                final TypeElement classElement = (TypeElement) glueableInfo.getElement();
                return resolveClass(classElement, glueables);

            case STATIC_METHOD:
            case INTERFACE:
            case ABSTRACT_CLASS:
                throw new GlueEntityConstructorNotSatisfiedException("Injecting this component is not yet supported. Look for an update!", glueableInfo.getElement());

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
        final Type entityType = Types.of(classElement);
        return new BlockWriter() {
            @Override
            protected void write(Block block) {
                block.append(entityType.newInstance(resolveTypeConstructorParameters(classElement, glueables)));
            }
        };
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

    private static class GlueEntityFactoryInfoImpl implements GlueEntityFactoryInfo {

        private final String mPackageName;
        private final Implementation mImplementation;

        private GlueEntityFactoryInfoImpl(String packageName, Implementation implementation) {
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

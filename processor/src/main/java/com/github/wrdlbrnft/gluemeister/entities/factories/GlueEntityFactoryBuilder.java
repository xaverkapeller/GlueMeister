package com.github.wrdlbrnft.gluemeister.entities.factories;

import com.github.wrdlbrnft.codebuilder.code.Block;
import com.github.wrdlbrnft.codebuilder.executables.ExecutableBuilder;
import com.github.wrdlbrnft.codebuilder.executables.Method;
import com.github.wrdlbrnft.codebuilder.implementations.Implementation;
import com.github.wrdlbrnft.codebuilder.types.DefinedType;
import com.github.wrdlbrnft.codebuilder.types.Types;
import com.github.wrdlbrnft.codebuilder.variables.Variable;
import com.github.wrdlbrnft.gluemeister.entities.GlueEntityInfo;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

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

    public GlueEntityFactoryInfo build(GlueEntityInfo entityInfo) {
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
                        block.append("return ").append(entityType.newInstance()).append(";");
                    }
                })
                .build());

        return new GlueEntityFactoryInfoImpl(
                determineFactoryPackageName(entityElement),
                builder.build()
        );
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

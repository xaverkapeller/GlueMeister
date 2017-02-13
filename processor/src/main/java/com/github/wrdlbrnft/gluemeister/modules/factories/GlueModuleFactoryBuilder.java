package com.github.wrdlbrnft.gluemeister.modules.factories;

import com.github.wrdlbrnft.codebuilder.code.Block;
import com.github.wrdlbrnft.codebuilder.code.CodeElement;
import com.github.wrdlbrnft.codebuilder.executables.ExecutableBuilder;
import com.github.wrdlbrnft.codebuilder.executables.Method;
import com.github.wrdlbrnft.codebuilder.implementations.Implementation;
import com.github.wrdlbrnft.codebuilder.variables.Variable;
import com.github.wrdlbrnft.gluemeister.glueable.GlueableInfo;
import com.github.wrdlbrnft.gluemeister.modules.GlueModuleInfo;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */

public class GlueModuleFactoryBuilder {

    private final ProcessingEnvironment mProcessingEnvironment;

    public GlueModuleFactoryBuilder(ProcessingEnvironment processingEnv) {
        mProcessingEnvironment = processingEnv;
    }

    public GlueModuleFactoryInfo build(GlueModuleInfo entityInfo, List<GlueableInfo> allGlueables) {
        final TypeElement entityElement = entityInfo.getEntityElement();

        final Implementation.Builder builder = new Implementation.Builder();
        builder.setModifiers(EnumSet.of(Modifier.PUBLIC, Modifier.FINAL));
        builder.setName(entityInfo.getFactoryName());

        final Map<MatchedGlueable, CodeElement> resolvedElementsMap = new HashMap<>();
        final List<FieldInfo> fields = new ArrayList<>();
        final ClassResolver resolver = new ClassResolver(mProcessingEnvironment, resolvedElementsMap, fields, builder, (DeclaredType) entityElement.asType());
        final ResolveResult resolveResult = resolver.resolveClass(allGlueables);

        builder.addMethod(new Method.Builder()
                .setModifiers(EnumSet.of(Modifier.PUBLIC, Modifier.STATIC))
                .setReturnType(resolveResult.getType())
                .setName("newInstance")
                .setCode(new ExecutableBuilder() {
                    @Override
                    protected List<Variable> createParameters() {
                        return new ArrayList<>();
                    }

                    @Override
                    protected void write(Block block) {
                        block.append("return ").append(resolveResult.getValue()).append(";");
                    }
                })
                .build());

        return new GlueModuleFactoryInfoImpl(
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

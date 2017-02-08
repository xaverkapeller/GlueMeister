package com.github.wrdlbrnft.gluemeister.modules;

import com.github.wrdlbrnft.gluemeister.GlueModule;
import com.github.wrdlbrnft.gluemeister.modules.exceptions.GlueModuleAnalyzerException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import static com.github.wrdlbrnft.gluemeister.utils.ElementUtils.determineUnimplementedMethods;
import static com.github.wrdlbrnft.gluemeister.utils.ElementUtils.findContainingPackageName;
import static com.github.wrdlbrnft.gluemeister.utils.ElementUtils.verifyAccessibility;
import static com.github.wrdlbrnft.gluemeister.utils.ElementUtils.verifyStaticModifier;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */

public class GlueModuleAnalyzer {

    private final ProcessingEnvironment mProcessingEnvironment;

    public GlueModuleAnalyzer(ProcessingEnvironment processingEnvironment) {
        mProcessingEnvironment = processingEnvironment;
    }

    public List<GlueModuleInfo> analyze(RoundEnvironment roundEnv) {
        final List<GlueModuleInfo> infos = new ArrayList<>();

        final Set<? extends Element> glueEntityElements = roundEnv.getElementsAnnotatedWith(GlueModule.class);
        for (Element glueEntityElement : glueEntityElements) {
            try {
                final TypeElement typeElement = (TypeElement) glueEntityElement;
                final GlueModuleInfo glueModuleInfo = analyzeEntity(typeElement);
                infos.add(glueModuleInfo);
            } catch (GlueModuleAnalyzerException e) {
                mProcessingEnvironment.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        e.getMessage(),
                        e.getElement()
                );
            }
        }

        return infos;
    }

    private GlueModuleInfo analyzeEntity(TypeElement element) {
        verifyStaticModifier(element, GlueModuleAnalyzerException::new);
        verifyAccessibility(element, GlueModuleAnalyzerException::new);
        final List<ExecutableElement> unimplementedMethods = determineUnimplementedMethods(element);
        for (ExecutableElement unimplementedMethod : unimplementedMethods) {
            if (!unimplementedMethod.getParameters().isEmpty()) {
                throw new GlueModuleAnalyzerException("The method " + unimplementedMethod.getSimpleName() + " of GlueModule " + element.getSimpleName() + " has a parameter. Abstract methods in a GlueModule are not allowed to have parameters (yet).", unimplementedMethod);
            }

            final TypeMirror returnType = unimplementedMethod.getReturnType();
            if(returnType.getKind() == TypeKind.VOID) {
                throw new GlueModuleAnalyzerException("The method " + unimplementedMethod.getSimpleName() + " of GlueModule " + element.getSimpleName() + " returns nothing. All methods in a GlueModule have to return something.", unimplementedMethod);
            }

            if(returnType.getKind() != TypeKind.DECLARED) {
                throw new GlueModuleAnalyzerException("The method " + unimplementedMethod.getSimpleName() + " of GlueModule " + element.getSimpleName() + " does not return a declared type. GlueMeister cannot inject primitive types (yet).", unimplementedMethod);
            }
        }
        return new GlueModuleInfoImpl(
                element,
                findContainingPackageName(element),
                determineFactoryName(element),
                unimplementedMethods
        );
    }

    private static String determineFactoryName(TypeElement element) {
        final GlueModule glueModuleAnnotation = element.getAnnotation(GlueModule.class);
        if (glueModuleAnnotation.factoryName().trim().isEmpty()) {
            return element.getSimpleName() + "Factory";
        }
        return glueModuleAnnotation.factoryName();
    }

    private static class GlueModuleInfoImpl implements GlueModuleInfo {

        private final TypeElement mEntityElement;
        private final String mFactoryPackageName;
        private final String mFactoryName;
        private final List<ExecutableElement> mUnimplementedMethods;

        private GlueModuleInfoImpl(TypeElement entityElement, String factoryPackageName, String factoryName, List<ExecutableElement> unimplementedMethods) {
            mEntityElement = entityElement;
            mFactoryPackageName = factoryPackageName;
            mFactoryName = factoryName;
            mUnimplementedMethods = Collections.unmodifiableList(unimplementedMethods);
        }

        @Override
        public List<ExecutableElement> getUnimplementedMethods() {
            return mUnimplementedMethods;
        }

        @Override
        public TypeElement getEntityElement() {
            return mEntityElement;
        }

        @Override
        public String getFactoryPackageName() {
            return mFactoryPackageName;
        }

        public String getFactoryName() {
            return mFactoryName;
        }
    }
}

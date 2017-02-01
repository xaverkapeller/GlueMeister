package com.github.wrdlbrnft.gluemeister.entities;

import com.github.wrdlbrnft.gluemeister.GlueEntity;
import com.github.wrdlbrnft.gluemeister.entities.exceptions.GlueEntityAnalyzerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import static com.github.wrdlbrnft.gluemeister.utils.ElementUtils.findContainingPackageName;
import static com.github.wrdlbrnft.gluemeister.utils.ElementUtils.verifyAccessibility;
import static com.github.wrdlbrnft.gluemeister.utils.ElementUtils.verifyStaticModifier;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */

public class GlueEntityAnalyzer {

    private final ProcessingEnvironment mProcessingEnvironment;

    public GlueEntityAnalyzer(ProcessingEnvironment processingEnvironment) {
        mProcessingEnvironment = processingEnvironment;
    }

    public List<GlueEntityInfo> analyze(RoundEnvironment roundEnv) {
        final List<GlueEntityInfo> infos = new ArrayList<>();

        final Set<? extends Element> glueEntityElements = roundEnv.getElementsAnnotatedWith(GlueEntity.class);
        for (Element glueEntityElement : glueEntityElements) {
            try {
                final TypeElement typeElement = (TypeElement) glueEntityElement;
                final GlueEntityInfo glueEntityInfo = analyzeEntity(typeElement);
                infos.add(glueEntityInfo);
            } catch (GlueEntityAnalyzerException e) {
                mProcessingEnvironment.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        e.getMessage(),
                        e.getElement()
                );
            }
        }

        return infos;
    }

    private GlueEntityInfo analyzeEntity(TypeElement element) {
        verifyStaticModifier(element, GlueEntityAnalyzerException::new);
        verifyAccessibility(element, GlueEntityAnalyzerException::new);
        return new GlueEntityInfoImpl(
                element,
                findContainingPackageName(element),
                determineFactoryName(element)
        );
    }

    private static String determineFactoryName(TypeElement element) {
        final GlueEntity glueEntityAnnotation = element.getAnnotation(GlueEntity.class);
        if (glueEntityAnnotation.factoryName().trim().isEmpty()) {
            return element.getSimpleName() + "Factory";
        }
        return glueEntityAnnotation.factoryName();
    }

    private static class GlueEntityInfoImpl implements GlueEntityInfo {

        private final TypeElement mEntityElement;
        private final String mFactoryPackageName;
        private final String mFactoryName;

        private GlueEntityInfoImpl(TypeElement entityElement, String factoryPackageName, String factoryName) {
            mEntityElement = entityElement;
            mFactoryPackageName = factoryPackageName;
            mFactoryName = factoryName;
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

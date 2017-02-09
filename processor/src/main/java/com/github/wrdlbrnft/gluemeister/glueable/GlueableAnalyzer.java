package com.github.wrdlbrnft.gluemeister.glueable;

import com.github.wrdlbrnft.gluemeister.Glueable;
import com.github.wrdlbrnft.gluemeister.glueable.exceptions.GlueableAnalyzerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;

import static com.github.wrdlbrnft.gluemeister.utils.ElementUtils.isStatic;
import static com.github.wrdlbrnft.gluemeister.utils.ElementUtils.verifyAccessibility;
import static com.github.wrdlbrnft.gluemeister.utils.ElementUtils.verifyFinalModifier;
import static com.github.wrdlbrnft.gluemeister.utils.ElementUtils.verifyStaticModifier;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */

public class GlueableAnalyzer {

    private final ProcessingEnvironment mProcessingEnvironment;

    public GlueableAnalyzer(ProcessingEnvironment processingEnvironment) {
        mProcessingEnvironment = processingEnvironment;
    }

    public List<GlueableInfo> analyze(RoundEnvironment roundEnv) {
        final List<GlueableInfo> infos = new ArrayList<>();

        final Set<? extends Element> glueableElements = roundEnv.getElementsAnnotatedWith(Glueable.class);

        for (Element element : glueableElements) {
            try {
                final GlueableInfo info = analyzeElement(element);
                infos.add(info);
            } catch (GlueableAnalyzerException e) {
                mProcessingEnvironment.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        e.getMessage(),
                        e.getElement()
                );
            }
        }

        return infos;
    }

    private GlueableInfo analyzeElement(Element element) {
        switch (element.getKind()) {

            case CLASS:
                final TypeElement classElement = (TypeElement) element;
                return analyzeClass(classElement);

            case INTERFACE:
                final TypeElement interfaceElement = (TypeElement) element;
                return analyzeInterface(interfaceElement);

            case FIELD:
                final VariableElement fieldElement = (VariableElement) element;
                return analyzeField(fieldElement);

            case METHOD:
                final ExecutableElement methodElement = (ExecutableElement) element;
                return analyzeMethod(methodElement);

            default:
                throw new GlueableAnalyzerException("Element " + element.getSimpleName() + " cannot be annotated with @Glueable. You can only annotate interfaces, static classes, fields and methods!", element);
        }
    }

    private GlueableInfo analyzeClass(TypeElement classElement) {
        verifyStaticModifier(classElement, GlueableAnalyzerException::new);
        verifyAccessibility(classElement, GlueableAnalyzerException::new);
        return new GlueableInfoImpl(
                classElement.getModifiers().contains(Modifier.ABSTRACT)
                        ? GlueableInfo.Kind.ABSTRACT_CLASS
                        : GlueableInfo.Kind.CLASS,
                classElement,
                parseKey(classElement)
        );
    }

    private String parseKey(Element element) {
        final Glueable glueableAnnotation = element.getAnnotation(Glueable.class);
        if (glueableAnnotation.value().trim().isEmpty()) {
            return null;
        }
        return glueableAnnotation.value();
    }

    private GlueableInfo analyzeInterface(TypeElement interfaceElement) {
        verifyStaticModifier(interfaceElement, GlueableAnalyzerException::new);
        verifyAccessibility(interfaceElement, GlueableAnalyzerException::new);
        return new GlueableInfoImpl(
                GlueableInfo.Kind.INTERFACE,
                interfaceElement,
                parseKey(interfaceElement)
        );
    }

    private GlueableInfo analyzeField(VariableElement fieldElement) {
        verifyStaticModifier(fieldElement, GlueableAnalyzerException::new);
        verifyFinalModifier(fieldElement, GlueableAnalyzerException::new);
        verifyAccessibility(fieldElement, GlueableAnalyzerException::new);
        return new GlueableInfoImpl(
                GlueableInfo.Kind.STATIC_FIELD,
                fieldElement,
                parseKey(fieldElement)
        );
    }

    private GlueableInfo analyzeMethod(ExecutableElement methodElement) {
        verifyAccessibility(methodElement, GlueableAnalyzerException::new);
        return new GlueableInfoImpl(
                isStatic(methodElement) ? GlueableInfo.Kind.STATIC_METHOD : GlueableInfo.Kind.INSTANCE_METHOD,
                methodElement,
                parseKey(methodElement)
        );
    }

    private static class GlueableInfoImpl implements GlueableInfo {

        private final Kind mKind;
        private final Element mElement;
        private final String mKey;

        private GlueableInfoImpl(Kind kind, Element element, String key) {
            mKind = kind;
            mElement = element;
            mKey = key;
        }

        public Kind getKind() {
            return mKind;
        }

        @Override
        public Element getElement() {
            return mElement;
        }

        @Override
        public String getKey() {
            return mKey;
        }
    }
}

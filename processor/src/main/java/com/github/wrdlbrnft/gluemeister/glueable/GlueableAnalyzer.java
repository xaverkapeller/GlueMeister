package com.github.wrdlbrnft.gluemeister.glueable;

import com.github.wrdlbrnft.gluemeister.Glueable;
import com.github.wrdlbrnft.gluemeister.glueable.exceptions.GlueableAnalyzerException;
import com.github.wrdlbrnft.gluemeister.utils.ElementUtils;

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
                        ? GlueableType.ABSTRACT_CLASS
                        : GlueableType.CLASS,
                classElement.getQualifiedName().toString()
        );
    }

    private GlueableInfo analyzeInterface(TypeElement interfaceElement) {
        verifyStaticModifier(interfaceElement, GlueableAnalyzerException::new);
        verifyAccessibility(interfaceElement, GlueableAnalyzerException::new);
        return new GlueableInfoImpl(
                GlueableType.INTERFACE,
                interfaceElement.getQualifiedName().toString()
        );
    }

    private GlueableInfo analyzeField(VariableElement fieldElement) {
        verifyStaticModifier(fieldElement, GlueableAnalyzerException::new);
        verifyFinalModifier(fieldElement, GlueableAnalyzerException::new);
        verifyAccessibility(fieldElement, GlueableAnalyzerException::new);
        return new GlueableInfoImpl(
                GlueableType.STATIC_FIELD,
                createFieldIdentifier(fieldElement)
        );
    }

    private static String createFieldIdentifier(VariableElement fieldElement) {
        final TypeElement enclosingElement = (TypeElement) fieldElement.getEnclosingElement();
        return enclosingElement.getQualifiedName().toString() + "#" + fieldElement.getSimpleName();
    }

    private GlueableInfo analyzeMethod(ExecutableElement methodElement) {
        verifyStaticModifier(methodElement, GlueableAnalyzerException::new);
        verifyAccessibility(methodElement, GlueableAnalyzerException::new);
        return new GlueableInfoImpl(
                GlueableType.STATIC_METHOD,
                createMethodIdentifier(methodElement)
        );
    }

    private static String createMethodIdentifier(ExecutableElement methodElement) {
        final TypeElement enclosingElement = (TypeElement) methodElement.getEnclosingElement();
        return enclosingElement.getQualifiedName().toString() + "::" + methodElement.getSimpleName();
    }

    private static class GlueableInfoImpl implements GlueableInfo {

        private final GlueableType mType;
        private final String mIdentifier;

        private GlueableInfoImpl(GlueableType type, String identifier) {
            mType = type;
            mIdentifier = identifier;
        }

        @Override
        public GlueableType getType() {
            return mType;
        }

        @Override
        public String getIdentifier() {
            return mIdentifier;
        }
    }
}

package com.github.wrdlbrnft.gluemeister.utils;

import com.github.wrdlbrnft.gluemeister.GlueMeisterException;

import java.util.Set;
import java.util.function.BiFunction;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */

public class ElementUtils {

    public static <E extends GlueMeisterException> void verifyStaticModifier(Element element, BiFunction<String, Element, E> exceptionFactory) {
        if (!isStatic(element)) {
            throw exceptionFactory.apply("Element " + element.getSimpleName() + " is not accessible to GlueMeister. It has to be static!", element);
        }
    }

    public static <E extends GlueMeisterException> void verifyFinalModifier(Element element, BiFunction<String, Element, E> exceptionFactory) {
        if (!isFinal(element)) {
            throw exceptionFactory.apply("Element " + element.getSimpleName() + " cannot be used by GlueMeister. To ensure proper runtime behavior it has to be final!", element);
        }
    }

    public static <E extends GlueMeisterException> void verifyAccessibility(Element element, BiFunction<String, Element, E> exceptionFactory) {
        Element enclosingElement = element.getEnclosingElement();

        if (enclosingElement == null) {
            return;
        }

        if (enclosingElement.getKind() == ElementKind.PACKAGE) {
            return;
        }

        if (!hasPublicOrPackageLocalVisibility(element)) {
            throw exceptionFactory.apply("Element " + element.getSimpleName() + " is not accessible to GlueMeister. The element has to have at least package local or public visibility.", element);
        }

        while (enclosingElement.getKind() != ElementKind.PACKAGE) {

            if (!isStatic(enclosingElement)) {
                throw exceptionFactory.apply("Element " + element.getSimpleName() + " is not accessible to GlueMeister. It is nested inside " + enclosingElement.getSimpleName() + " and this element has to be static. Currently it is not static.", enclosingElement);
            }

            if (!hasPublicOrPackageLocalVisibility(enclosingElement)) {
                throw exceptionFactory.apply("Element " + element.getSimpleName() + " is not accessible to GlueMeister. It is nested inside " + enclosingElement.getSimpleName() + " and this element has to have at least package local or public visibility.", enclosingElement);
            }

            enclosingElement = enclosingElement.getEnclosingElement();

            if (enclosingElement == null) {
                return;
            }
        }
    }

    public static PackageElement findContainingPackage(Element element) {
        Element enclosingElement = element.getEnclosingElement();

        if (enclosingElement == null) {
            return null;
        }

        if (enclosingElement.getKind() == ElementKind.PACKAGE) {
            return (PackageElement) enclosingElement;
        }

        while (enclosingElement.getKind() != ElementKind.PACKAGE) {

            enclosingElement = enclosingElement.getEnclosingElement();

            if (enclosingElement == null) {
                return null;
            }
        }

        return (PackageElement) enclosingElement;
    }

    public static String findContainingPackageName(Element element) {
        final PackageElement packageElement = findContainingPackage(element);
        if (packageElement == null) {
            return "";
        }
        return packageElement.getQualifiedName().toString();
    }

    public static boolean isStatic(Element element) {
        return element.getEnclosingElement().getKind() == ElementKind.PACKAGE || element.getModifiers().contains(Modifier.STATIC);
    }

    public static boolean isFinal(Element element) {
        return element.getModifiers().contains(Modifier.FINAL);
    }

    public static boolean hasPublicOrPackageLocalVisibility(Element element) {
        final Set<Modifier> modifiers = element.getModifiers();
        if (modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.DEFAULT)) {
            return true;
        }

        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.PROTECTED)) {
            return false;
        }

        return true;
    }
}

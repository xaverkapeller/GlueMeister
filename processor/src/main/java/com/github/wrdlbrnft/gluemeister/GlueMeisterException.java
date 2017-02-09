package com.github.wrdlbrnft.gluemeister;

import javax.lang.model.element.Element;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 28/01/2017
 */

public class GlueMeisterException extends RuntimeException {

    private final Element mElement;

    public GlueMeisterException(String message, Element element) {
        super(message, null);
        mElement = element;
    }

    public GlueMeisterException(String message, Element element, Throwable cause) {
        super(message, cause);
        mElement = element;
    }

    public Element getElement() {
        return mElement;
    }
}

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
        super(message);
        mElement = element;
    }

    public GlueMeisterException(String message, Throwable cause, Element element) {
        super(message, cause);
        mElement = element;
    }

    public Element getElement() {
        return mElement;
    }
}

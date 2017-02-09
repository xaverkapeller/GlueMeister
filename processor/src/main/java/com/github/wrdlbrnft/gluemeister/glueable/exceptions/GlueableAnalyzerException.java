package com.github.wrdlbrnft.gluemeister.glueable.exceptions;

import com.github.wrdlbrnft.gluemeister.GlueMeisterException;

import javax.lang.model.element.Element;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */

public class GlueableAnalyzerException extends GlueMeisterException {

    public GlueableAnalyzerException(String message, Element element) {
        super(message, element);
    }

    public GlueableAnalyzerException(String message, Throwable cause, Element element) {
        super(message, element, cause);
    }
}

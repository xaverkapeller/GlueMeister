package com.github.wrdlbrnft.gluemeister.entities.exceptions;

import com.github.wrdlbrnft.gluemeister.GlueMeisterException;

import javax.lang.model.element.Element;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */

public class GlueEntityAnalyzerException extends GlueMeisterException {

    public GlueEntityAnalyzerException(String message, Element element) {
        super(message, element);
    }

    public GlueEntityAnalyzerException(String message, Throwable cause, Element element) {
        super(message, element, cause);
    }
}

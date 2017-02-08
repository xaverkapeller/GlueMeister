package com.github.wrdlbrnft.gluemeister.modules.exceptions;

import com.github.wrdlbrnft.gluemeister.GlueMeisterException;

import javax.lang.model.element.Element;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */

public class GlueModuleAnalyzerException extends GlueMeisterException {

    public GlueModuleAnalyzerException(String message, Element element) {
        super(message, element);
    }

    public GlueModuleAnalyzerException(String message, Throwable cause, Element element) {
        super(message, element, cause);
    }
}

package com.github.wrdlbrnft.gluemeister.modules.exceptions;

import com.github.wrdlbrnft.gluemeister.GlueMeisterException;

import javax.lang.model.element.Element;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 01/02/2017
 */

public class GlueModuleFactoryException extends GlueMeisterException {

    public GlueModuleFactoryException(String message, Element element) {
        super(message, element);
    }

    public GlueModuleFactoryException(String message, Element element, Throwable cause) {
        super(message, element, cause);
    }
}

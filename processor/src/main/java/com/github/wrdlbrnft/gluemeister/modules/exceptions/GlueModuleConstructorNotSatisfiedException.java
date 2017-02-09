package com.github.wrdlbrnft.gluemeister.modules.exceptions;

import javax.lang.model.element.Element;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 01/02/2017
 */

public class GlueModuleConstructorNotSatisfiedException extends GlueModuleFactoryException {

    public GlueModuleConstructorNotSatisfiedException(String message, Element element) {
        super(message, element);
    }

    public GlueModuleConstructorNotSatisfiedException(String message, Element element, Throwable cause) {
        super(message, element, cause);
    }
}

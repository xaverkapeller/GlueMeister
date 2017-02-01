package com.github.wrdlbrnft.gluemeister.entities.exceptions;

import javax.lang.model.element.Element;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 01/02/2017
 */

public class GlueEntityConstructorNotSatisfiedException extends GlueEntityFactoryException {

    public GlueEntityConstructorNotSatisfiedException(String message, Element element) {
        super(message, element);
    }

    public GlueEntityConstructorNotSatisfiedException(String message, Element element, Throwable cause) {
        super(message, element, cause);
    }
}

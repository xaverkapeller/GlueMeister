package com.github.wrdlbrnft.gluemeister.config.exceptions;

import com.github.wrdlbrnft.gluemeister.GlueMeisterException;

import javax.lang.model.element.Element;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 01/02/2017
 */

public class GlueMeisterConfigException extends GlueMeisterException {

    public GlueMeisterConfigException(String message, Element element) {
        super(message, element);
    }

    public GlueMeisterConfigException(String message, Element element, Throwable cause) {
        super(message, element, cause);
    }
}

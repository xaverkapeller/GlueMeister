package com.github.wrdlbrnft.gluemeister.glueable;

import javax.lang.model.element.Element;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */

public interface GlueableInfo {

    enum Kind {
        INSTANCE_METHOD,
        STATIC_METHOD,
        STATIC_FIELD,
        INTERFACE,
        ABSTRACT_CLASS,
        CLASS
    }

    Kind getKind();
    Element getElement();
    String getKey();
    boolean isEnabled();
}

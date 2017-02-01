package com.github.wrdlbrnft.gluemeister.glueable;

import javax.lang.model.element.Element;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */

public interface GlueableInfo {

    GlueableType getType();
    Element getElement();
    String getKey();
}

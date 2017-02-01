package com.github.wrdlbrnft.gluemeister.entities;

import javax.lang.model.element.TypeElement;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */

public interface GlueEntityInfo {
    TypeElement getEntityElement();
    String getFactoryPackageName();
    String getFactoryName();
}

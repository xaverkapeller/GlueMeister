package com.github.wrdlbrnft.gluemeister.modules;

import java.util.List;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */

public interface GlueModuleInfo {
    List<ExecutableElement> getUnimplementedMethods();
    TypeElement getEntityElement();
    String getFactoryPackageName();
    String getFactoryName();
}

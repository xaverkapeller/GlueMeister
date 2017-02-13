package com.github.wrdlbrnft.gluemeister.modules.factories;

import com.github.wrdlbrnft.gluemeister.glueable.GlueableInfo;

import javax.lang.model.type.DeclaredType;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 12/02/2017
 */
interface MatchedGlueable {
    GlueableInfo getInfo();
    DeclaredType getDeclaredType();
}

package com.github.wrdlbrnft.gluemeister.modules.factories;

import com.github.wrdlbrnft.codebuilder.variables.Field;

import javax.lang.model.type.TypeMirror;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 09/02/2017
 */
interface FieldInfo {
    TypeMirror getTypeMirror();
    Field getField();
}

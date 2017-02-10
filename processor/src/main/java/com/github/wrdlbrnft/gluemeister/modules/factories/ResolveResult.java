package com.github.wrdlbrnft.gluemeister.modules.factories;

import com.github.wrdlbrnft.codebuilder.code.CodeElement;
import com.github.wrdlbrnft.codebuilder.types.Type;

import javax.lang.model.type.DeclaredType;

/**
 * Created by kapeller on 09/02/2017.
 */
interface ResolveResult {
    Type getType();
    DeclaredType getBaseType();
    CodeElement getValue();
}

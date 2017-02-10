package com.github.wrdlbrnft.gluemeister.modules.factories;

import com.github.wrdlbrnft.codebuilder.implementations.Implementation;
import com.github.wrdlbrnft.codebuilder.variables.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.lang.model.type.TypeMirror;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 10/02/2017
 */

class FieldManager {

    private final List<FieldInfo> mFields = new ArrayList<>();

    private final Implementation.Builder mBuilder;

    FieldManager(Implementation.Builder builder) {
        mBuilder = builder;
    }

//    public Field getFieldFor(TypeMirror typeMirror, Supplier<Field> fieldSupplier) {
//        for (FieldInfo field : mFields) {
//            final TypeMirror fieldType = field.getTypeMirror();
//            if (isAssignable(typeMirror, fieldType)) {
//                return field.getField();
//            }
//        }
//        final Field field = fieldSupplier.get();
//        mBuilder.addField(field);
//        mFields.add(new FieldInfoImpl(typeMirror, field));
//        return field;
//    }
//
//    public void clear() {
//        mFields.clear();
//    }

    private static class FieldInfoImpl implements FieldInfo {

        private final TypeMirror mTypeMirror;
        private final Field mField;

        private FieldInfoImpl(TypeMirror typeMirror, Field field) {
            mTypeMirror = typeMirror;
            mField = field;
        }

        @Override
        public TypeMirror getTypeMirror() {
            return mTypeMirror;
        }

        @Override
        public Field getField() {
            return mField;
        }
    }
}

package com.github.wrdlbrnft.gluemeister.json;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 28/01/2017
 */
class ArrayBuilderImpl implements JsonBuilder.ArrayBuilder, JsonValue {

    private final List<JsonValue> mValues = new ArrayList<>();

    @Override
    public JsonBuilder.ObjectBuilder object() {
        final ObjectBuilderImpl builder = new ObjectBuilderImpl();
        mValues.add(builder);
        return builder;
    }

    @Override
    public JsonBuilder.ArrayBuilder value(String value) {
        mValues.add(new StringJsonValue(value));
        return this;
    }

    @Override
    public String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int i = 0, size = mValues.size(); i < size; i++) {

            if (i > 0) {
                builder.append(",");
            }

            final JsonValue value = mValues.get(i);
            builder.append(value.toJson());
        }
        builder.append("]");
        return builder.toString();
    }
}

package com.github.wrdlbrnft.gluemeister.json;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 28/01/2017
 */

class ObjectBuilderImpl implements JsonBuilder.ObjectBuilder, JsonValue {

    private final List<Property> mProperties = new ArrayList<>();

    public JsonBuilder.ObjectBuilder property(String key, String value) {
        mProperties.add(new Property(key, new StringJsonValue(value)));
        return this;
    }

    public JsonBuilder.ArrayBuilder array(String key) {
        final ArrayBuilderImpl builder = new ArrayBuilderImpl();
        mProperties.add(new Property(key, builder));
        return builder;
    }

    public JsonBuilder.ObjectBuilder object(String key) {
        final ObjectBuilderImpl builder = new ObjectBuilderImpl();
        mProperties.add(new Property(key, builder));
        return builder;
    }

    @Override
    public String toJson() {
        final StringBuilder builder = new StringBuilder();
        builder.append("{");
        for (int i = 0, size = mProperties.size(); i < size; i++) {

            if (i > 0) {
                builder.append(",");
            }

            final Property property = mProperties.get(i);
            builder.append("\"").append(property.getKey()).append("\":");
            builder.append(property.getValue().toJson());
        }
        builder.append("}");
        return builder.toString();
    }
}

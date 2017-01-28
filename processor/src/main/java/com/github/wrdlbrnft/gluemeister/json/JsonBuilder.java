package com.github.wrdlbrnft.gluemeister.json;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 28/01/2017
 */

public class JsonBuilder implements JsonValue {

    public interface ArrayBuilder {
        ObjectBuilder object();
        ArrayBuilder value(String value);
    }

    public interface ObjectBuilder {
        ObjectBuilder property(String key, String value);
        ArrayBuilder array(String key);
        ObjectBuilder object(String key);
    }

    private final ObjectBuilderImpl mRootObject = new ObjectBuilderImpl();

    public JsonBuilder property(String key, String value) {
        mRootObject.property(key, value);
        return this;
    }

    public ArrayBuilder array(String key) {
        return mRootObject.array(key);
    }

    public ObjectBuilder object(String key) {
        return mRootObject.object(key);
    }

    @Override
    public String toJson() {
        return mRootObject.toJson();
    }
}

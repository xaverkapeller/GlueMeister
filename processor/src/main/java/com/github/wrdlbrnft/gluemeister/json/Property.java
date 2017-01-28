package com.github.wrdlbrnft.gluemeister.json;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 28/01/2017
 */
class Property {
    private final String mKey;
    private final JsonValue mValue;

    Property(String key, JsonValue value) {
        mKey = key;
        mValue = value;
    }

    public String getKey() {
        return mKey;
    }

    public JsonValue getValue() {
        return mValue;
    }
}

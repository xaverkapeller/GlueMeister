package com.github.wrdlbrnft.gluemeister.json;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 28/01/2017
 */

class StringJsonValue implements JsonValue {

    private final String mValue;

    StringJsonValue(String value) {
        mValue = value;
    }

    @Override
    public String toJson() {
        return "\"" + mValue + "\"";
    }
}

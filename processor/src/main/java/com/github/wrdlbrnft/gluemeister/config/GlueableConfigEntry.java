package com.github.wrdlbrnft.gluemeister.config;

import com.github.wrdlbrnft.gluemeister.glueable.GlueableType;
import com.google.gson.annotations.SerializedName;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 30/01/2017
 */
class GlueableConfigEntry {

    @SerializedName("identifier")
    private String mIdentifier;

    @SerializedName("type")
    private GlueableType mType;

    @SerializedName("key")
    private String mKey;

    public GlueableConfigEntry(String identifier, GlueableType type, String key) {
        mIdentifier = identifier;
        mType = type;
        mKey = key;
    }

    private GlueableConfigEntry() {
    }

    public String getIdentifier() {
        return mIdentifier;
    }

    public GlueableType getType() {
        return mType;
    }

    public String getKey() {
        return mKey;
    }
}

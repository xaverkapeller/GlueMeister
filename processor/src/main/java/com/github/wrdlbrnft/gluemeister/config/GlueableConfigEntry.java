package com.github.wrdlbrnft.gluemeister.config;

import com.github.wrdlbrnft.gluemeister.glueable.GlueableInfo;
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
    private GlueableInfo.Kind mKind;

    @SerializedName("key")
    private String mKey;

    public GlueableConfigEntry(String identifier, GlueableInfo.Kind kind, String key) {
        mIdentifier = identifier;
        mKind = kind;
        mKey = key;
    }

    private GlueableConfigEntry() {
    }

    public String getIdentifier() {
        return mIdentifier;
    }

    public GlueableInfo.Kind getKind() {
        return mKind;
    }

    public String getKey() {
        return mKey;
    }
}

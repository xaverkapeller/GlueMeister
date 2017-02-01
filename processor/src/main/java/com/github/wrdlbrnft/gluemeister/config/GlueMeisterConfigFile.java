package com.github.wrdlbrnft.gluemeister.config;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 30/01/2017
 */

class GlueMeisterConfigFile {

    @SerializedName("entities")
    private List<GlueEntityConfigEntry> mEntityConfigEntries;

    @SerializedName("glueables")
    private List<GlueableConfigEntry> mGlueableConfigEntries;

    public GlueMeisterConfigFile(List<GlueEntityConfigEntry> entityConfigEntries, List<GlueableConfigEntry> glueableConfigEntries) {
        mEntityConfigEntries = entityConfigEntries;
        mGlueableConfigEntries = glueableConfigEntries;
    }

    private GlueMeisterConfigFile() {
    }

    public List<GlueEntityConfigEntry> getEntityConfigEntries() {
        return mEntityConfigEntries;
    }

    public List<GlueableConfigEntry> getGlueableConfigEntries() {
        return mGlueableConfigEntries;
    }
}

package com.github.wrdlbrnft.gluemeister.config;

import com.google.gson.annotations.SerializedName;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 01/02/2017
 */
class GlueEntityConfigEntry {

    @SerializedName("entityClass")
    private String mEntityClass;

    @SerializedName("factoryName")
    private String mFactoryClassName;

    @SerializedName("factoryPackageName")
    private String mFactoryPackageName;

    public GlueEntityConfigEntry(String entityClass, String factoryPackageName, String factoryClassName) {
        mEntityClass = entityClass;
        mFactoryClassName = factoryClassName;
        mFactoryPackageName = factoryPackageName;
    }

    private GlueEntityConfigEntry() {
    }

    public String getEntityClassName() {
        return mEntityClass;
    }

    public String getFactoryClassName() {
        return mFactoryClassName;
    }

    public String getFactoryPackageName() {
        return mFactoryPackageName;
    }
}

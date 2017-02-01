package com.github.wrdlbrnft.gluemeister.config;

import com.github.wrdlbrnft.gluemeister.entities.GlueEntityInfo;
import com.github.wrdlbrnft.gluemeister.glueable.GlueableInfo;

import java.util.List;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 01/02/2017
 */
public interface GlueMeisterConfig {
    List<GlueableInfo> getGlueableInfos();
    List<GlueEntityInfo> getGlueEntityInfos();
}

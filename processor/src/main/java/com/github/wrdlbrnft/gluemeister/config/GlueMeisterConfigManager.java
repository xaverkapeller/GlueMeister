package com.github.wrdlbrnft.gluemeister.config;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.StandardLocation;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 01/02/2017
 */

public class GlueMeisterConfigManager {

    private final GlueMeisterConfigReader mReader;
    private final GlueMeisterConfigWriter mWriter;

    public GlueMeisterConfigManager(ProcessingEnvironment processingEnv) {
        mReader = new GlueMeisterConfigReader(processingEnv);
        mWriter = new GlueMeisterConfigWriter(processingEnv);
    }

    public void writeConfigFile(GlueMeisterConfig config, String fileName) {
        mWriter.writeConfigFile(config, StandardLocation.SOURCE_OUTPUT, "com.github.wrdlbrnft.gluemeister", fileName);
    }

    public GlueMeisterConfig readConfigFile(String fileName) {
        return mReader.readConfigFile(StandardLocation.SOURCE_OUTPUT, "com.github.wrdlbrnft.gluemeister", fileName);
    }
}

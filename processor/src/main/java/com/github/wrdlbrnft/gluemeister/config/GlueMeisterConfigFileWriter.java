package com.github.wrdlbrnft.gluemeister.config;

import com.github.wrdlbrnft.gluemeister.entities.GlueEntityInfo;
import com.github.wrdlbrnft.gluemeister.glueable.GlueableInfo;
import com.github.wrdlbrnft.gluemeister.json.JsonBuilder;

import java.util.List;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 29/01/2017
 */

public class GlueMeisterConfigFileWriter {

    public GlueMeisterConfigFile write(List<GlueEntityInfo> entities, List<GlueableInfo> glueables) {
        final JsonBuilder builder = new JsonBuilder();

        final JsonBuilder.ArrayBuilder entitiesArray = builder.array("entities");
        for (GlueEntityInfo entity : entities) {
            final String entityName = entity.getEntityElement().getQualifiedName().toString();
            entitiesArray.value(entityName);
        }

        final JsonBuilder.ArrayBuilder glueablesArray = builder.array("glueables");
        for (GlueableInfo glueable : glueables) {
            final JsonBuilder.ObjectBuilder glueableInfoObject = glueablesArray.object();
            glueableInfoObject.property("type", glueable.getType().name());
            glueableInfoObject.property("identifier", glueable.getIdentifier());
        }

        return new GlueMeisterConfigFileImpl(builder.toJson());
    }

    private static class GlueMeisterConfigFileImpl implements GlueMeisterConfigFile {

        private final String mContent;

        private GlueMeisterConfigFileImpl(String content) {
            mContent = content;
        }

        @Override
        public String toString() {
            return mContent;
        }
    }
}

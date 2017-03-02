package com.github.wrdlbrnft.gluemeister.config;

import com.github.wrdlbrnft.gluemeister.GlueMeisterException;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileManager;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 01/02/2017
 */

class GlueMeisterConfigWriter {

    private final Gson mGson = new Gson();

    private final ProcessingEnvironment mProcessingEnvironment;

    GlueMeisterConfigWriter(ProcessingEnvironment processingEnvironment) {
        mProcessingEnvironment = processingEnvironment;
    }

    void writeConfigFile(GlueMeisterConfig config, JavaFileManager.Location location, String packageName, String fileName) {
        try (final Writer writer = mProcessingEnvironment.getFiler()
                .createResource(location, packageName, fileName)
                .openWriter()) {
            final GlueMeisterConfigFile file = formatConfig(config);
            final String json = mGson.toJson(file);
            writer.append(json);
        } catch (IOException e) {
            throw new GlueMeisterException("Failed to generate glue-meister.json", null, e);
        }
    }

    private GlueMeisterConfigFile formatConfig(GlueMeisterConfig config) {
        final List<GlueEntityConfigEntry> entityConfigEntries = config.getGlueModuleInfos().stream()
                .map(info -> new GlueEntityConfigEntry(
                        info.getEntityElement().getQualifiedName().toString(),
                        info.getFactoryPackageName(),
                        info.getFactoryName()
                ))
                .collect(Collectors.toList());
        final List<GlueableConfigEntry> glueableConfigEntries = config.getGlueableInfos().stream()
                .map(info -> new GlueableConfigEntry(
                        createIdentifier(info.getElement()),
                        info.getKind(),
                        info.getKey(),
                        info.isEnabled()
                ))
                .collect(Collectors.toList());
        return new GlueMeisterConfigFile(
                entityConfigEntries,
                glueableConfigEntries
        );
    }

    private String createIdentifier(Element element) {
        if (element instanceof TypeElement) {
            final TypeElement typeElement = (TypeElement) element;
            return typeElement.getQualifiedName().toString();
        }

        if (element instanceof VariableElement) {
            final VariableElement variableElement = (VariableElement) element;
            final TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
            return enclosingElement.getQualifiedName() + "#" + variableElement.getSimpleName();
        }

        if (element instanceof ExecutableElement) {
            final ExecutableElement executableElement = (ExecutableElement) element;
            final TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
            return enclosingElement.getQualifiedName() + "::" + executableElement.getSimpleName();
        }

        throw new GlueMeisterException("Failed to create identifier for element: " + element, null);
    }
}

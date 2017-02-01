package com.github.wrdlbrnft.gluemeister.config;

import com.github.wrdlbrnft.gluemeister.GlueMeisterException;
import com.github.wrdlbrnft.gluemeister.config.exceptions.GlueMeisterConfigException;
import com.github.wrdlbrnft.gluemeister.entities.GlueEntityInfo;
import com.github.wrdlbrnft.gluemeister.glueable.GlueableInfo;
import com.github.wrdlbrnft.gluemeister.glueable.GlueableType;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 01/02/2017
 */

class GlueMeisterConfigReader {

    private final Gson mGson = new Gson();

    private final ProcessingEnvironment mProcessingEnvironment;

    GlueMeisterConfigReader(ProcessingEnvironment processingEnvironment) {
        mProcessingEnvironment = processingEnvironment;
    }

    GlueMeisterConfig readConfigFile(JavaFileManager.Location location, String packageName, String fileName) {
        try {
            final FileObject resource = mProcessingEnvironment.getFiler().getResource(
                    location,
                    packageName,
                    fileName
            );
            final InputStream inputStream = resource.openInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            final String json = reader.lines().collect(Collectors.joining());
            final GlueMeisterConfigFile configFile = mGson.fromJson(json, GlueMeisterConfigFile.class);
            final List<GlueEntityInfo> entities = parseGlueEntityInfos(configFile);
            final List<GlueableInfo> glueables = parseGlueableInfos(configFile);
            return new GlueMeisterConfigImpl(entities, glueables);
        } catch (IOException e) {
            throw new GlueMeisterException("Failed to read config file. Something seems to be wrong with your project setup!", null, e);
        }
    }

    private List<GlueableInfo> parseGlueableInfos(GlueMeisterConfigFile configFile) {
        final List<GlueableInfo> glueables = new ArrayList<>();
        for (GlueableConfigEntry configEntry : configFile.getGlueableConfigEntries()) {
            try {
                glueables.add(parseGlueableConfigEntry(configEntry));
            } catch (GlueMeisterConfigException e) {
                mProcessingEnvironment.getMessager().printMessage(Diagnostic.Kind.NOTE,
                        e.getMessage(),
                        e.getElement()
                );
            }
        }
        return glueables;
    }

    private List<GlueEntityInfo> parseGlueEntityInfos(GlueMeisterConfigFile configFile) {
        final List<GlueEntityInfo> entities = new ArrayList<>();
        for (GlueEntityConfigEntry configEntry : configFile.getEntityConfigEntries()) {
            try {
                entities.add(parseGlueEntityConfigEntry(configEntry));
            } catch (GlueMeisterConfigException e) {
                mProcessingEnvironment.getMessager().printMessage(Diagnostic.Kind.NOTE,
                        e.getMessage(),
                        e.getElement()
                );
            }
        }
        return entities;
    }

    private GlueEntityInfo parseGlueEntityConfigEntry(GlueEntityConfigEntry configEntry) {
        final TypeElement element = mProcessingEnvironment.getElementUtils().getTypeElement(configEntry.getEntityClassName());
        if (element == null) {
            throw new GlueMeisterConfigException("Failed to find class mentioned in config file of some dependency: " + configEntry.getEntityClassName(), null);
        }
        return new GlueEntityInfoImpl(
                element,
                configEntry.getFactoryPackageName(),
                configEntry.getFactoryClassName()
        );
    }

    private GlueableInfo parseGlueableConfigEntry(GlueableConfigEntry configEntry) {
        final GlueableType type = configEntry.getType();
        final String identifier = configEntry.getIdentifier();
        return new GlueableInfoImpl(
                type,
                findGlueableElement(type, identifier),
                configEntry.getKey()
        );
    }

    private Element findGlueableElement(GlueableType type, String identifier) {
        switch (type) {

            case STATIC_METHOD:
                return findMethod(identifier);

            case STATIC_FIELD:
                return findField(identifier);

            case INTERFACE:
            case ABSTRACT_CLASS:
            case CLASS:
                return findType(identifier);

            default:
                throw new GlueMeisterException("Encountered unknown GlueableType: " + type, null);
        }
    }

    private ExecutableElement findMethod(String identifier) {
        final int index = identifier.lastIndexOf("::");
        final String className = identifier.substring(0, index);
        final String methodName = identifier.substring(index + 2, identifier.length());
        final TypeElement type = mProcessingEnvironment.getElementUtils().getTypeElement(className);
        if (type == null) {
            throw new GlueMeisterConfigException("Failed to find class \"" + className + "\" containing the static method \"" + methodName + "\". A dependency listed this method as @Glueable in their GlueMeister config file (Most likely cause of this error is obfuscation).", null);
        }

        final Element method = type.getEnclosedElements().stream()
                .filter(element -> element.getSimpleName().toString().equals(methodName))
                .findFirst().orElseThrow(() -> new GlueMeisterConfigException("Failed to find static method \"" + methodName + "\" in class \"" + className + "\". A dependency listed this method as @Glueable in their GlueMeister config file (Most likely cause of this error is obfuscation).", null));

        if (method.getKind() != ElementKind.METHOD) {
            throw new GlueMeisterConfigException("Failed to find static method \"" + methodName + "\" in class \"" + className + "\". An element with this name was present, but it was not a method! A dependency listed this method as @Glueable in their GlueMeister config file (A probable - yet unlikely - cause for this error is obfuscation).", null);
        }

        return (ExecutableElement) method;
    }

    private VariableElement findField(String identifier) {
        final int index = identifier.lastIndexOf("#");
        final String className = identifier.substring(0, index);
        final String fieldName = identifier.substring(index + 1, identifier.length());
        final TypeElement type = mProcessingEnvironment.getElementUtils().getTypeElement(className);
        if (type == null) {
            throw new GlueMeisterConfigException("Failed to find class \"" + className + "\" containing the static field \"" + fieldName + "\". A dependency listed this field as @Glueable in their GlueMeister config file (Most likely cause of this error is obfuscation).", null);
        }

        final Element field = type.getEnclosedElements().stream()
                .filter(element -> element.getSimpleName().toString().equals(fieldName))
                .findFirst().orElseThrow(() -> new GlueMeisterConfigException("Failed to find static field \"" + fieldName + "\" in class \"" + className + "\". A dependency listed this method as @Glueable in their GlueMeister config file (Most likely cause of this error is obfuscation).", null));

        if (field.getKind() != ElementKind.FIELD) {
            throw new GlueMeisterConfigException("Failed to find static field \"" + fieldName + "\" in class \"" + className + "\". An element with this name was present, but it was not a field! A dependency listed this field as @Glueable in their GlueMeister config file (A probable - yet unlikely - cause for this error is obfuscation).", null);
        }

        return (VariableElement) field;
    }

    private TypeElement findType(String identifier) {
        final TypeElement typeElement = mProcessingEnvironment.getElementUtils().getTypeElement(identifier);
        if (typeElement == null) {
            throw new GlueMeisterConfigException("Failed to find class \"" + identifier + "\". A dependency listed this class as @Glueable in their GlueMeister config file (Most likely cause of this error is obfuscation).", null);
        }
        return typeElement;
    }

    private static class GlueMeisterConfigImpl implements GlueMeisterConfig {

        private final List<GlueEntityInfo> mGlueEntityInfos;
        private final List<GlueableInfo> mGlueableInfos;

        private GlueMeisterConfigImpl(List<GlueEntityInfo> glueEntityInfos, List<GlueableInfo> glueableInfos) {
            mGlueEntityInfos = glueEntityInfos;
            mGlueableInfos = glueableInfos;
        }

        @Override
        public List<GlueEntityInfo> getGlueEntityInfos() {
            return mGlueEntityInfos;
        }

        @Override
        public List<GlueableInfo> getGlueableInfos() {
            return mGlueableInfos;
        }
    }

    private static class GlueEntityInfoImpl implements GlueEntityInfo {

        private final TypeElement mEntityElement;
        private final String mFactoryPackageName;
        private final String mFactoryName;

        private GlueEntityInfoImpl(TypeElement entityElement, String factoryPackageName, String factoryName) {
            mEntityElement = entityElement;
            mFactoryPackageName = factoryPackageName;
            mFactoryName = factoryName;
        }

        @Override
        public TypeElement getEntityElement() {
            return mEntityElement;
        }

        @Override
        public String getFactoryPackageName() {
            return mFactoryPackageName;
        }

        @Override
        public String getFactoryName() {
            return mFactoryName;
        }
    }

    private static class GlueableInfoImpl implements GlueableInfo {

        private final GlueableType mType;
        private final Element mElement;
        private final String mKey;

        private GlueableInfoImpl(GlueableType type, Element element, String key) {
            mType = type;
            mElement = element;
            mKey = key;
        }

        @Override
        public GlueableType getType() {
            return mType;
        }

        @Override
        public Element getElement() {
            return mElement;
        }

        @Override
        public String getKey() {
            return mKey;
        }
    }
}

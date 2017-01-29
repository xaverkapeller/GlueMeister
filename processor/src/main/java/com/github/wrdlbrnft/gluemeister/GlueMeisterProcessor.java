package com.github.wrdlbrnft.gluemeister;

import com.github.wrdlbrnft.codebuilder.code.SourceFile;
import com.github.wrdlbrnft.gluemeister.config.GlueMeisterConfigFile;
import com.github.wrdlbrnft.gluemeister.config.GlueMeisterConfigFileWriter;
import com.github.wrdlbrnft.gluemeister.entities.GlueEntityAnalyzer;
import com.github.wrdlbrnft.gluemeister.entities.GlueEntityInfo;
import com.github.wrdlbrnft.gluemeister.entities.factories.GlueEntityFactoryBuilder;
import com.github.wrdlbrnft.gluemeister.entities.factories.GlueEntityFactoryInfo;
import com.github.wrdlbrnft.gluemeister.glueable.GlueableAnalyzer;
import com.github.wrdlbrnft.gluemeister.glueable.GlueableInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 28/01/2017
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class GlueMeisterProcessor extends AbstractProcessor {

    private int mRound = 0;

    private GlueMeisterConfigFileWriter mConfigFileWriter;
    private GlueableAnalyzer mGlueableAnalyzer;
    private GlueEntityAnalyzer mGlueEntityAnalyzer;
    private GlueEntityFactoryBuilder mGlueEntityFactoryBuilder;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        mConfigFileWriter = new GlueMeisterConfigFileWriter();
        mGlueableAnalyzer = new GlueableAnalyzer(processingEnv);
        mGlueEntityAnalyzer = new GlueEntityAnalyzer(processingEnv);
        mGlueEntityFactoryBuilder = new GlueEntityFactoryBuilder(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        System.out.println(String.format(Locale.getDefault(), "\t# Round %d: Generating GlueMeister components...", mRound));

        try {
            performProcessing(roundEnv);
        } catch (GlueMeisterException e) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    e.getMessage(),
                    e.getElement()
            );
            if (e.getCause() != null) {
                e.getCause().printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Some unknown error occurred in the GlueMeister Compiler. It's most likely a bug in GlueMeister. Look at the stacktrace in the build log to figure out what is triggering it. Please report it as an issue on GitHub: https://github.com/Wrdlbrnft/GlueMeister/issues"
            );
        } finally {
            mRound++;
        }

        return false;
    }

    private void performProcessing(RoundEnvironment roundEnv) {
        readDependenciesConfigFile();
        final List<GlueEntityInfo> glueEntityInfos = mGlueEntityAnalyzer.analyze(roundEnv);
        for (GlueEntityInfo glueEntityInfo : glueEntityInfos) {
            final GlueEntityFactoryInfo entityFactoryInfo = mGlueEntityFactoryBuilder.build(glueEntityInfo);
            try {
                final SourceFile sourceFile = SourceFile.create(processingEnv, entityFactoryInfo.getPackageName());
                sourceFile.write(entityFactoryInfo.getImplementation());
                sourceFile.flushAndClose();
            } catch (IOException e) {
                final TypeElement entityElement = glueEntityInfo.getEntityElement();
                throw new GlueMeisterException("Failed to create factory for GlueEntity " + entityElement.getSimpleName() + ".", entityElement, e);
            }
        }

        final List<GlueableInfo> glueableInfos = mGlueableAnalyzer.analyze(roundEnv);
        final GlueMeisterConfigFile configFile = mConfigFileWriter.write(glueEntityInfos, glueableInfos);

        try (final Writer writer = openOutputFile()) {
            writer.append(configFile.toString());
        } catch (IOException e) {
            e.printStackTrace();
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate glue-meister.json");
        }
    }

    private void readDependenciesConfigFile() {
        try {
            final FileObject resource = processingEnv.getFiler().getResource(StandardLocation.SOURCE_OUTPUT, "com.github.wrdlbrnft.gluemeister", "glue-meister-dependencies.json");
            final InputStream inputStream = resource.openInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            final String json = reader.lines().collect(Collectors.joining());

        } catch (IOException e) {
            throw new GlueMeisterException("Failed to read config file. Something seems to be wrong with your project setup!", null, e);
        }
    }

    private Writer openOutputFile() throws IOException {
        return processingEnv.getFiler()
                .createResource(StandardLocation.SOURCE_OUTPUT, "com.github.wrdlbrnft.gluemeister", String.format(Locale.getDefault(), "glue-meister_%d.json", mRound))
                .openWriter();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        final Set<String> annotations = new HashSet<>();
        annotations.add(GlueEntity.class.getCanonicalName());
        annotations.add(GlueInject.class.getCanonicalName());
        annotations.add(Glueable.class.getCanonicalName());
        return annotations;
    }
}

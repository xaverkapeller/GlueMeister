package com.github.wrdlbrnft.gluemeister;

import com.github.wrdlbrnft.codebuilder.code.SourceFile;
import com.github.wrdlbrnft.gluemeister.config.GlueMeisterConfig;
import com.github.wrdlbrnft.gluemeister.config.GlueMeisterConfigManager;
import com.github.wrdlbrnft.gluemeister.modules.GlueModuleAnalyzer;
import com.github.wrdlbrnft.gluemeister.modules.GlueModuleInfo;
import com.github.wrdlbrnft.gluemeister.modules.exceptions.GlueModuleFactoryException;
import com.github.wrdlbrnft.gluemeister.modules.factories.GlueModuleFactoryBuilder;
import com.github.wrdlbrnft.gluemeister.modules.factories.GlueModuleFactoryInfo;
import com.github.wrdlbrnft.gluemeister.glueable.GlueableAnalyzer;
import com.github.wrdlbrnft.gluemeister.glueable.GlueableInfo;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 28/01/2017
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class GlueMeisterProcessor extends AbstractProcessor {

    private GlueableAnalyzer mGlueableAnalyzer;
    private GlueModuleAnalyzer mGlueModuleAnalyzer;
    private GlueModuleFactoryBuilder mGlueModuleFactoryBuilder;
    private GlueMeisterConfigManager mConfigManager;

    private int mRound = 0;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        mGlueableAnalyzer = new GlueableAnalyzer(processingEnv);
        mGlueModuleAnalyzer = new GlueModuleAnalyzer(processingEnv);
        mGlueModuleFactoryBuilder = new GlueModuleFactoryBuilder(processingEnv);
        mConfigManager = new GlueMeisterConfigManager(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        System.out.println(String.format(Locale.getDefault(), "\t# Round %d: Generating GlueMeister components...", mRound));

        try {
            tryProcessing(roundEnv);
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

    private void tryProcessing(RoundEnvironment roundEnv) {
        final GlueMeisterConfig dependencyConfig = mConfigManager.readConfigFile("glue-meister-dependencies.json");
        final GlueMeisterConfig currentConfig = analyzeCurrentEnvironment(roundEnv);

        buildGlueMeisterComponents(currentConfig, dependencyConfig);

        final String outputConfigName = String.format(Locale.getDefault(), "glue-meister_%d.json", mRound);
        mConfigManager.writeConfigFile(currentConfig, outputConfigName);
    }

    private GlueMeisterConfig analyzeCurrentEnvironment(RoundEnvironment roundEnv) {
        final List<GlueModuleInfo> glueModuleInfos = mGlueModuleAnalyzer.analyze(roundEnv);
        final List<GlueableInfo> glueableInfos = mGlueableAnalyzer.analyze(roundEnv);
        return new GlueMeisterConfigImpl(glueModuleInfos, glueableInfos);
    }

    private void buildGlueMeisterComponents(GlueMeisterConfig currentConfig, GlueMeisterConfig dependencyConfig) {
        final List<GlueModuleInfo> glueModuleInfos = currentConfig.getGlueModuleInfos();
        final List<GlueableInfo> allGlueables = Stream.of(currentConfig.getGlueableInfos(), dependencyConfig.getGlueableInfos())
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        for (GlueModuleInfo glueModuleInfo : glueModuleInfos) {
            try {
                final GlueModuleFactoryInfo entityFactoryInfo = mGlueModuleFactoryBuilder.build(glueModuleInfo, allGlueables);
                final SourceFile sourceFile = SourceFile.create(processingEnv, entityFactoryInfo.getPackageName());
                sourceFile.write(entityFactoryInfo.getImplementation());
                sourceFile.flushAndClose();
            } catch (GlueModuleFactoryException e) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        e.getMessage(),
                        e.getElement()
                );
            } catch (IOException e) {
                final TypeElement entityElement = glueModuleInfo.getEntityElement();
                throw new GlueMeisterException("Failed to create factory for GlueEntity " + entityElement.getSimpleName() + ".", entityElement, e);
            }
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        final Set<String> annotations = new HashSet<>();
        annotations.add(GlueModule.class.getCanonicalName());
        annotations.add(GlueInject.class.getCanonicalName());
        annotations.add(Glueable.class.getCanonicalName());
        return annotations;
    }

    private static class GlueMeisterConfigImpl implements GlueMeisterConfig {

        private final List<GlueModuleInfo> mGlueModuleInfos;
        private final List<GlueableInfo> mGlueableInfos;

        private GlueMeisterConfigImpl(List<GlueModuleInfo> glueModuleInfos, List<GlueableInfo> glueableInfos) {
            mGlueModuleInfos = glueModuleInfos;
            mGlueableInfos = glueableInfos;
        }

        public List<GlueModuleInfo> getGlueModuleInfos() {
            return mGlueModuleInfos;
        }

        @Override
        public List<GlueableInfo> getGlueableInfos() {
            return mGlueableInfos;
        }
    }
}

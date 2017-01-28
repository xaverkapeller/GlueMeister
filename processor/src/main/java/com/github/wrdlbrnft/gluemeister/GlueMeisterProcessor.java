package com.github.wrdlbrnft.gluemeister;

import com.github.wrdlbrnft.gluemeister.json.JsonBuilder;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 28/01/2017
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class GlueMeisterProcessor extends AbstractProcessor {

    private int mRound = 0;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        System.out.println(String.format(Locale.getDefault(), "\t# Round %2d: Generating GlueMeister components...", mRound));

        try {
            performProcessing(roundEnv);
        } catch (GlueMeisterException e) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    e.getMessage(),
                    e.getElement()
            );
        } catch (Exception e) {
            e.printStackTrace();
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Some unknown error occurred in the GlueMeister Compile. It's most likely a bug in GlueMeister. Look at the stacktrace in the build log to figure out what is triggering it. Please report it as an issue on GitHub: https://github.com/Wrdlbrnft/GlueMeister/issues"
            );
        } finally {
            mRound++;
        }

        return false;
    }

    private void performProcessing(RoundEnvironment roundEnv) {
        final List<TypeElement> entities = roundEnv.getElementsAnnotatedWith(GlueEntity.class).stream()
                .map(TypeElement.class::cast)
                .collect(Collectors.toList());

        final JsonBuilder builder = new JsonBuilder();
        final JsonBuilder.ArrayBuilder entitiesArrayBuilder = builder.array("entities");
        for (int i = 0, size = entities.size(); i < size; i++) {
            final TypeElement entity = entities.get(i);
            entitiesArrayBuilder.value(entity.getQualifiedName().toString());
        }

        try (final Writer writer = openOutputFile()) {
            writer.append(builder.toJson());
        } catch (IOException e) {
            e.printStackTrace();
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate glue-meister.json");
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
        annotations.add(GlueProvide.class.getCanonicalName());
        return annotations;
    }
}

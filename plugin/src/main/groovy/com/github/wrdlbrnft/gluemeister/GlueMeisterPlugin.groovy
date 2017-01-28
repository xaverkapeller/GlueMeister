package com.github.wrdlbrnft.gluemeister

import com.github.wrdlbrnft.gluemeister.BuildConfig
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

import java.util.zip.ZipFile

/**
 *  Created with IntelliJ IDEA<br>
 *  User: Xaver<br>
 *  Date: 27/01/2017
 */
class GlueMeisterPlugin implements Plugin<Project> {

    @SuppressWarnings("GroovyAssignabilityCheck")
    @Override
    void apply(Project project) {

        final projectInfo = ProjectInfo.analyzeProject(project)

        project.dependencies {
            provided 'com.github.wrdlbrnft:glue-meister-api:' + BuildConfig.VERSION
            annotationProcessor 'com.github.wrdlbrnft:glue-meister-processor:' + BuildConfig.VERSION
        }

        project.afterEvaluate {

            project.android[projectInfo.variants].all { variant ->

                if (projectInfo.library) {
                    project.tasks.findByName(NameUtils.createVariantTaskName('bundle', variant)).configure {
                        from new File(project.buildDir, 'generated/glue/' + variant.name)
                    }
                }

                def javaCompile = (variant.hasProperty('javaCompiler') ? variant.javaCompiler : variant.javaCompile) as Task
                javaCompile.doFirst {
                    beforeCompile(project)
                }

                javaCompile.doLast {
                    afterCompilation(project, projectInfo, variant)
                }
            }
        }
    }

    static beforeCompile(project) {
        printLogo()
        println()
        println '\tGlueMeister is performing its magic! Are you ready for the magic show?'
        println()

        final Map<File, Object> fileConfigMap = new HashMap<>();

        println '\t# Now scanning dependencies for GlueMeister componenents...'
        final Set<File> files = new HashSet<>();
        project.configurations.compile.resolve().forEach { files.add(it) }
        project.configurations.provided.resolve().forEach { files.add(it) }
        files.forEach { file ->
            if (!fileConfigMap.containsKey(file)) {
                def zipFile = new ZipFile(file)
                def entries = zipFile.entries().findAll {
                    it.name == 'glue-meister.json'
                }
                if (!entries.isEmpty()) {
                    println '\t# - Reading Config of ' + file.name
                    entries.each {
                        final slurper = new JsonSlurper();
                        final result = slurper.parseText(zipFile.getInputStream(it).text)
                        fileConfigMap.put(file, result)
                    }
                }
            }
        }
        println '\t# Scanning dependencies is done!'
        println()

        println '\tNow your code will be compiled and GlueMeister components generated...'
        println '\tCompilation starts now...'
        println()
    }

    static afterCompilation(Project project, ProjectInfo projectInfo, variant) {
        println()
        println '\tCompilation is done! GlueMeister components generated.'

        if (projectInfo.library) {
            println '\tNow we are exporting GlueMeister configuration.'
            println '\tThis enabled other projects depending on this library to use GlueMeister components you defined here.'
            println()

            final List<String> entities = new ArrayList<>();

            def files = new FileNameFinder().getFileNames(project.buildDir.absolutePath, '**/' + variant.name + '/com/github/wrdlbrnft/gluemeister/glue-meister_*.json')
            files.each {
                final slurper = new JsonSlurper();
                final result = slurper.parseText(new File(it).text)
                entities.addAll(result.entities);
            }

            def builder = new JsonBuilder();
            builder entities: entities

            println '\tExported Entities:'
            entities.each {
                println '\t - ' + it
            }

            final glueOutputDir = new File(project.buildDir, 'generated/glue/' + variant.name)
            glueOutputDir.mkdirs()
            final glueJson = new File(glueOutputDir, 'glue-meister.json')
            glueJson.withWriter { file -> file.append(builder.toString()) }
        }

        println()
        println '\tGlueMeister is done! Hope you enjoyed the show.'
        println()
    }

    static printLogo() {
        println '\t  _____ _            __  __      _     _'
        println '\t / ____| |          |  \\/  |    (_)   | |'
        println '\t| |  __| |_   _  ___| \\  / | ___ _ ___| |_ ___ _ __'
        println '\t| | |_ | | | | |/ _ \\ |\\/| |/ _ \\ / __| __/ _ \\ \'__|'
        println '\t| |__| | | |_| |  __/ |  | |  __/ \\__ \\ ||  __/ |'
        println '\t \\_____|_|\\__,_|\\___|_|  |_|\\___|_|___/\\__\\___|_|'
    }
}
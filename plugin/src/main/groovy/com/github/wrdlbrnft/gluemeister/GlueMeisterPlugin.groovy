package com.github.wrdlbrnft.gluemeister

import com.github.wrdlbrnft.gluemeister.BuildConfig
import groovy.io.FileType
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

        project.afterEvaluate {

            final projectInfo = ProjectInfo.analyzeProject(project)

            project.dependencies {
                compileOnly 'com.github.wrdlbrnft:glue-meister-api:' + BuildConfig.VERSION
                annotationProcessor 'com.github.wrdlbrnft:glue-meister-processor:' + BuildConfig.VERSION
            }

            project.android[projectInfo.variants].all { variant ->

                if (projectInfo.library) {
                    project.tasks.findByName(NameUtils.createVariantTaskName('bundle', variant)).configure {
                        from new File(project.buildDir, 'generated/glue/' + getBuildPath(variant))
                    }
                }

                def javaCompile = (variant.hasProperty('javaCompiler') ? variant.javaCompiler : variant.javaCompile) as Task
                javaCompile.doFirst {
                    beforeCompile(project, variant)
                }

                javaCompile.doLast {
                    afterCompilation(project, projectInfo, variant)
                }
            }
        }
    }

    static beforeCompile(Project project, variant) {
        printLogo()
        println()
        println 'GlueMeister is performing its magic! Are you ready for the magic show?'
        println()

        println '# Now scanning dependencies for GlueMeister componenents...'
        println '#'

        final entities = []
        final glueables = []

        final Set<File> files = new HashSet<>();
        project.configurations.implementation.resolve().forEach { files.add(it) }
        project.configurations.api.resolve().forEach { files.add(it) }
        project.configurations.compileOnly.resolve().forEach { files.add(it) }

        final flavor = variant.productFlavors[0]
        if (flavor) {
            project.configurations.getByName(flavor.name + 'Compile').resolve().forEach {
                files.add(it)
            }
        }

        files.forEach { file ->
            def zipFile = new ZipFile(file)
            def entries = zipFile.entries().findAll {
                it.name == 'glue-meister.json'
            }
            if (!entries.isEmpty()) {
                print '# - Reading Config of ' + file.name + ': '
                entries.each {
                    final slurper = new JsonSlurper();
                    final result = slurper.parseText(zipFile.getInputStream(it).text)
                    entities.addAll(result.entities)
                    glueables.addAll(result.glueables)

                    println 'Found ' + (result.entities.size() + result.glueables.size()) + ' GlueMeister components'
                }
            }
        }
        if (!entities.isEmpty() || !glueables.isEmpty()) {
            println '#'
            println '# Scanning dependencies is done!'
        } else {
            println '# Scanning dependencies is done! Nothing was found...'
        }

        final jsonBulder = new JsonBuilder();
        jsonBulder entities: entities, glueables: glueables, rootPackageName: variant.applicationId

        final outputDir = new File(project.buildDir, '/generated/source/apt/' + getBuildPath(variant) + '/com/github/wrdlbrnft/gluemeister')
        outputDir.mkdirs()
        final dependenciesFile = new File(outputDir, 'glue-meister-dependencies.json')
        dependenciesFile.withWriter {
            it.append(jsonBulder.toString())
        }

        println()
        println 'Now your code will be compiled and GlueMeister components generated...'
        println 'Compilation starts now...'
        println()
    }

    static afterCompilation(Project project, ProjectInfo projectInfo, variant) {        println()
        println 'Compilation is done! GlueMeister components generated.'

        if (projectInfo.library) {
            println 'Now we are exporting GlueMeister configuration.'
            println 'This enables other projects depending on this library to use GlueMeister components you defined here.'
            println()

            final List entities = new ArrayList<>();
            final List glueables = new ArrayList<>();

            project.buildDir.traverse(type: FileType.FILES, nameFilter: ~/glue-meister_[0-9]+.json/) {
                if (it.absolutePath.endsWith('/' + getBuildPath(variant) + '/com/github/wrdlbrnft/gluemeister/' + it.name)) {
                    final slurper = new JsonSlurper();
                    final result = slurper.parseText(it.text)
                    entities.addAll(result.entities);
                    glueables.addAll(result.glueables);
                }
            }

            def builder = new JsonBuilder();
            builder entities: entities, glueables: glueables

            final glueOutputDir = new File(project.buildDir, 'generated/glue/' + getBuildPath(variant))
            glueOutputDir.mkdirs()
            final glueJson = new File(glueOutputDir, 'glue-meister.json')
            glueJson.withWriter { file -> file.append(builder.toString()) }
        }

        println 'GlueMeister is done! Hope you enjoyed the show.'
        println()
    }

    static def getBuildPath(variant) {
        final flavor = variant.productFlavors[0]
        if (flavor) {
            return flavor.name + '/' + variant.buildType.name
        }
        return variant.name
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

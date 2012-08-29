/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.build.gradle

import com.android.build.gradle.internal.AndroidSourceSet
import com.android.build.gradle.internal.ApplicationVariant
import com.android.build.gradle.internal.ProductFlavorData
import com.android.builder.AndroidBuilder
import com.android.builder.DefaultSdkParser
import com.android.builder.ProductFlavor
import com.android.builder.SdkParser
import com.android.builder.VariantConfiguration
import com.android.utils.ILogger
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.Compile

/**
 * Base class for all Android plugins
 */
abstract class AndroidBasePlugin {

    public final static String INSTALL_GROUP = "Install"

    private final Map<Object, AndroidBuilder> builders = [:]

    protected Project project
    protected File sdkDir
    private DefaultSdkParser androidSdkParser
    private AndroidLogger androidLogger

    private ProductFlavorData defaultConfigData
    protected SourceSet mainSourceSet
    protected SourceSet testSourceSet

    abstract String getTarget()

    protected void apply(Project project) {
        this.project = project
        project.apply plugin: JavaBasePlugin

        mainSourceSet = project.sourceSets.add("main")
        testSourceSet = project.sourceSets.add("test")

        project.tasks.assemble.description =
            "Assembles all variants of all applications and secondary packages."

        findSdk(project)
    }

    protected setDefaultConfig(ProductFlavor defaultConfig) {
        defaultConfigData = new ProductFlavorData(defaultConfig, mainSourceSet,
                testSourceSet, project)
    }

    ProductFlavorData getDefaultConfigData() {
        return defaultConfigData
    }

    SdkParser getSdkParser() {
        if (androidSdkParser == null) {
            androidSdkParser = new DefaultSdkParser(sdkDir.absolutePath)
        }

        return androidSdkParser;
    }

    ILogger getLogger() {
        if (androidLogger == null) {
            androidLogger = new AndroidLogger(project.logger)
        }

        return androidLogger
    }

    boolean isVerbose() {
        return project.logger.isEnabled(LogLevel.DEBUG)
    }

    AndroidBuilder getAndroidBuilder(Object key) {
        return builders.get(key)
    }

    void setAndroidBuilder(Object key, AndroidBuilder androidBuilder) {
        builders.put(key, androidBuilder)
    }

    private void findSdk(Project project) {
        def localProperties = project.file("local.properties")
        if (localProperties.exists()) {
            Properties properties = new Properties()
            localProperties.withInputStream { instr ->
                properties.load(instr)
            }
            def sdkDirProp = properties.getProperty('sdk.dir')
            if (!sdkDirProp) {
                throw new RuntimeException("No sdk.dir property defined in local.properties file.")
            }
            sdkDir = new File(sdkDirProp)
        } else {
            def envVar = System.getenv("ANDROID_HOME")
            if (envVar != null) {
                sdkDir = new File(envVar)
            }
        }

        if (sdkDir == null) {
            throw new RuntimeException(
                    "SDK location not found. Define location with sdk.dir in the local.properties file or with an ANDROID_HOME environment variable.")
        }

        if (!sdkDir.directory) {
            throw new RuntimeException(
                    "The SDK directory '$sdkDir' specified in local.properties does not exist.")
        }
    }

    protected String getRuntimeJars(ApplicationVariant variant) {
        AndroidBuilder androidBuilder = getAndroidBuilder(variant)

        return androidBuilder.runtimeClasspath.join(":")
    }

    /**
     * Returns the folder directly under build/ into which the generated manifest is saved.
     */
    protected abstract String getManifestOutDir();

    protected ProcessManifest createProcessManifestTask(ApplicationVariant variant) {
        def processManifestTask = project.tasks.add("process${variant.name}Manifest",
                ProcessManifest)
        processManifestTask.plugin = this
        processManifestTask.variant = variant
        processManifestTask.conventionMapping.mergedManifest = {
            project.file(
                    "$project.buildDir/${getManifestOutDir()}/$variant.dirName/AndroidManifest.xml")
        }
        return processManifestTask
    }

    protected CrunchResources createCrunchResTask(ApplicationVariant variant) {
        def crunchTask = project.tasks.add("crunch${variant.name}Res", CrunchResources)
        crunchTask.plugin = this
        crunchTask.variant = variant
        crunchTask.conventionMapping.outputDir = {
            project.file("$project.buildDir/res/$variant.dirName")
        }
        return crunchTask
    }

    protected GenerateBuildConfigTask createBuildConfigTask(ApplicationVariant variant,
                                                            ProcessManifest processManifestTask) {
        def generateBuildConfigTask = project.tasks.add(
                "generate${variant.name}BuildConfig", GenerateBuildConfigTask)
        if (processManifestTask != null) {
            // This is in case the manifest is generated
            generateBuildConfigTask.dependsOn processManifestTask
        }
        generateBuildConfigTask.plugin = this
        generateBuildConfigTask.variant = variant
        generateBuildConfigTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/${variant.dirName}")
        }
        return generateBuildConfigTask
    }

    protected ProcessResources createProcessResTask(ApplicationVariant variant,
                                                    ProcessManifest processManifestTask,
                                                    CrunchResources crunchTask) {
        def processResources = project.tasks.add("process${variant.name}Res", ProcessResources)
        processResources.dependsOn processManifestTask, crunchTask
        processResources.plugin = this
        processResources.variant = variant
        processResources.conventionMapping.manifestFile = { processManifestTask.mergedManifest }
        processResources.conventionMapping.crunchDir = { crunchTask.outputDir }
        // TODO: unify with generateBuilderConfig somehow?
        processResources.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/$variant.dirName")
        }
        processResources.conventionMapping.packageFile = {
            project.file(
                    "$project.buildDir/libs/${project.archivesBaseName}-${variant.baseName}.ap_")
        }
        if (variant.runProguard) {
            processResources.conventionMapping.proguardFile = {
                project.file("$project.buildDir/proguard/${variant.dirName}/rules.txt")
            }
        }
        processResources.aaptOptions = extension.aaptOptions
        return processResources
    }

    protected void createCompileTask(ApplicationVariant variant,
                                     ApplicationVariant testedVariant,
                                     ProcessResources processResources,
                                     GenerateBuildConfigTask generateBuildConfigTask) {
        def compileTask = project.tasks.add("compile${variant.name}", Compile)
        compileTask.dependsOn processResources, generateBuildConfigTask

        VariantConfiguration config = variant.config

        List<Object> sourceList = new ArrayList<Object>();
        sourceList.add(((AndroidSourceSet) config.defaultSourceSet).sourceSet.java)
        sourceList.add({ processResources.sourceOutputDir })
        if (config.getType() != VariantConfiguration.Type.TEST) {
            sourceList.add(((AndroidSourceSet) config.buildTypeSourceSet).sourceSet.java)
        }
        if (config.hasFlavors()) {
            for (com.android.builder.SourceSet flavorSourceSet : config.flavorSourceSets) {
                sourceList.add(((AndroidSourceSet) flavorSourceSet).sourceSet.java)
            }
        }
        compileTask.source = sourceList.toArray()
        // TODO: support classpath per flavor
        if (testedVariant != null) {
            compileTask.classpath =
                ((AndroidSourceSet) config.defaultSourceSet).sourceSet.compileClasspath +
                        testedVariant.runtimeClasspath
        } else {
            compileTask.classpath =
                ((AndroidSourceSet) config.defaultSourceSet).sourceSet.compileClasspath
        }
        compileTask.conventionMapping.destinationDir = {
            project.file("$project.buildDir/classes/$variant.dirName")
        }
        compileTask.doFirst {
            compileTask.options.bootClasspath = getRuntimeJars(variant)
        }

        // Wire up the outputs
        // TODO: remove the classpath once the jar deps are set in the Variantconfig, so that we can pre-dex
        variant.runtimeClasspath = compileTask.outputs.files + compileTask.classpath
        variant.resourcePackage = project.files({processResources.packageFile}) {
            builtBy processResources
        }
        variant.compileTask = compileTask
    }
}

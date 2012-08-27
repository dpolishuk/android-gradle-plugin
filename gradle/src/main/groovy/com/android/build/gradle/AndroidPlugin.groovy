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

import com.android.build.gradle.internal.AndroidManifest
import com.android.build.gradle.internal.AndroidSourceSet
import com.android.build.gradle.internal.ApplicationVariant
import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.ProductionAppVariant
import com.android.build.gradle.internal.TestAppVariant
import com.android.builder.BuildType
import com.android.builder.ProductFlavor
import com.android.builder.VariantConfiguration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.Compile
import org.gradle.internal.reflect.Instantiator

class AndroidPlugin extends AndroidBasePlugin implements Plugin<Project> {
    private final Map<String, BuildTypeData> buildTypes = [:]
    private final Map<String, ProductFlavorData> productFlavors = [:]

    private AndroidExtension extension
    private AndroidManifest mainManifest

    @Override
    void apply(Project project) {
        super.apply(project)

        def buildTypes = project.container(BuildType)
        // TODO - do the decoration by default
        def productFlavors = project.container(ProductFlavor) { name ->
            project.services.get(Instantiator).newInstance(ProductFlavor, name)
        }

        extension = project.extensions.create('android', AndroidExtension, buildTypes, productFlavors)
        setDefaultConfig(extension.defaultConfig)

        findSdk(project)

        buildTypes.whenObjectAdded { BuildType buildType ->
            addBuildType(buildType)
        }
        buildTypes.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing build types is not implemented yet.")
        }

        buildTypes.create(BuildType.DEBUG)
        buildTypes.create(BuildType.RELEASE)

        productFlavors.whenObjectAdded { ProductFlavor productFlavor ->
            addProductFlavor(productFlavor)
        }

        productFlavors.whenObjectRemoved {
            throw new UnsupportedOperationException(
                    "Removing product flavors is not implemented yet.")
        }


        project.afterEvaluate {
            createAndroidTasks()
        }
    }

    private void addBuildType(BuildType buildType) {
        if (buildType.name.startsWith("test")) {
            throw new RuntimeException("BuildType names cannot start with 'test'")
        }
        if (productFlavors.containsKey(buildType.name)) {
            throw new RuntimeException("BuildType names cannot collide with ProductFlavor names")
        }

        def sourceSet = project.sourceSets.add(buildType.name)

        BuildTypeData buildTypeData = new BuildTypeData(buildType, sourceSet, project)
        project.tasks.assemble.dependsOn buildTypeData.assembleTask

        buildTypes[buildType.name] = buildTypeData
    }

    private void addProductFlavor(ProductFlavor productFlavor) {
        if (productFlavor.name.startsWith("test")) {
            throw new RuntimeException("ProductFlavor names cannot start with 'test'")
        }
        if (productFlavors.containsKey(productFlavor.name)) {
            throw new RuntimeException("ProductFlavor names cannot collide with BuildType names")
        }

        def mainSourceSet = project.sourceSets.add(productFlavor.name)
        def testSourceSet = project.sourceSets.add("test${productFlavor.name.capitalize()}")

        ProductFlavorData productFlavorData = new ProductFlavorData(
                productFlavor, mainSourceSet, testSourceSet, project)
        project.tasks.assemble.dependsOn productFlavorData.assembleTask

        productFlavors[productFlavor.name] = productFlavorData
    }

    private void createAndroidTasks() {
        if (productFlavors.isEmpty()) {
            createTasksForDefaultBuild()
        } else {
            productFlavors.values().each { ProductFlavorData productFlavorData ->
                createTasksForFlavoredBuild(productFlavorData)
            }
        }
    }

    private createTasksForDefaultBuild() {

        BuildTypeData testData = buildTypes[extension.testBuildType]
        if (testData == null) {
            throw new RuntimeException("Test Build Type '$extension.testBuildType' does not exist.")
        }

        VariantConfiguration testedVariantConfig = null
        ProductionAppVariant testedVariant = null

        ProductFlavorData defaultConfigData = getDefaultConfigData();

        for (BuildTypeData buildTypeData : buildTypes.values()) {

            def variantConfig = new VariantConfiguration(
                    defaultConfigData.productFlavor, defaultConfigData.androidSourceSet,
                    buildTypeData.buildType, buildTypeData.androidSourceSet,
                    VariantConfiguration.Type.DEFAULT)

            ProductionAppVariant productionAppVariant = addVariant(variantConfig,
                    buildTypeData.assembleTask)

            if (buildTypeData == testData) {
                testedVariantConfig = variantConfig
                testedVariant = productionAppVariant
            }
        }

        assert testedVariantConfig != null && testedVariant != null

        def variantTestConfig = new VariantConfiguration(
                defaultConfigData.productFlavor, defaultConfigData.androidSourceSet,
                testData.buildType, null,
                VariantConfiguration.Type.TEST)

        addTestVariant(variantTestConfig, testedVariantConfig, testedVariant)
    }

    private createTasksForFlavoredBuild(ProductFlavorData productFlavorData) {

        BuildTypeData testData = buildTypes[extension.testBuildType]
        if (testData == null) {
            throw new RuntimeException("Test Build Type '$extension.testBuildType' does not exist.")
        }

        VariantConfiguration testedVariantConfig = null
        ProductionAppVariant testedVariant = null

        for (BuildTypeData buildTypeData : buildTypes.values()) {

            def variantConfig = new VariantConfiguration(
                    extension.defaultConfig, getDefaultConfigData().androidSourceSet,
                    buildTypeData.buildType, buildTypeData.androidSourceSet,
                    VariantConfiguration.Type.DEFAULT)

            variantConfig.addProductFlavor(productFlavorData.productFlavor,
                    productFlavorData.androidSourceSet)

            ProductionAppVariant productionAppVariant = addVariant(variantConfig, null)

            buildTypeData.assembleTask.dependsOn productionAppVariant.assembleTask
            productFlavorData.assembleTask.dependsOn productionAppVariant.assembleTask

            if (buildTypeData == testData) {
                testedVariantConfig = variantConfig
                testedVariant = productionAppVariant
            }
        }

        assert testedVariantConfig != null && testedVariant != null

        def variantTestConfig = new VariantConfiguration(
                extension.defaultConfig, getDefaultConfigData().androidTestSourceSet,
                testData.buildType, null,
                VariantConfiguration.Type.TEST)
        variantTestConfig.addProductFlavor(productFlavorData.productFlavor,
                productFlavorData.androidTestSourceSet)

        addTestVariant(variantTestConfig, testedVariantConfig, testedVariant)
    }



    private AndroidManifest getMainManifest() {
        if (mainManifest == null) {
            mainManifest = new AndroidManifest()
            mainManifest.load(project.file("src/main/AndroidManifest.xml"))
        }
        return mainManifest
    }

    /**
     * Creates build tasks for a given variant.
     * @param variantConfig
     * @param assembleTask an optional assembleTask to be used. If null, a new one is created in the
     *                     returned ProductAppVariant instance.
     * @return
     */
    private ProductionAppVariant addVariant(VariantConfiguration variantConfig, Task assembleTask) {

        def variant = new ProductionAppVariant(variantConfig)

        // Add a task to merge the manifest(s)
        def mergeManifestTask = project.tasks.add("process${variant.name}Manifest", MergeManifest)
        mergeManifestTask.plugin = this
        mergeManifestTask.variant = variant
        mergeManifestTask.conventionMapping.mergedManifest = { project.file("$project.buildDir/manifests/$variant.dirName/AndroidManifest.xml") }

        // Add a task to crunch resource files
        def crunchTask = project.tasks.add("crunchAndroid${variant.name}Res", CrunchResources)
        crunchTask.plugin = this
        crunchTask.variant = variant
        crunchTask.conventionMapping.outputDir = { project.file("$project.buildDir/resources/$variant.dirName/res") }

        // Add a task to create the BuildConfig class
        def generateBuildConfigTask = project.tasks.add("generate${variant.name}BuildConfig", GenerateBuildConfigTask)
        generateBuildConfigTask.plugin = this
        generateBuildConfigTask.variant = variant
        generateBuildConfigTask.conventionMapping.sourceOutputDir = { project.file("$project.buildDir/source/${variant.dirName}") }

        // Add a task to generate resource source files
        def processResources = project.tasks.add("processAndroid${variant.name}Res", ProcessResources)
        processResources.dependsOn mergeManifestTask, crunchTask
        processResources.plugin = this
        processResources.variant = variant
        processResources.conventionMapping.manifestFile = { mergeManifestTask.mergedManifest }
        processResources.conventionMapping.crunchDir = { crunchTask.outputDir }
        // TODO: unify with generateBuilderConfig somehow?
        processResources.conventionMapping.sourceOutputDir = { project.file("$project.buildDir/source/$variant.dirName") }
        processResources.conventionMapping.packageFile = { project.file("$project.buildDir/libs/${project.archivesBaseName}-${variant.baseName}.ap_") }
        if (variantConfig.buildType.runProguard) {
            processResources.conventionMapping.proguardFile = { project.file("$project.buildDir/proguard/${variant.dirName}/rules.txt") }
        }
        processResources.aaptOptions = extension.aaptOptions

        // Add a compile task
        def compileTaskName = "compile${variant.name}"
        def compileTask = project.tasks.add(compileTaskName, Compile)
        compileTask.dependsOn processResources, generateBuildConfigTask
        if (variantConfig.hasFlavors()) {
            compileTask.source(
                    ((AndroidSourceSet)variantConfig.defaultSourceSet).sourceSet.java,
                    ((AndroidSourceSet)variantConfig.buildTypeSourceSet).sourceSet.java,
                    ((AndroidSourceSet)variantConfig.firstFlavorSourceSet).sourceSet.java,
                    { processResources.sourceOutputDir })
        } else {
            compileTask.source(
                    ((AndroidSourceSet)variantConfig.defaultSourceSet).sourceSet.java,
                    ((AndroidSourceSet)variantConfig.buildTypeSourceSet).sourceSet.java,
                    { processResources.sourceOutputDir })
        }
        // TODO: support classpath per flavor
        compileTask.classpath = ((AndroidSourceSet)variantConfig.defaultSourceSet).sourceSet.compileClasspath
        compileTask.conventionMapping.destinationDir = { project.file("$project.buildDir/classes/$variant.dirName") }
        compileTask.doFirst {
            compileTask.options.bootClasspath = getRuntimeJars(variant)
        }

        // Wire up the outputs
        // TODO: remove the classpath once the jar deps are set in the Variantconfig, so that we can pre-dex
        variant.runtimeClasspath = compileTask.outputs.files + compileTask.classpath
        variant.resourcePackage = project.files({processResources.packageFile}) { builtBy processResources }
        variant.compileTask = compileTask

        Task returnTask = addPackageTasks(variant, assembleTask)
        if (returnTask != null) {
            variant.assembleTask = returnTask
        }

        return variant;
    }

    private void addTestVariant(VariantConfiguration variantConfig,
                                VariantConfiguration testedVariantConfig,
                                ProductionAppVariant testedVariant) {

        def variant = new TestAppVariant(variantConfig, testedVariantConfig)

        // Add a task to merge the manifest(s)
        def mergeManifestTask = project.tasks.add("process${variant.name}Manifest", MergeManifest)
        mergeManifestTask.plugin = this
        mergeManifestTask.variant = variant
        mergeManifestTask.conventionMapping.mergedManifest = { project.file("$project.buildDir/manifests/$variant.dirName/AndroidManifest.xml") }

        // Add a task to crunch resource files
        def crunchTask = project.tasks.add("crunchAndroid${variant.name}Res", CrunchResources)
        crunchTask.plugin = this
        crunchTask.variant = variant
        crunchTask.conventionMapping.outputDir = { project.file("$project.buildDir/resources/$variant.dirName/res") }

        // Add a task to create the BuildConfig class
        def generateBuildConfigTask = project.tasks.add("generate${variant.name}BuildConfig", GenerateBuildConfigTask)
        generateBuildConfigTask.plugin = this
        generateBuildConfigTask.variant = variant
        generateBuildConfigTask.conventionMapping.sourceOutputDir = { project.file("$project.buildDir/source/${variant.dirName}") }

        // Add a task to generate resource source files
        def processResources = project.tasks.add("processAndroid${variant.name}Res", ProcessResources)
        processResources.dependsOn mergeManifestTask, crunchTask
        processResources.plugin = this
        processResources.variant = variant
        processResources.conventionMapping.manifestFile = { mergeManifestTask.mergedManifest }
        processResources.conventionMapping.crunchDir = { crunchTask.outputDir }
        // TODO: unify with generateBuilderConfig somehow?
        processResources.conventionMapping.sourceOutputDir = { project.file("$project.buildDir/source/$variant.dirName") }
        processResources.conventionMapping.packageFile = { project.file("$project.buildDir/libs/${project.archivesBaseName}-${variant.baseName}.ap_") }
        processResources.aaptOptions = extension.aaptOptions

        // Add a task to compile the test application
        def testCompile = project.tasks.add("compile${variant.name}", Compile)
        testCompile.dependsOn processResources, generateBuildConfigTask
        if (variantConfig.hasFlavors()) {
            testCompile.source(
                    ((AndroidSourceSet)variantConfig.defaultSourceSet).sourceSet.java,
                    ((AndroidSourceSet)variantConfig.firstFlavorSourceSet).sourceSet.java,
                    { processResources.sourceOutputDir })
        } else {
            testCompile.source(
                    ((AndroidSourceSet)variantConfig.defaultSourceSet).sourceSet.java,
                    { processResources.sourceOutputDir })
        }

        testCompile.classpath = ((AndroidSourceSet)variantConfig.defaultSourceSet).sourceSet.compileClasspath + testedVariant.runtimeClasspath
        testCompile.conventionMapping.destinationDir = { project.file("$project.buildDir/classes/$variant.dirName") }
        testCompile.doFirst {
            testCompile.options.bootClasspath = getRuntimeJars(variant)
        }

        // TODO: remove the classpath once the jar deps are set in the Variantconfig, so that we can pre-dex
        variant.runtimeClasspath = testCompile.outputs.files + testCompile.classpath
        variant.resourcePackage = project.files({processResources.packageFile}) { builtBy processResources }
        variant.compileTask = testCompile

        Task assembleTask = addPackageTasks(variant, null)

        project.tasks.check.dependsOn assembleTask
    }

    private Task addPackageTasks(ApplicationVariant variant, Task assembleTask) {
        // Add a dex task
        def dexTaskName = "dex${variant.name}"
        def dexTask = project.tasks.add(dexTaskName, Dex)
        dexTask.dependsOn variant.compileTask
        dexTask.plugin = this
        dexTask.variant = variant
        dexTask.conventionMapping.sourceFiles = { variant.runtimeClasspath }
        dexTask.conventionMapping.outputFile = { project.file("${project.buildDir}/libs/${project.archivesBaseName}-${variant.baseName}.dex") }
        dexTask.dexOptions = extension.dexOptions

        // Add a task to generate application package
        def packageApp = project.tasks.add("package${variant.name}", PackageApplication)
        packageApp.dependsOn variant.resourcePackage, dexTask
        packageApp.plugin = this
        packageApp.variant = variant

        def signedApk = variant.isSigned()

        def apkName = signedApk ?
            "${project.archivesBaseName}-${variant.baseName}-unaligned.apk" :
            "${project.archivesBaseName}-${variant.baseName}-unsigned.apk"

        packageApp.conventionMapping.outputFile = { project.file("$project.buildDir/apk/${apkName}") }
        packageApp.conventionMapping.resourceFile = { variant.resourcePackage.singleFile }
        packageApp.conventionMapping.dexFile = { dexTask.outputFile }

        def appTask = packageApp

        if (signedApk) {
            if (variant.zipAlign) {
                // Add a task to zip align application package
                def alignApp = project.tasks.add("zipalign${variant.name}", ZipAlign)
                alignApp.dependsOn packageApp
                alignApp.conventionMapping.inputFile = { packageApp.outputFile }
                alignApp.conventionMapping.outputFile = { project.file("$project.buildDir/apk/${project.archivesBaseName}-${variant.baseName}.apk") }
                alignApp.sdkDir = sdkDir

                appTask = alignApp
            }

            // Add a task to install the application package
            def installApp = project.tasks.add("install${variant.name}", InstallApplication)
            installApp.dependsOn appTask
            installApp.conventionMapping.packageFile = { appTask.outputFile }
            installApp.sdkDir = sdkDir
        }

        // Add an assemble task
        Task returnTask = null
        if (assembleTask == null) {
            assembleTask = project.tasks.add("assemble${variant.name}")
            assembleTask.description = variant.description
            assembleTask.group = "Build"
            returnTask = assembleTask
        }
        assembleTask.dependsOn appTask

        return returnTask
    }

    @Override
    String getTarget() {
        return extension.target;
    }
}

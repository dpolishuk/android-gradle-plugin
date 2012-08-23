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
import com.android.build.gradle.internal.ApplicationVariant
import com.android.build.gradle.internal.BuildTypeDimension
import com.android.build.gradle.internal.ProductFlavorDimension
import com.android.build.gradle.internal.ProductionAppVariant
import com.android.build.gradle.internal.TestAppVariant
import com.android.builder.BuildType
import com.android.builder.BuildTypeHolder
import com.android.builder.ProductFlavor
import com.android.builder.ProductFlavorHolder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.Compile
import org.gradle.internal.reflect.Instantiator

class AndroidPlugin extends AndroidBasePlugin implements Plugin<Project> {
    private final Set<ProductionAppVariant> variants = []
    private final Map<String, BuildTypeDimension> buildTypes = [:]
    private final Map<String, ProductFlavorDimension> productFlavors = [:]
    private SourceSet main
    private SourceSet test
    private AndroidExtension extension
    private AndroidManifest mainManifest

    @Override
    void apply(Project project) {
        super.apply(project)

        project.apply plugin: JavaBasePlugin

        def buildTypes = project.container(BuildType)
        // TODO - do the decoration by default
        def productFlavors = project.container(ProductFlavor) { name ->
            project.services.get(Instantiator).newInstance(ProductFlavor, name)
        }

        extension = project.extensions.create('android', AndroidExtension, buildTypes, productFlavors)

        findSdk(project)

        main = project.sourceSets.add('main')
        test = project.sourceSets.add('test')

        buildTypes.whenObjectAdded { BuildType buildType ->
            addBuildType(buildType)
        }
        buildTypes.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing build types is not implemented yet.")
        }

        buildTypes.create(BuildType.DEBUG)
        buildTypes.create(BuildType.RELEASE)

        productFlavors.whenObjectAdded { ProductFlavor flavor ->
            addProductFlavor(flavor)
        }
        productFlavors.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing product flavors is not implemented yet.")
        }

        productFlavors.create('main')

        project.tasks.assemble.dependsOn { variants.collect { "assemble${it.name}" } }
    }

    private AndroidManifest getMainManifest() {
        if (mainManifest == null) {
            mainManifest = new AndroidManifest()
            mainManifest.load(project.file("src/main/AndroidManifest.xml"))
        }
        return mainManifest
    }

    private void addBuildType(BuildType buildType) {
        if (buildType.name.startsWith("test")) {
            throw new RuntimeException("BuildType names cannot start with 'test'")
        }

        def sourceSet = project.sourceSets.add(buildType.name)

        def buildTypeDimension = new BuildTypeDimension(buildType, sourceSet, project.projectDir.absolutePath)
        buildTypes[buildType.name] = buildTypeDimension

        def assembleBuildType = project.tasks.add(buildTypeDimension.assembleTaskName)
        assembleBuildType.dependsOn {
            buildTypeDimension.variants.collect { "assemble$it.name" }
        }
        assembleBuildType.description = "Assembles all ${buildType.name} applications"
        assembleBuildType.group = "Build"

        productFlavors.values().each { flavor ->
            addVariant(buildTypeDimension, flavor)
        }
    }

    private void addProductFlavor(ProductFlavor productFlavor) {
        if (productFlavor.name.startsWith("test")) {
            throw new RuntimeException("ProductFlavor names cannot start with 'test'")
        }

        def mainSourceSet
        def testSourceSet
        if (productFlavor.name == 'main') {
            mainSourceSet = main
            testSourceSet = test
        } else {
            mainSourceSet = project.sourceSets.add(productFlavor.name)
            testSourceSet = project.sourceSets.add("test${productFlavor.name.capitalize()}")
        }

        def productFlavorDimension = new ProductFlavorDimension(productFlavor, mainSourceSet, testSourceSet, project.projectDir.absolutePath)
        productFlavors[productFlavor.name] = productFlavorDimension

        def assembleFlavour = project.tasks.add(productFlavorDimension.assembleTaskName)
        assembleFlavour.dependsOn {
            productFlavorDimension.variants.collect { "assemble${it.name}" }
        }
        assembleFlavour.description = "Assembles all ${productFlavor.name} applications"
        assembleFlavour.group = "Build"

        buildTypes.values().each { buildType ->
            addVariant(buildType, productFlavorDimension)
        }

        assert productFlavorDimension.debugVariant != null

        def testAppVariant = new TestAppVariant(productFlavorDimension)
        def flavorName = productFlavor.name.capitalize()

        // Add a task to generate the test app manifest
        def generateManifestTask = project.tasks.add("generate${flavorName}TestManifest", GenerateTestManifest)
        generateManifestTask.conventionMapping.outputFile = { project.file("$project.buildDir/manifests/test/$flavorName/AndroidManifest.xml") }
        generateManifestTask.conventionMapping.packageName = { getMainManifest().packageName + '.test' }

        // Add a task to crunch resource files
        def crunchTask = project.tasks.add("crunch${flavorName}TestResources", CrunchResources)
        crunchTask.plugin = this
        crunchTask.variant = testAppVariant
        crunchTask.conventionMapping.outputDir = { project.file("$project.buildDir/resources/test/$flavorName") }

        // Add a task to generate resource package
        def processResources = project.tasks.add("process${flavorName}TestResources", ProcessResources)
        processResources.dependsOn generateManifestTask, crunchTask
        processResources.plugin = this
        processResources.variant = testAppVariant
        processResources.conventionMapping.manifestFile = { generateManifestTask.outputFile }
        processResources.conventionMapping.crunchDir = { crunchTask.outputDir }
        // TODO: unify with generateManifestTask somehow?
        processResources.conventionMapping.sourceOutputDir = { project.file("$project.buildDir/source/test/$flavorName") }
        processResources.conventionMapping.packageFile = { project.file("$project.buildDir/libs/${project.archivesBaseName}-test${flavorName}.ap_") }
        processResources.aaptOptions = extension.aaptOptions

        // Add a task to compile the test application
        def testCompile = project.tasks.add("compile${flavorName}Test", Compile)
        testCompile.dependsOn processResources
        testCompile.source test.java, productFlavorDimension.testSource.java
        testCompile.classpath = test.compileClasspath + productFlavorDimension.debugVariant.runtimeClasspath
        testCompile.conventionMapping.destinationDir = { project.file("$project.buildDir/classes/test/$flavorName") }
        testCompile.doFirst {
            testCompile.options.bootClasspath = getRuntimeJars(testAppVariant)
        }


        testAppVariant.runtimeClasspath = testCompile.outputs.files + testCompile.classpath
        testAppVariant.resourcePackage = project.files({processResources.packageFile}) { builtBy processResources }
        testAppVariant.compileTask = testCompile
        addPackageTasks(testAppVariant)

        project.tasks.check.dependsOn "assemble${testAppVariant.name}"
    }

    private void addVariant(BuildTypeDimension buildType, ProductFlavorDimension productFlavor) {
        def variant = new ProductionAppVariant(buildType, productFlavor)
        variants << variant
        buildType.variants << variant
        productFlavor.variants << variant
        if (buildType.name == BuildType.DEBUG) {
            productFlavor.debugVariant = variant
        }

        // Add a task to merge the manifest(s)
        def mergeManifestTask = project.tasks.add("merge${variant.name}Manifest", MergeManifest)
        mergeManifestTask.plugin = this
        mergeManifestTask.variant = variant
        mergeManifestTask.conventionMapping.mergedManifest = { project.file("$project.buildDir/manifests/$variant.dirName/AndroidManifest.xml") }

        // Add a task to crunch resource files
        def crunchTask = project.tasks.add("crunch${variant.name}Resources", CrunchResources)
        crunchTask.plugin = this
        crunchTask.variant = variant
        crunchTask.conventionMapping.outputDir = { project.file("$project.buildDir/resources/$variant.dirName/res") }

        // Add a task to create the BuildConfig class
        def generateBuildConfigTask = project.tasks.add("generate${variant.name}BuildConfig", GenerateBuildConfigTask)
        generateBuildConfigTask.plugin = this
        generateBuildConfigTask.variant = variant
        generateBuildConfigTask.conventionMapping.sourceOutputDir = { project.file("$project.buildDir/source/${variant.dirName}") }

        // Add a task to generate resource source files
        def processResources = project.tasks.add("process${variant.name}Resources", ProcessResources)
        processResources.dependsOn mergeManifestTask, crunchTask
        processResources.plugin = this
        processResources.variant = variant
        processResources.conventionMapping.manifestFile = { mergeManifestTask.mergedManifest }
        processResources.conventionMapping.crunchDir = { crunchTask.outputDir }
        // TODO: unify with generateBuilderConfig somehow?
        processResources.conventionMapping.sourceOutputDir = { project.file("$project.buildDir/source/$variant.dirName") }
        processResources.conventionMapping.packageFile = { project.file("$project.buildDir/libs/${project.archivesBaseName}-${variant.baseName}.ap_") }
        if (buildType.buildType.runProguard) {
            processResources.conventionMapping.proguardFile = { project.file("$project.buildDir/proguard/${variant.dirName}/rules.txt") }
        }
        processResources.aaptOptions = extension.aaptOptions

        // Add a compile task
        def compileTaskName = "compile${variant.name}"
        def compileTask = project.tasks.add(compileTaskName, Compile)
        compileTask.dependsOn processResources, generateBuildConfigTask
        compileTask.source main.java, buildType.mainSource.java, productFlavor.mainSource.java, { processResources.sourceOutputDir }
        compileTask.classpath = main.compileClasspath
        compileTask.conventionMapping.destinationDir = { project.file("$project.buildDir/classes/$variant.dirName") }
        compileTask.doFirst {
            compileTask.options.bootClasspath = getRuntimeJars(variant)
        }

        // Wire up the outputs
        variant.runtimeClasspath = project.files(compileTask.outputs, main.compileClasspath)
        variant.resourcePackage = project.files({processResources.packageFile}) { builtBy processResources }
        variant.compileTask = compileTask

        addPackageTasks(variant)
    }

    private void addPackageTasks(ApplicationVariant variant) {
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

        logger.info(apkName)
        packageApp.conventionMapping.outputFile = { project.file("$project.buildDir/apk/${apkName}") }
        packageApp.conventionMapping.resourceFile = { variant.resourcePackage.singleFile }
        packageApp.conventionMapping.dexFile = { dexTask.outputFile }

        def appTask = packageApp

        if (signedApk && variant.zipAlign) {
            // Add a task to zip align application package
            def alignApp = project.tasks.add("zipalign${variant.name}", ZipAlign)
            alignApp.dependsOn packageApp
            alignApp.conventionMapping.inputFile = { packageApp.outputFile }
            alignApp.conventionMapping.outputFile = { project.file("$project.buildDir/apk/${project.archivesBaseName}-${variant.baseName}.apk") }
            alignApp.sdkDir = sdkDir

            appTask = alignApp
        }

        // Add an assemble task
        def assembleTask = project.tasks.add("assemble${variant.name}")
        assembleTask.dependsOn appTask
        assembleTask.description = "Assembles the ${variant.description} application"
        assembleTask.group = "Build"

        // Add a task to install the application package
        def installApp = project.tasks.add("install${variant.name}", InstallApplication)
        installApp.dependsOn appTask
        installApp.conventionMapping.packageFile = { appTask.outputFile }
        installApp.sdkDir = sdkDir
    }

    @Override
    String getTarget() {
        return extension.target;
    }

    @Override
    ProductFlavorHolder getMainFlavor() {
        return productFlavors.get('main')
    }

    @Override
    BuildTypeHolder getDebugType() {
        return buildTypes.get(BuildType.DEBUG)
    }
}

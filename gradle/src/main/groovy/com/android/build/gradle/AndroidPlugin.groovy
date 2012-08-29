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
import org.gradle.internal.reflect.Instantiator
import org.gradle.api.plugins.BasePlugin

class AndroidPlugin extends AndroidBasePlugin implements Plugin<Project> {
    private final Map<String, BuildTypeData> buildTypes = [:]
    private final Map<String, ProductFlavorData> productFlavors = [:]

    private AndroidExtension extension

    private Task installAllForTests
    private Task runAllTests
    private Task uninstallAll
    private Task assembleTest

    @Override
    void apply(Project project) {
        super.apply(project)

        def buildTypes = project.container(BuildType)
        // TODO - do the decoration by default
        def productFlavors = project.container(ProductFlavor) { name ->
            project.services.get(Instantiator).newInstance(ProductFlavor, name)
        }

        extension = project.extensions.create('android', AndroidExtension,
                buildTypes, productFlavors)
        setDefaultConfig(extension.defaultConfig)

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

        installAllForTests = project.tasks.add("installAllForTests")
        installAllForTests.group = "Verification"
        installAllForTests.description = "Installs all applications needed to run tests."

        runAllTests = project.tasks.add("runAllTests")
        runAllTests.group = "Verification"
        runAllTests.description = "Runs all tests."

        uninstallAll = project.tasks.add("uninstallAll")
        uninstallAll.description = "Uninstall all applications."
        uninstallAll.group = "Install"

        project.tasks.check.dependsOn installAllForTests, runAllTests

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
            // there'll be more than one test app, so we need a top level assembleTest
            assembleTest = project.tasks.add("assembleTest")
            assembleTest.group = BasePlugin.BUILD_GROUP
            assembleTest.description = "Assembles all the Test applications"

            productFlavors.values().each { ProductFlavorData productFlavorData ->
                createTasksForFlavoredBuild(productFlavorData)
            }
        }
    }

    /**
     * Creates Tasks for non-flavored build. This means assembleDebug, assembleRelease, and other
     * assemble<Type> are directly building the <type> build instead of all build of the given
     * <type>.
     */
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

            boolean isTestedVariant = (buildTypeData == testData)

            ProductionAppVariant productionAppVariant = addVariant(variantConfig,
                    buildTypeData.assembleTask, isTestedVariant)

            if (isTestedVariant) {
                testedVariantConfig = variantConfig
                testedVariant = productionAppVariant
            }
        }

        assert testedVariantConfig != null && testedVariant != null

        def variantTestConfig = new VariantConfiguration(
                defaultConfigData.productFlavor, defaultConfigData.androidTestSourceSet,
                testData.buildType, null,
                VariantConfiguration.Type.TEST)

        addTestVariant(variantTestConfig, testedVariantConfig, testedVariant)
    }

    /**
     * Creates Task for a given flavor. This will create tasks for all build types for the given
     * flavor.
     * @param productFlavorData the flavor to build.
     */
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

            boolean isTestedVariant = (buildTypeData == testData)

            ProductionAppVariant productionAppVariant = addVariant(variantConfig, null,
                    isTestedVariant)

            buildTypeData.assembleTask.dependsOn productionAppVariant.assembleTask
            productFlavorData.assembleTask.dependsOn productionAppVariant.assembleTask

            if (isTestedVariant) {
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

    /**
     * Creates build tasks for a given variant.
     * @param variantConfig
     * @param assembleTask an optional assembleTask to be used. If null, a new one is created in the
     *                     returned ProductAppVariant instance.
     * @param isTestApk whether this apk is needed for running tests
     * @return
     */
    private ProductionAppVariant addVariant(VariantConfiguration variantConfig, Task assembleTask,
                                            boolean isTestApk) {

        def variant = new ProductionAppVariant(variantConfig)

        // Add a task to process the manifest(s)
        ProcessManifest processManifestTask = createProcessManifestTask(variant)

        // Add a task to crunch resource files
        def crunchTask = createCrunchResTask(variant)

        // Add a task to create the BuildConfig class
        def generateBuildConfigTask = createBuildConfigTask(variant, null)

        // Add a task to generate resource source files
        def processResources = createProcessResTask(variant, processManifestTask, crunchTask)

        // Add a compile task
        createCompileTask(variant, null/*testedVariant*/, processResources, generateBuildConfigTask)

        Task returnTask = addPackageTasks(variant, assembleTask, isTestApk)
        if (returnTask != null) {
            variant.assembleTask = returnTask
        }

        return variant;
    }

    private void addTestVariant(VariantConfiguration variantConfig,
                                VariantConfiguration testedVariantConfig,
                                ProductionAppVariant testedVariant) {

        def variant = new TestAppVariant(variantConfig, testedVariantConfig)

        // Add a task to process the manifest
        def processManifestTask = createProcessManifestTask(variant)

        // Add a task to crunch resource files
        def crunchTask = createCrunchResTask(variant)

        // Add a task to create the BuildConfig class
        def generateBuildConfigTask = createBuildConfigTask(variant, processManifestTask)

        // Add a task to generate resource source files
        def processResources = createProcessResTask(variant, processManifestTask, crunchTask)

        // Add a task to compile the test application
        createCompileTask(variant, testedVariant, processResources, generateBuildConfigTask)

        Task assembleTask = addPackageTasks(variant, null, true /*isTestApk*/)

        if (assembleTest != null) {
            assembleTest.dependsOn assembleTask
        }

        def runTestsTask = project.tasks.add("Run${variant.name}Tests", RunTestsTask)
        runTestsTask.sdkDir = sdkDir
        runTestsTask.variant = variant

        runAllTests.dependsOn runTestsTask
    }

    private Task addPackageTasks(ApplicationVariant variant, Task assembleTask, boolean isTestApk) {
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
            installApp.description = "Installs the " + variant.description
            installApp.group = "Install"
            installApp.dependsOn appTask
            installApp.conventionMapping.packageFile = { appTask.outputFile }
            installApp.sdkDir = sdkDir

            if (isTestApk) {
                installAllForTests.dependsOn installApp
            }
        }

        // Add an assemble task
        Task returnTask = null
        if (assembleTask == null) {
            assembleTask = project.tasks.add("assemble${variant.name}")
            assembleTask.description = "Assembles the " + variant.description
            assembleTask.group = BasePlugin.BUILD_GROUP
            returnTask = assembleTask
        }
        assembleTask.dependsOn appTask

        // add an uninstall task
        def uninstallApp = project.tasks.add("uninstall${variant.name}", UninstallApplication)
        uninstallApp.description = "Uninstalls the " + variant.description
        uninstallApp.group = AndroidBasePlugin.INSTALL_GROUP
        uninstallApp.variant = variant
        uninstallApp.sdkDir = sdkDir

        uninstallAll.dependsOn uninstallApp

        return returnTask
    }

    @Override
    String getTarget() {
        return extension.target;
    }

    protected String getManifestOutDir() {
        return "manifests"
    }

}

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
import org.gradle.api.plugins.BasePlugin
import org.gradle.internal.reflect.Instantiator

class AndroidPlugin extends AndroidBasePlugin implements Plugin<Project> {
    private final Map<String, BuildTypeData> buildTypes = [:]
    private final Map<String, ProductFlavorData> productFlavors = [:]

    private AndroidExtension extension

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

        ProductionAppVariant testedVariant = null

        ProductFlavorData defaultConfigData = getDefaultConfigData();

        for (BuildTypeData buildTypeData : buildTypes.values()) {

            def variantConfig = new VariantConfiguration(
                    defaultConfigData.productFlavor, defaultConfigData.androidSourceSet,
                    buildTypeData.buildType, buildTypeData.androidSourceSet)
            // TODO: add actual dependencies
            variantConfig.setAndroidDependencies(null)

            boolean isTestedVariant = (buildTypeData == testData)

            ProductionAppVariant productionAppVariant = addVariant(variantConfig,
                    buildTypeData.assembleTask, isTestedVariant)

            if (isTestedVariant) {
                testedVariant = productionAppVariant
            }
        }

        assert testedVariant != null

        def testVariantConfig = new VariantConfiguration(
                defaultConfigData.productFlavor, defaultConfigData.androidTestSourceSet,
                testData.buildType, null,
                VariantConfiguration.Type.TEST, testedVariant.config)

        // TODO: add actual dependencies
        testVariantConfig.setAndroidDependencies(null)

        def testVariant = new TestAppVariant(testVariantConfig)
        createTestTasks(testVariant, testedVariant)
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

        ProductionAppVariant testedVariant = null

        for (BuildTypeData buildTypeData : buildTypes.values()) {

            def variantConfig = new VariantConfiguration(
                    extension.defaultConfig, getDefaultConfigData().androidSourceSet,
                    buildTypeData.buildType, buildTypeData.androidSourceSet)

            variantConfig.addProductFlavor(productFlavorData.productFlavor,
                    productFlavorData.androidSourceSet)

            // TODO: add actual dependencies
            variantConfig.setAndroidDependencies(null)

            boolean isTestedVariant = (buildTypeData == testData)

            ProductionAppVariant productionAppVariant = addVariant(variantConfig, null,
                    isTestedVariant)

            buildTypeData.assembleTask.dependsOn productionAppVariant.assembleTask
            productFlavorData.assembleTask.dependsOn productionAppVariant.assembleTask

            if (isTestedVariant) {
                testedVariant = productionAppVariant
            }
        }

        assert testedVariant != null

        def testVariantConfig = new VariantConfiguration(
                extension.defaultConfig, getDefaultConfigData().androidTestSourceSet,
                testData.buildType, null,
                VariantConfiguration.Type.TEST, testedVariant.config)
        testVariantConfig.addProductFlavor(productFlavorData.productFlavor,
                productFlavorData.androidTestSourceSet)

        // TODO: add actual dependencies
        testVariantConfig.setAndroidDependencies(null)

        def testVariant = new TestAppVariant(testVariantConfig)
        createTestTasks(testVariant, testedVariant)
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
        ProcessManifest processManifestTask = createProcessManifestTask(variant, "manifests")

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

    @Override
    String getTarget() {
        return extension.target;
    }
}

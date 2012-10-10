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
import com.android.build.gradle.internal.BuildTypeFactory
import com.android.build.gradle.internal.ConfigurationDependencies
import com.android.build.gradle.internal.GroupableProductFlavor
import com.android.build.gradle.internal.GroupableProductFlavorFactory
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.ProductionAppVariant
import com.android.build.gradle.internal.TestAppVariant
import com.android.builder.AndroidDependency
import com.android.builder.BuildType
import com.android.builder.JarDependency
import com.android.builder.VariantConfiguration
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePlugin
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject

class AppPlugin extends com.android.build.gradle.BasePlugin implements org.gradle.api.Plugin<Project> {
    private final Map<String, BuildTypeData> buildTypes = [:]
    private final Map<String, ProductFlavorData<GroupableProductFlavor>> productFlavors = [:]

    AppExtension extension

    @Inject
    public AppPlugin(Instantiator instantiator) {
        super(instantiator)
    }

    @Override
    void apply(Project project) {
        super.apply(project)

        def buildTypeContainer = project.container(BuildType, new BuildTypeFactory(instantiator))
        def productFlavorContainer = project.container(GroupableProductFlavor,
                new GroupableProductFlavorFactory(instantiator))

        extension = project.extensions.create('android', AppExtension,
                (ProjectInternal) project, instantiator, buildTypeContainer, productFlavorContainer)
        setDefaultConfig(extension.defaultConfig, extension.sourceSetsContainer)

        buildTypeContainer.whenObjectAdded { BuildType buildType ->
            addBuildType(buildType)
        }
        buildTypeContainer.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing build types is not implemented yet.")
        }

        buildTypeContainer.create(BuildType.DEBUG)
        buildTypeContainer.create(BuildType.RELEASE)

        productFlavorContainer.whenObjectAdded { GroupableProductFlavor productFlavor ->
            addProductFlavor(productFlavor)
        }

        productFlavorContainer.whenObjectRemoved {
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

        def sourceSet = extension.sourceSetsContainer.create(buildType.name)

        BuildTypeData buildTypeData = new BuildTypeData(buildType, sourceSet, project)
        project.tasks.assemble.dependsOn buildTypeData.assembleTask

        buildTypes[buildType.name] = buildTypeData
    }

    private void addProductFlavor(GroupableProductFlavor productFlavor) {
        if (productFlavor.name.startsWith("test")) {
            throw new RuntimeException("ProductFlavor names cannot start with 'test'")
        }
        if (buildTypes.containsKey(productFlavor.name)) {
            throw new RuntimeException("ProductFlavor names cannot collide with BuildType names")
        }

        def mainSourceSet = extension.sourceSetsContainer.create(productFlavor.name)
        String testName = "test${productFlavor.name.capitalize()}"
        def testSourceSet = extension.sourceSetsContainer.create(testName)

        ProductFlavorData<GroupableProductFlavor> productFlavorData =
                new ProductFlavorData<GroupableProductFlavor>(
                        productFlavor, mainSourceSet, testSourceSet, project)

        productFlavors[productFlavor.name] = productFlavorData
    }

    private void createAndroidTasks() {
        // resolve dependencies for all config
        List<ConfigurationDependencies> dependencies = []
        dependencies.addAll(buildTypes.values())
        dependencies.addAll(productFlavors.values())
        resolveDependencies(dependencies)

        // now create the tasks.
        if (productFlavors.isEmpty()) {
            createTasksForDefaultBuild()
        } else {
            // there'll be more than one test app, so we need a top level assembleTest
            assembleTest = project.tasks.add("assembleTest")
            assembleTest.group = BasePlugin.BUILD_GROUP
            assembleTest.description = "Assembles all the Test applications"

            // check whether we have multi flavor builds
            if (extension.flavorGroupList == null || extension.flavorGroupList.size() < 2) {
                productFlavors.values().each { ProductFlavorData productFlavorData ->
                    createTasksForFlavoredBuild(productFlavorData)
                }
            } else {
                // need to group the flavor per group.
                // First a map of group -> list(ProductFlavor)
                ArrayListMultimap<String, ProductFlavorData<GroupableProductFlavor>> map = ArrayListMultimap.create();
                productFlavors.values().each { ProductFlavorData<GroupableProductFlavor> productFlavorData ->
                    def flavor = productFlavorData.productFlavor
                    if (flavor.flavorGroup == null) {
                        throw new RuntimeException(
                                "Flavor ${flavor.name} has no flavor group.")
                    }
                    if (!extension.flavorGroupList.contains(flavor.flavorGroup)) {
                        throw new RuntimeException(
                                "Flavor ${flavor.name} has unknown group ${flavor.flavorGroup}.")
                    }

                    map.put(flavor.flavorGroup, productFlavorData)
                }

                // now we use the flavor groups to generate an ordered array of flavor to use
                ProductFlavorData[] array = new ProductFlavorData[extension.flavorGroupList.size()]
                createTasksForMultiFlavoredBuilds(array, 0, map)
            }
        }

        createDependencyReportTask()
    }

    private createTasksForMultiFlavoredBuilds(ProductFlavorData[] datas, int i,
                                              ListMultimap<String, ProductFlavorData> map) {
        if (i == datas.length) {
            createTasksForFlavoredBuild(datas)
            return
        }

        // fill the array at the current index
        def group = extension.flavorGroupList.get(i)
        def flavorList = map.get(group)
        for (ProductFlavorData flavor : flavorList) {
            datas[i] = flavor
            createTasksForMultiFlavoredBuilds(datas, i+1, map)
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
            List<ConfigurationDependencies> configDependencies = []
            configDependencies.add(defaultConfigData)
            configDependencies.add(buildTypeData)

            // list of dependency to set on the variantConfig
            List<JarDependency> jars = []
            jars.addAll(defaultConfigData.jars)
            jars.addAll(buildTypeData.jars)

            // the order of the libraries is important. In descending order:
            // build types, flavors, defaultConfig.
            List<AndroidDependency> libs = []
            libs.addAll(buildTypeData.libraries)
            // no flavors, just add the default config
            libs.addAll(defaultConfigData.libraries)

            def variantConfig = new VariantConfiguration(
                    defaultConfigData.productFlavor, defaultConfigData.sourceSet,
                    buildTypeData.buildType, buildTypeData.sourceSet)

            variantConfig.setJarDependencies(jars)
            variantConfig.setAndroidDependencies(libs)

            ProductionAppVariant productionAppVariant = addVariant(variantConfig,
                    buildTypeData.assembleTask, configDependencies)
            variants.add(productionAppVariant)

            if (buildTypeData == testData) {
                testedVariant = productionAppVariant
            }
        }

        assert testedVariant != null

        def testVariantConfig = new VariantConfiguration(
                defaultConfigData.productFlavor, defaultConfigData.testSourceSet,
                testData.buildType, null,
                VariantConfiguration.Type.TEST, testedVariant.config)

        List<ConfigurationDependencies> testConfigDependencies = []
        testConfigDependencies.add(defaultConfigData.testConfigDependencies)

        // list of dependency to set on the variantConfig
        List<JarDependency> testJars = []
        testJars.addAll(defaultConfigData.testConfigDependencies.jars)
        List<AndroidDependency> testLibs = []
        testLibs.addAll(defaultConfigData.testConfigDependencies.libraries)

        testVariantConfig.setJarDependencies(testJars)
        testVariantConfig.setAndroidDependencies(testLibs)

        def testVariant = new TestAppVariant(testVariantConfig)
        variants.add(testVariant)
        createTestTasks(testVariant, testedVariant, testConfigDependencies)
    }

    /**
     * Creates Task for a given flavor. This will create tasks for all build types for the given
     * flavor.
     * @param flavorDataList the flavor(s) to build.
     */
    private createTasksForFlavoredBuild(ProductFlavorData... flavorDataList) {

        BuildTypeData testData = buildTypes[extension.testBuildType]
        if (testData == null) {
            throw new RuntimeException("Test Build Type '$extension.testBuildType' does not exist.")
        }

        ProductionAppVariant testedVariant = null

        // assembleTask for this flavor(group)
        def assembleTask

        for (BuildTypeData buildTypeData : buildTypes.values()) {
            List<ConfigurationDependencies> configDependencies = []
            configDependencies.add(defaultConfigData)
            configDependencies.add(buildTypeData)

            // list of dependency to set on the variantConfig
            List<JarDependency> jars = []
            jars.addAll(defaultConfigData.jars)
            jars.addAll(buildTypeData.jars)

            // the order of the libraries is important. In descending order:
            // build types, flavors, defaultConfig.
            List<AndroidDependency> libs = []
            libs.addAll(buildTypeData.libraries)

            def variantConfig = new VariantConfiguration(
                    extension.defaultConfig, getDefaultConfigData().sourceSet,
                    buildTypeData.buildType, buildTypeData.sourceSet)

            for (ProductFlavorData data : flavorDataList) {
                variantConfig.addProductFlavor(data.productFlavor, data.sourceSet)
                jars.addAll(data.jars)
                libs.addAll(data.libraries)
                configDependencies.add(data)
            }

            // now add the defaultConfig
            libs.addAll(defaultConfigData.libraries)

            variantConfig.setJarDependencies(jars)
            variantConfig.setAndroidDependencies(libs)

            ProductionAppVariant productionAppVariant = addVariant(variantConfig, null,
                    configDependencies)
            variants.add(productionAppVariant)

            buildTypeData.assembleTask.dependsOn productionAppVariant.assembleTask

            if (assembleTask == null) {
                // create the task based on the name of the flavors.
                assembleTask = createAssembleTask(flavorDataList)
                project.tasks.assemble.dependsOn assembleTask
            }
            assembleTask.dependsOn productionAppVariant.assembleTask

            if (buildTypeData == testData) {
                testedVariant = productionAppVariant
            }
        }

        assert testedVariant != null

        def testVariantConfig = new VariantConfiguration(
                extension.defaultConfig, getDefaultConfigData().testSourceSet,
                testData.buildType, null,
                VariantConfiguration.Type.TEST, testedVariant.config)

        List<ConfigurationDependencies> testConfigDependencies = []
        testConfigDependencies.add(defaultConfigData.testConfigDependencies)

        // list of dependency to set on the variantConfig
        List<JarDependency> testJars = []
        testJars.addAll(defaultConfigData.testConfigDependencies.jars)

        // the order of the libraries is important. In descending order:
        // flavors, defaultConfig.
        List<AndroidDependency> testLibs = []

        for (ProductFlavorData data : flavorDataList) {
            testVariantConfig.addProductFlavor(data.productFlavor, data.testSourceSet)
            testJars.addAll(data.testConfigDependencies.jars)
            testLibs.addAll(data.testConfigDependencies.libraries)
        }

        // now add the default config
        testLibs.addAll(defaultConfigData.testConfigDependencies.libraries)

        testVariantConfig.setJarDependencies(testJars)
        testVariantConfig.setAndroidDependencies(testLibs)

        def testVariant = new TestAppVariant(testVariantConfig)
        variants.add(testVariant)
        createTestTasks(testVariant, testedVariant, testConfigDependencies)
    }

    private Task createAssembleTask(ProductFlavorData[] flavorDataList) {
        def name = ProductFlavorData.getFlavoredName(flavorDataList, true)

        def assembleTask = project.tasks.add("assemble${name}")
        assembleTask.description = "Assembles all builds for flavor ${name}"
        assembleTask.group = "Build"

        return assembleTask
    }

    /**
     * Creates build tasks for a given variant.
     * @param variantConfig
     * @param assembleTask an optional assembleTask to be used. If null, a new one is created.
     * @return
     */
    private ProductionAppVariant addVariant(VariantConfiguration variantConfig, Task assembleTask,
                                            List<ConfigurationDependencies> configDependencies) {

        def variant = new ProductionAppVariant(variantConfig)

        def prepareDependenciesTask = createPrepareDependenciesTask(variant, configDependencies)

        // Add a task to process the manifest(s)
        def processManifestTask = createProcessManifestTask(variant, "manifests")
        // TODO - move this
        processManifestTask.dependsOn prepareDependenciesTask

        // Add a task to crunch resource files
        def crunchTask = createCrunchResTask(variant)

        // Add a task to create the BuildConfig class
        def generateBuildConfigTask = createBuildConfigTask(variant, null)

        // Add a task to generate resource source files
        def processResources = createProcessResTask(variant, processManifestTask, crunchTask)

        // Add a task to process the java resources
        createProcessJavaResTask(variant)

        def compileAidl = createAidlTask(variant)
        // TODO - move this
        compileAidl.dependsOn prepareDependenciesTask

        // Add a compile task
        createCompileTask(variant, null/*testedVariant*/, processResources, generateBuildConfigTask,
                compileAidl)

        addPackageTasks(variant, assembleTask)

        return variant;
    }

    @Override
    String getTarget() {
        return extension.target;
    }
}

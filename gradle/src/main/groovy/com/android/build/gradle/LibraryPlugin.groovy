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
import com.android.builder.AndroidDependency
import com.android.builder.BuildType
import com.android.builder.BundleDependency
import com.android.builder.VariantConfiguration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip

class LibraryPlugin extends BasePlugin implements Plugin<Project> {

    private final static String DIR_BUNDLES = "bundles";

    LibraryExtension extension
    BuildTypeData debugBuildTypeData
    BuildTypeData releaseBuildTypeData

    @Override
    void apply(Project project) {
        super.apply(project)

        extension = project.extensions.create('android', LibraryExtension,
                (ProjectInternal) project)
        setDefaultConfig(extension.defaultConfig, extension.sourceSetsContainer)

        // create the source sets for the build type.
        // the ones for the main product flavors are handled by the base plugin.
        def debugSourceSet = extension.sourceSetsContainer.create(BuildType.DEBUG)
        def releaseSourceSet = extension.sourceSetsContainer.create(BuildType.RELEASE)

        debugBuildTypeData = new BuildTypeData(extension.debug, debugSourceSet, project)
        releaseBuildTypeData = new BuildTypeData(extension.release, releaseSourceSet, project)
        project.tasks.assemble.dependsOn debugBuildTypeData.assembleTask
        project.tasks.assemble.dependsOn releaseBuildTypeData.assembleTask

        createConfigurations()

        project.afterEvaluate {
            createAndroidTasks()
        }
    }

    void createConfigurations() {
        def debugConfig = project.configurations.add(BuildType.DEBUG)
        def releaseConfig = project.configurations.add(BuildType.RELEASE)
        debugConfig.extendsFrom(project.configurations["package"])
        releaseConfig.extendsFrom(project.configurations["package"])
        project.configurations["default"].extendsFrom(releaseConfig)

        // Adjust the pom scope mappings
        // TODO - this should be part of JavaBase plugin. Fix this in Gradle
        project.plugins.withType(MavenPlugin) {
            project.conf2ScopeMappings.addMapping(300, project.configurations.compile, "runtime")
            project.conf2ScopeMappings.addMapping(300, project.configurations["package"], "runtime")
            project.conf2ScopeMappings.addMapping(300, releaseConfig, "runtime")
        }
    }

    void createAndroidTasks() {
        ProductionAppVariant testedVariant = createLibraryTasks(debugBuildTypeData)
        createLibraryTasks(releaseBuildTypeData)
        createTestTasks(testedVariant)
    }

    ProductionAppVariant createLibraryTasks(BuildTypeData buildTypeData) {
        ProductFlavorData defaultConfigData = getDefaultConfigData();

        def variantConfig = new VariantConfiguration(
                defaultConfigData.productFlavor, defaultConfigData.sourceSet,
                buildTypeData.buildType, buildTypeData.sourceSet,
                VariantConfiguration.Type.LIBRARY)

        ProductionAppVariant variant = new ProductionAppVariant(variantConfig)

        def prepareDependenciesTask = createPrepareDependenciesTask(variant)

        // Add a task to process the manifest(s)
        ProcessManifestTask processManifestTask = createProcessManifestTask(variant, DIR_BUNDLES)
        // TODO - move this
        processManifestTask.dependsOn prepareDependenciesTask

        // Add a task to create the BuildConfig class
        def generateBuildConfigTask = createBuildConfigTask(variant, null)

        // Add a task to generate resource source files
        def processResources = createProcessResTask(variant, processManifestTask,
                null /*crunchTask*/)

        // process java resources
        createProcessJavaResTask(variant)

        def compileAidl = createAidlTask(variant)
        // TODO - move this
        compileAidl.dependsOn prepareDependenciesTask

        // Add a compile task
        createCompileTask(variant, null/*testedVariant*/, processResources, generateBuildConfigTask,
                compileAidl)

        // jar the classes.
        Jar jar = project.tasks.add("package${buildTypeData.buildType.name.capitalize()}Jar", Jar);
        jar.dependsOn variant.compileTask, variant.processJavaResources
        jar.from(variant.compileTask.outputs);
        jar.from(variant.processJavaResources.destinationDir)

        jar.destinationDir = project.file("$project.buildDir/$DIR_BUNDLES/${variant.dirName}")
        jar.archiveName = "classes.jar"
        String packageName = variantConfig.getPackageFromManifest().replace('.', '/');
        jar.exclude(packageName + "/R.class")
        jar.exclude(packageName + "/R\$*.class")

        // package the android resources into the bundle folder
        Copy packageRes = project.tasks.add("package${variant.name}Res", Copy)
        // packageRes from 3 sources. the order is important to make sure the override works well.
        // TODO: fix the case of values -- need to merge the XML!
        packageRes.from(defaultConfigData.sourceSet.resources.directory,
                buildTypeData.sourceSet.resources.directory)
        packageRes.into(project.file("$project.buildDir/$DIR_BUNDLES/${variant.dirName}/res"))

        // package the aidl files into the bundle folder
        Copy packageAidl = project.tasks.add("package${variant.name}Aidl", Copy)
        // packageAidl from 3 sources. the order is important to make sure the override works well.
        packageAidl.from(defaultConfigData.sourceSet.aidl.directory,
                buildTypeData.sourceSet.aidl.directory)
        packageAidl.into(project.file("$project.buildDir/$DIR_BUNDLES/${variant.dirName}/aidl"))

        // package the R symbol test file into the bundle folder
        Copy packageSymbol = project.tasks.add("package${variant.name}Symbols", Copy)
        packageSymbol.dependsOn processResources
        packageSymbol.from(processResources.textSymbolDir)
        packageSymbol.into(project.file("$project.buildDir/$DIR_BUNDLES/${variant.dirName}"))

        Zip bundle = project.tasks.add("bundle${variant.name}", Zip)
        bundle.dependsOn jar, packageRes, packageAidl, packageSymbol
        bundle.setDescription("Assembles a bundle containing the library in ${variant.name}.");
        bundle.destinationDir = project.file("$project.buildDir/libs")
        bundle.extension = "alb"
        if (variant.baseName != BuildType.RELEASE) {
            bundle.classifier = variant.baseName
        }
        bundle.from(project.file("$project.buildDir/$DIR_BUNDLES/${variant.dirName}"))

        project.artifacts.add(buildTypeData.buildType.name, bundle)

        buildTypeData.assembleTask.dependsOn bundle
        variant.assembleTask = bundle

        // configure the variant to be testable.
        variantConfig.output = new BundleDependency(
                project.file("$project.buildDir/$DIR_BUNDLES/${variant.dirName}")) {

            @Override
            List<AndroidDependency> getDependencies() {
                return variantConfig.directLibraries
            }
        };

        return variant
    }

    void createTestTasks(ProductionAppVariant testedVariant) {
        ProductFlavorData defaultConfigData = getDefaultConfigData();

        def testVariantConfig = new VariantConfiguration(
                defaultConfigData.productFlavor, defaultConfigData.testSourceSet,
                debugBuildTypeData.buildType, null,
                VariantConfiguration.Type.TEST, testedVariant.config)
        // TODO: add actual dependencies
        testVariantConfig.setAndroidDependencies(null)

        def testVariant = new TestAppVariant(testVariantConfig,)
        createTestTasks(testVariant, testedVariant)
    }

    @Override
    String getTarget() {
        return extension.target
    }

    protected String getManifestOutDir() {
        return DIR_BUNDLES
    }
}

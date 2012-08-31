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
import com.android.builder.VariantConfiguration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip

class AndroidLibraryPlugin extends AndroidBasePlugin implements Plugin<Project> {

    private final static String DIR_BUNDLES = "bundles";

    AndroidLibraryExtension extension
    BuildTypeData debugBuildTypeData
    BuildTypeData releaseBuildTypeData

    @Override
    void apply(Project project) {
        super.apply(project)

        extension = project.extensions.create('android', AndroidLibraryExtension)
        setDefaultConfig(extension.defaultConfig)

        // create the source sets for the build type.
        // the ones for the main product flavors are handled by the base plugin.
        def debugSourceSet = project.sourceSets.add(BuildType.DEBUG)
        def releaseSourceSet = project.sourceSets.add(BuildType.RELEASE)

        debugBuildTypeData = new BuildTypeData(extension.debug, debugSourceSet, project)
        releaseBuildTypeData = new BuildTypeData(extension.release, releaseSourceSet, project)
        project.tasks.assemble.dependsOn debugBuildTypeData.assembleTask
        project.tasks.assemble.dependsOn releaseBuildTypeData.assembleTask

        project.afterEvaluate {
            createAndroidTasks()
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
                defaultConfigData.productFlavor, defaultConfigData.androidSourceSet,
                buildTypeData.buildType, buildTypeData.androidSourceSet,
                VariantConfiguration.Type.LIBRARY)
        // TODO: add actual dependencies
        variantConfig.setAndroidDependencies(null)

        ProductionAppVariant variant = new ProductionAppVariant(variantConfig)

        // Add a task to process the manifest(s)
        ProcessManifestTask processManifestTask = createProcessManifestTask(variant, DIR_BUNDLES)

        // Add a task to create the BuildConfig class
        def generateBuildConfigTask = createBuildConfigTask(variant, null)

        // Add a task to generate resource source files
        def processResources = createProcessResTask(variant, processManifestTask,
                null /*crunchTask*/)

        // Add a compile task
        createCompileTask(variant, null/*testedVariant*/, processResources, generateBuildConfigTask)

        // jar the classes.
        Jar jar = project.tasks.add("${buildTypeData.buildType.name}Jar", Jar);
        jar.from(variant.compileTask.outputs);
        jar.destinationDir = project.file("$project.buildDir/$DIR_BUNDLES/${variant.dirName}")
        jar.baseName = "classes"
        String packageName = variantConfig.getPackageFromManifest().replace('.', '/');
        jar.exclude(packageName + "/R.class")
        jar.exclude(packageName + "/R\$*.class")
        jar.exclude(packageName + "/Manifest.class")
        jar.exclude(packageName + "/Manifest\$*.class")

        // merge the resources into the bundle folder
        Copy mergeRes = project.tasks.add("merge${variant.name}Res",
                Copy)
        // mergeRes from 3 sources. the order is important to make sure the override works well.
        // TODO: fix the case of values -- need to merge the XML!
        mergeRes.from(defaultConfigData.androidSourceSet.androidResources,
                buildTypeData.androidSourceSet.androidResources)
        mergeRes.into(project.file("$project.buildDir/$DIR_BUNDLES/${variant.dirName}/res"))

        Zip bundle = project.tasks.add("bundle${variant.name}", Zip)
        bundle.dependsOn jar, mergeRes
        bundle.setDescription("Assembles a bundle containing the library in ${variant.name}.");
        bundle.destinationDir = project.file("$project.buildDir/libs")
        bundle.extension = "alb"
        bundle.baseName = "${project.archivesBaseName}-${variant.baseName}"
        bundle.from(project.file("$project.buildDir/$DIR_BUNDLES/${variant.dirName}"))

        buildTypeData.assembleTask.dependsOn bundle
        variant.assembleTask = bundle

        // configure the variant to be testable.
        variantConfig.output = new AndroidDependency() {

            @Override
            List<AndroidDependency> getDependencies() {
                return null;
            }

            @Override
            File getJarFile() {
                return jar.archivePath
            }

            @Override
            File getManifest() {
                return processManifestTask.processedManifest
            }

            @Override
            File getResFolder() {
                return mergeRes.destinationDir
            }

            @Override
            File getAssetsFolder() {
                return null
            }

            @Override
            File getJniFolder() {
                return null
            }

            @Override
            File getProguardRules() {
                return null
            }

            @Override
            File getLintJar() {
                return null
            }
        };

        return variant
    }

    void createTestTasks(ProductionAppVariant testedVariant) {
        ProductFlavorData defaultConfigData = getDefaultConfigData();

        def testVariantConfig = new VariantConfiguration(
                defaultConfigData.productFlavor, defaultConfigData.androidTestSourceSet,
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

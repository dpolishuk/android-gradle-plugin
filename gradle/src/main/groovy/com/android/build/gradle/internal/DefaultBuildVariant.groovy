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
package com.android.build.gradle.internal

import com.android.build.gradle.BuildVariant
import com.android.build.gradle.tasks.AidlCompile
import com.android.build.gradle.tasks.Dex
import com.android.build.gradle.tasks.GenerateBuildConfig
import com.android.build.gradle.tasks.PackageApplication
import com.android.build.gradle.tasks.ProcessImages
import com.android.build.gradle.tasks.ProcessManifest
import com.android.build.gradle.tasks.ProcessResources
import com.android.build.gradle.tasks.ZipAlign
import com.android.builder.BuildType
import com.android.builder.ProductFlavor
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile

/**
 * implementation of the {@link BuildVariant} interface around an {@link ApplicationVariant}
 * object.
 */
public class DefaultBuildVariant implements BuildVariant {

    private final ApplicationVariant variant
    private final BuildVariant testVariant

    public DefaultBuildVariant(ApplicationVariant variant, BuildVariant testVariant) {
        this.variant = variant
        this.testVariant = testVariant
    }

    public DefaultBuildVariant(ApplicationVariant variant) {
        this(variant, null)
    }

    @Override
    String getName() {
        return variant.name
    }

    String getDescription() {
        return variant.description
    }

    String getDirName() {
        return variant.dirName
    }

    String getBaseName() {
        return variant.baseName
    }

    @Override
    BuildType getBuildType() {
        return variant.config.buildType
    }

    @Override
    List<ProductFlavor> getProductFlavors() {
        return variant.config.flavorConfigs
    }

    @Override
    ProductFlavor getMergedConfig() {
        return variant.config.mergedFlavor
    }

    @Override
    File getOutputFile() {
        return variant.outputFile
    }

    @Override
    BuildVariant getTestVariant() {
        return testVariant
    }

    @Override
    ProcessManifest getProcessManifest() {
        return variant.processManifestTask
    }

    @Override
    AidlCompile getAidlCompile() {
        return variant.aidlCompileTask
    }

    @Override
    ProcessImages getProcessImages() {
        return variant.processImagesTask
    }

    @Override
    ProcessResources getProcessResources() {
        return variant.processResourcesTask
    }

    @Override
    GenerateBuildConfig getGenerateBuildConfig() {
        return variant.generateBuildConfigTask
    }

    @Override
    JavaCompile getJavaCompile() {
        return variant.javaCompileTask
    }

    @Override
    Copy getProcessJavaResources() {
        return variant.processJavaResources
    }

    @Override
    Dex getDex() {
        return variant.dexTask
    }

    @Override
    PackageApplication getPackageApplication() {
        return variant.packageApplicationTask
    }

    ZipAlign getZipAlign() {
        return variant.zipAlignTask
    }

    @Override
    Task getAssemble() {
        return variant.assembleTask
    }

    @Override
    Task getInstall() {
        return variant.installTask
    }

    @Override
    Task getUninstall() {
        return variant.uninstallTask
    }

    @Override
    Task getRunTests() {
        return variant.runTestsTask
    }
}

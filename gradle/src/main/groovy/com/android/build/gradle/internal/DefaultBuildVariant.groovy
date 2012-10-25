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
import com.android.build.gradle.internal.tasks.BaseManifestTask
import com.android.build.gradle.tasks.CompileAidlTask
import com.android.build.gradle.tasks.CrunchResourcesTask
import com.android.build.gradle.tasks.DexTask
import com.android.build.gradle.tasks.GenerateBuildConfigTask
import com.android.build.gradle.tasks.PackageApplicationTask
import com.android.build.gradle.tasks.ProcessResourcesTask
import com.android.build.gradle.tasks.RunTestsTask
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
    BuildVariant getTestVariant() {
        return testVariant
    }

    @Override
    BaseManifestTask getProcessManifestTask() {
        return variant.processManifestTask
    }

    @Override
    CompileAidlTask getCompileAidlTask() {
        return variant.compileAidlTask
    }

    @Override
    CrunchResourcesTask getCrunchResourcesTask() {
        return variant.crunchResourcesTask
    }

    @Override
    ProcessResourcesTask getProcessResourcesTask() {
        return variant.processResourcesTask
    }

    @Override
    GenerateBuildConfigTask getGenerateBuildConfigTask() {
        return variant.generateBuildConfigTask
    }

    @Override
    JavaCompile getCompileTask() {
        return variant.compileTask
    }

    @Override
    Copy getProcessJavaResources() {
        return variant.processJavaResources
    }

    @Override
    DexTask getDexTask() {
        return variant.dexTask
    }

    @Override
    PackageApplicationTask getPackageApplicationTask() {
        return variant.packageApplicationTask
    }

    @Override
    Task getAssembleTask() {
        return variant.assembleTask
    }

    @Override
    Task getInstallTask() {
        return variant.installTask
    }

    @Override
    Task getUninstallTask() {
        return variant.uninstallTask
    }

    @Override
    RunTestsTask getRunTestsTask() {
        return variant.runTestsTask
    }
}

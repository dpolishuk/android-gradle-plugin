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

import com.android.build.gradle.BasePlugin
import com.android.build.gradle.internal.tasks.AidlCompileTask
import com.android.build.gradle.internal.tasks.DexTask
import com.android.build.gradle.internal.tasks.GenerateBuildConfigTask
import com.android.build.gradle.internal.tasks.PackageApplicationTask
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask
import com.android.build.gradle.internal.tasks.ProcessImagesTask
import com.android.build.gradle.internal.tasks.ProcessResourcesTask
import com.android.build.gradle.internal.tasks.RunTestsTask
import com.android.build.gradle.internal.tasks.ZipAlignTask
import com.android.build.gradle.tasks.ProcessManifest
import com.android.builder.AndroidBuilder
import com.android.builder.ProductFlavor
import com.android.builder.VariantConfiguration
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile

/**
 * Represents something that can be packaged into an APK and installed.
 */
public abstract class ApplicationVariant {

    final VariantConfiguration config

    PrepareDependenciesTask prepareDependenciesTask

    ProcessManifest processManifestTask
    AidlCompileTask aidlCompileTask
    ProcessImagesTask processImagesTask
    ProcessResourcesTask processResourcesTask
    GenerateBuildConfigTask generateBuildConfigTask

    JavaCompile javaCompileTask
    Copy processJavaResources

    DexTask dexTask
    PackageApplicationTask packageApplicationTask
    ZipAlignTask zipAlignTask

    File outputFile

    Task assembleTask

    Task installTask
    Task uninstallTask

    RunTestsTask runTestsTask

    ApplicationVariant(VariantConfiguration config) {
        this.config = config
    }

    public VariantConfiguration getVariantConfiguration() {
        return config
    }

    abstract String getName()

    abstract String getDescription()

    abstract String getDirName()

    abstract String getBaseName()

    abstract boolean getZipAlign()

    boolean isSigned() {
        return config.buildType.debugSigned ||
                config.mergedFlavor.isSigningReady()
    }

    abstract boolean getRunProguard()

    abstract List<String> getRunCommand()

    abstract String getPackage()

    abstract AndroidBuilder createBuilder(BasePlugin androidBasePlugin)

    protected String getFlavoredName(boolean capitalized) {
        StringBuilder builder = new StringBuilder()
        for (ProductFlavor flavor : config.flavorConfigs) {
            builder.append(capitalized ? flavor.name.capitalize() : flavor.name)
        }

        return builder.toString()
    }
}

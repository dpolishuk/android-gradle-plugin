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

import com.android.annotations.NonNull
import com.android.annotations.Nullable
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
 * A Build variant and all it's public data.
 */
public interface BuildVariant {

    /**
     * Returns the name of the variant. Guaranteed to be unique.
     */
    @NonNull
    String getName()

    /**
     * Returns a description for the build variant.
     */
    @NonNull
    String getDescription()

    /**
     * Returns a subfolder name for the variant. Guaranteed to be unique.
     *
     * This is usually a mix of build type and flavor(s) (if applicable).
     * For instance this could be:
     * "debug"
     * "debug/myflavor"
     * "release/Flavor1Flavor2"
     */
    @NonNull
    String getDirName()

    /**
     * Returns the base name for the output of the variant. Guaranteed to be unique.
     */
    @NonNull
    String getBaseName()

    /**
     * Returns the {@link BuildType} for this build variant.
     */
    @NonNull
    BuildType getBuildType()

    /**
     * Returns the list of {@link ProductFlavor} for this build variant.
     *
     * This is always non-null but could be empty.
     */
    @NonNull
    List<ProductFlavor> getProductFlavors()

    /**
     * Returns a {@link ProductFlavor} that represents the merging of the default config
     * and the flavors of this build variant.
     */
    @NonNull
    ProductFlavor getMergedConfig()

    /**
     * Returns the build variant that will test this build variant.
     *
     * Will return null if this build variant is a test build already.
     */
    @Nullable
    BuildVariant getTestVariant()

    /**
     * Returns the Manifest processing task.
     */
    @NonNull
    BaseManifestTask getProcessManifestTask()

    /**
     * Returns the AIDL compilation task.
     */
    @NonNull
    CompileAidlTask getCompileAidlTask()

    /**
     * Returns the Resource crunching task.
     */
    @Nullable
    CrunchResourcesTask getCrunchResourcesTask()

    /**
     * Returns the Android Resources processing task.
     */
    @NonNull
    ProcessResourcesTask getProcessResourcesTask()

    /**
     * Returns the BuildConfig generation task.
     */
    @Nullable
    GenerateBuildConfigTask getGenerateBuildConfigTask()

    /**
     * Returns the Java Compilation task.
     */
    @NonNull
    JavaCompile getCompileTask()

    /**
     * Returns the Java resource processing task.
     */
    @NonNull
    Copy getProcessJavaResources()

    /**
     * Returns the Dex task.
     */
    @Nullable
    DexTask getDexTask()

    /**
     * Returns the APK packaging task.
     */
    @Nullable
    PackageApplicationTask getPackageApplicationTask()

    /**
     * Returns the assemble task.
     */
    @Nullable
    Task getAssembleTask()

    /**
     * Returns the installation task.
     *
     * Even for variant for regular project, this can be null if the app cannot be signed.
     */
    @Nullable
    Task getInstallTask()

    /**
     * Returns the uinstallation task.
     *
     * For non-library project this is always true even if the APK is not created because
     * signing isn't setup.
     */
    @Nullable
    Task getUninstallTask()

    /**
     * Returns the task to run the tests.
     * Only valid for test project.
     */
    @Nullable
    RunTestsTask getRunTestsTask()
}

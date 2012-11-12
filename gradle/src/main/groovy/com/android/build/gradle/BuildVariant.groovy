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
import org.gradle.api.tasks.bundling.Zip
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
     * Returns the output file for this build variants. Depending on the configuration, this could
     * be an apk (regular and test project) or a bundled library (library project).
     *
     * If it's an apk, it could be signed, or not; zip-aligned, or not.
     */
    @NonNull
    File getOutputFile()

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
    ProcessManifest getProcessManifest()

    /**
     * Returns the AIDL compilation task.
     */
    @NonNull
    AidlCompile getAidlCompile()

    /**
     * Returns the image processing task.
     */
    @Nullable
    ProcessImages getProcessImages()

    /**
     * Returns the Android Resources processing task.
     */
    @NonNull
    ProcessResources getProcessResources()

    /**
     * Returns the BuildConfig generation task.
     */
    @Nullable
    GenerateBuildConfig getGenerateBuildConfig()

    /**
     * Returns the Java Compilation task.
     */
    @NonNull
    JavaCompile getJavaCompile()

    /**
     * Returns the Java resource processing task.
     */
    @NonNull
    Copy getProcessJavaResources()

    /**
     * Returns the Dex task.
     */
    @Nullable
    Dex getDex()

    /**
     * Returns the APK packaging task.
     */
    @Nullable
    PackageApplication getPackageApplication()

    /**
     * Retursn the Zip align task.
     */
    @Nullable
    ZipAlign getZipAlign()

    /**
     * Returns the Library AAR packaging task.
     */
    @Nullable
    Zip getPackageLibrary()

    /**
     * Returns the assemble task.
     */
    @Nullable
    Task getAssemble()

    /**
     * Returns the installation task.
     *
     * Even for variant for regular project, this can be null if the app cannot be signed.
     */
    @Nullable
    Task getInstall()

    /**
     * Returns the uinstallation task.
     *
     * For non-library project this is always true even if the APK is not created because
     * signing isn't setup.
     */
    @Nullable
    Task getUninstall()

    /**
     * Returns the task to run the tests.
     * Only valid for test project.
     */
    @Nullable
    Task getRunTests()
}

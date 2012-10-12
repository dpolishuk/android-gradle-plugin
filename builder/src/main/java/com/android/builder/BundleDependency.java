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

package com.android.builder;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.io.File;

/**
 * Default implementation of the AndroidDependency interface that handles a default bundle project
 * structure.
 */
public abstract class BundleDependency implements AndroidDependency {

    private final String mName;
    private final File mBundleFolder;

    /**
     * Creates the bundle dependency with an optional name
     * @param bundleFolder the folder containing the library
     * @param name an optional name
     */
    protected BundleDependency(@NonNull File bundleFolder, @Nullable String name) {
        mName = name;
        mBundleFolder = bundleFolder;
    }

    protected BundleDependency(@NonNull File bundleFolder) {
        this(bundleFolder, null);
    }

    public String getName() {
        return mName;
    }

    @Override
    public String toString() {
        return mName;
    }

    @Override
    public File getManifest() {
        return new File(mBundleFolder, SdkConstants.FN_ANDROID_MANIFEST_XML);
    }

    @Override
    public File getTextSymbol() {
        return new File(mBundleFolder, "R.txt");
    }

    @Override
    public File getFolder() {
        return mBundleFolder;
    }

    @Override
    public File getJarFile() {
        return new File(mBundleFolder, SdkConstants.FN_CLASSES_JAR);
    }

    @Override
    public File getResFolder() {
        return new File(mBundleFolder, SdkConstants.FD_RES);
    }

    @Override
    public File getAssetsFolder() {
        return new File(mBundleFolder, SdkConstants.FD_ASSETS);
    }

    @Override
    public File getJniFolder() {
        return new File(mBundleFolder, "jni");
    }

    @Override
    public File getAidlFolder() {
        return new File(mBundleFolder, SdkConstants.FD_AIDL);
    }

    @Override
    public File getProguardRules() {
        return new File(mBundleFolder, "proguard.txt");
    }

    @Override
    public File getLintJar() {
        return new File(mBundleFolder, "lint.jar");
    }

    public File getBundleFolder() {
        return mBundleFolder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BundleDependency that = (BundleDependency) o;

        if (mName != null ? !mName.equals(that.mName) : that.mName != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return mName != null ? mName.hashCode() : 0;
    }
}

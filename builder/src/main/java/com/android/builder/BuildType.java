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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Objects;

public class BuildType extends BuildConfig {
    private static final long serialVersionUID = 1L;

    public final static String DEBUG = "debug";
    public final static String RELEASE = "release";

    private final String mName;
    private boolean mDebuggable;
    private boolean mDebugJniBuild;
    private boolean mDebugSigned;
    private String mPackageNameSuffix = null;
    private boolean mRunProguard = false;

    private boolean mZipAlign = true;

    public BuildType(@NonNull String name) {
        this.mName = name;
        if (DEBUG.equals(name)) {
            initDebug();
        } else if (RELEASE.equals(name)) {
            initRelease();
        }
    }

    private void initDebug() {
        mDebuggable = true;
        mDebugJniBuild = true;
        mDebugSigned = true;
        mZipAlign = false;
    }

    private void initRelease() {
        mDebuggable = false;
        mDebugJniBuild = false;
        mDebugSigned = false;
    }

    public String getName() {
        return mName;
    }

    public BuildType setDebuggable(boolean debuggable) {
        mDebuggable = debuggable;
        return this;
    }

    public boolean isDebuggable() {
        return mDebuggable;
    }

    public BuildType setDebugJniBuild(boolean debugJniBuild) {
        mDebugJniBuild = debugJniBuild;
        return this;
    }

    public boolean isDebugJniBuild() {
        return mDebugJniBuild;
    }

    public BuildType setDebugSigned(boolean debugSigned) {
        mDebugSigned = debugSigned;
        return this;
    }

    public boolean isDebugSigned() {
        return mDebugSigned;
    }

    public BuildType setPackageNameSuffix(@Nullable String packageNameSuffix) {
        mPackageNameSuffix = packageNameSuffix;
        return this;
    }

    @Nullable public String getPackageNameSuffix() {
        return mPackageNameSuffix;
    }

    public BuildType setRunProguard(boolean runProguard) {
        mRunProguard = runProguard;
        return this;
    }

    public boolean isRunProguard() {
        return mRunProguard;
    }

    public BuildType setZipAlign(boolean zipAlign) {
        mZipAlign = zipAlign;
        return this;
    }

    public boolean isZipAlign() {
        return mZipAlign;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        BuildType buildType = (BuildType) o;

        if (mName != null ? !mName.equals(buildType.mName) : buildType.mName != null) return false;
        if (mDebugJniBuild != buildType.mDebugJniBuild) return false;
        if (mDebugSigned != buildType.mDebugSigned) return false;
        if (mDebuggable != buildType.mDebuggable) return false;
        if (mRunProguard != buildType.mRunProguard) return false;
        if (mZipAlign != buildType.mZipAlign) return false;
        if (mPackageNameSuffix != null ?
                !mPackageNameSuffix.equals(buildType.mPackageNameSuffix) :
                buildType.mPackageNameSuffix != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (mName != null ? mName.hashCode() : 0);
        result = 31 * result + (mDebuggable ? 1 : 0);
        result = 31 * result + (mDebugJniBuild ? 1 : 0);
        result = 31 * result + (mDebugSigned ? 1 : 0);
        result = 31 * result + (mPackageNameSuffix != null ? mPackageNameSuffix.hashCode() : 0);
        result = 31 * result + (mRunProguard ? 1 : 0);
        result = 31 * result + (mZipAlign ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", mName)
                .add("debuggable", mDebuggable)
                .add("debugJniBuild", mDebugJniBuild)
                .add("debugSigned", mDebugSigned)
                .add("packageNameSuffix", mPackageNameSuffix)
                .add("runProguard", mRunProguard)
                .add("zipAlign", mZipAlign)
                .omitNullValues()
                .toString();
    }
}

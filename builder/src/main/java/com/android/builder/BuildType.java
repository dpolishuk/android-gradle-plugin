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

public class BuildType {

    public final static String DEBUG = "debug";
    public final static String RELEASE = "release";

    private final String mName;
    private boolean mDebuggable;
    private boolean mDebugJniBuild;
    private boolean mDebugSigningKey;
    private String mPackageNameSuffix = null;

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
        mDebugSigningKey = true;
        mZipAlign = false;
    }

    private void initRelease() {
        mDebuggable = false;
        mDebugJniBuild = false;
        mDebugSigningKey = false;
    }

    public String getName() {
        return mName;
    }

    public void setDebuggable(boolean debuggable) {
        mDebuggable = debuggable;
    }

    public boolean isDebuggable() {
        return mDebuggable;
    }

    public void setDebugJniBuild(boolean debugJniBuild) {
        mDebugJniBuild = debugJniBuild;
    }

    public boolean isDebugJniBuild() {
        return mDebugJniBuild;
    }

    public void setDebugSigningKey(boolean debugSigningKey) {
        mDebugSigningKey = debugSigningKey;
    }

    public boolean isDebugSigningKey() {
        return mDebugSigningKey;
    }

    public void setPackageNameSuffix(@Nullable String packageNameSuffix) {
        mPackageNameSuffix = packageNameSuffix;
    }

    @Nullable public String getPackageNameSuffix() {
        return mPackageNameSuffix;
    }

    public void setZipAlign(boolean zipAlign) {
        mZipAlign = zipAlign;
    }

    public boolean isZipAlign() {
        return mZipAlign;
    }

    /*
proguard enabled + rules
Buildconfig: DEBUG flag + other custom properties?
     */
}

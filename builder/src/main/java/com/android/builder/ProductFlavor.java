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

public class ProductFlavor {

    private final String mName;
    private int mMinSdkVersion = -1;
    private int mTargetSdkVersion = -1;
    private int mVersionCode = -1;
    private String mVersionName = null;
    private String mPackageName = null;
    private String mTestPackageName = null;
    private String mTestInstrumentationRunner = null;

    public ProductFlavor(String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public void setPackageName(String packageName) {
        mPackageName = packageName;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public void setVersionCode(int versionCode) {
        mVersionCode = versionCode;
    }

    public int getVersionCode() {
        return mVersionCode;
    }

    public void setVersionName(String versionName) {
        mVersionName = versionName;
    }

    public String getVersionName() {
        return mVersionName;
    }

    public void setMinSdkVersion(int minSdkVersion) {
        mMinSdkVersion  = minSdkVersion;
    }

    public int getMinSdkVersion() {
        return mMinSdkVersion;
    }

    public void setTargetSdkVersion(int targetSdkVersion) {
        mTargetSdkVersion  = targetSdkVersion;
    }

    public int getTargetSdkVersion() {
        return mTargetSdkVersion;
    }

    public void setTestPackageName(String testPackageName) {
        mTestPackageName = testPackageName;
    }

    public String getTestPackageName() {
        return mTestPackageName;
    }

    public void setTestInstrumentationRunner(String testInstrumentationRunner) {
        mTestInstrumentationRunner = testInstrumentationRunner;
    }

    public String getTestInstrumentationRunner() {
        return mTestInstrumentationRunner;
    }

    /**
     * Merges the flavor on top of a base platform and returns a new object with the result.
     * @param base
     * @return
     */
    ProductFlavor mergeWith(@NonNull ProductFlavor base) {
        ProductFlavor flavor = new ProductFlavor("");

        flavor.mMinSdkVersion = chooseInt(mMinSdkVersion, base.mMinSdkVersion);
        flavor.mTargetSdkVersion = chooseInt(mTargetSdkVersion, base.mTargetSdkVersion);

        flavor.mVersionCode = chooseInt(mVersionCode, base.mVersionCode);
        flavor.mVersionName = chooseString(mVersionName, base.mVersionName);

        flavor.mPackageName = chooseString(mPackageName, base.mPackageName);

        flavor.mTestPackageName = chooseString(mTestPackageName, base.mTestPackageName);
        flavor.mTestInstrumentationRunner = chooseString(mTestInstrumentationRunner,
                base.mTestInstrumentationRunner);

        return flavor;
    }

    private int chooseInt(int overlay, int base) {
        return overlay != -1 ? overlay : base;
    }

    private String chooseString(String overlay, String base) {
        return overlay != null ? overlay : base;
    }

    /*
    release signing info (keystore, key alias, passwords,...).
    native abi filter
*/

}

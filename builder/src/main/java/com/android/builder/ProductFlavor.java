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
import com.google.common.base.Objects;

/**
 * The configuration of a product flavor.
 *
 * This is also used to describe the default configuration of all builds, even those that
 * do not contain any flavors.
 */
public class ProductFlavor extends BuildConfig {
    private static final long serialVersionUID = 1L;

    /**
     * The name of the default config.
     */
    public static final String MAIN = "main";

    private final String mName;
    private int mMinSdkVersion = -1;
    private int mTargetSdkVersion = -1;
    private int mVersionCode = -1;
    private String mVersionName = null;
    private String mPackageName = null;
    private String mTestPackageName = null;
    private String mTestInstrumentationRunner = null;

    private String mSigningStoreLocation = null;
    private String mSigningStorePassword = null;
    private String mSigningKeyAlias = null;
    private String mSigningKeyPassword = null;

    /**
     * Creates a ProductFlavor with a given name.
     *
     * Names can be important when dealing with flavor groups.
     * @param name the name of the flavor.
     *
     * @see #MAIN
     */
    public ProductFlavor(String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    /**
     * Sets the package name.
     *
     * @param packageName the package name
     * @return the flavor object
     */
    public ProductFlavor setPackageName(String packageName) {
        mPackageName = packageName;
        return this;
    }

    public String getPackageName() {
        return mPackageName;
    }

    /**
     * Sets the version code. If the value is -1, it is considered not set.
     *
     * @param versionCode the version code
     * @return the flavor object
     */
    public ProductFlavor setVersionCode(int versionCode) {
        mVersionCode = versionCode;
        return this;
    }

    public int getVersionCode() {
        return mVersionCode;
    }

    /**
     * Sets the version name.
     *
     * @param versionName the version name
     * @return the flavor object
     */
    public ProductFlavor setVersionName(String versionName) {
        mVersionName = versionName;
        return this;
    }

    public String getVersionName() {
        return mVersionName;
    }

    public ProductFlavor setMinSdkVersion(int minSdkVersion) {
        mMinSdkVersion = minSdkVersion;
        return this;
    }

    public int getMinSdkVersion() {
        return mMinSdkVersion;
    }

    public ProductFlavor setTargetSdkVersion(int targetSdkVersion) {
        mTargetSdkVersion = targetSdkVersion;
        return this;
    }

    public int getTargetSdkVersion() {
        return mTargetSdkVersion;
    }

    public ProductFlavor setTestPackageName(String testPackageName) {
        mTestPackageName = testPackageName;
        return this;
    }

    public String getTestPackageName() {
        return mTestPackageName;
    }

    public ProductFlavor setTestInstrumentationRunner(String testInstrumentationRunner) {
        mTestInstrumentationRunner = testInstrumentationRunner;
        return this;
    }

    public String getTestInstrumentationRunner() {
        return mTestInstrumentationRunner;
    }

    public String getSigningStoreLocation() {
        return mSigningStoreLocation;
    }

    public ProductFlavor setSigningStoreLocation(String signingStoreLocation) {
        mSigningStoreLocation = signingStoreLocation;
        return this;
    }

    public String getSigningStorePassword() {
        return mSigningStorePassword;
    }

    public ProductFlavor setSigningStorePassword(String signingStorePassword) {
        mSigningStorePassword = signingStorePassword;
        return this;
    }

    public String getSigningKeyAlias() {
        return mSigningKeyAlias;
    }

    public ProductFlavor setSigningKeyAlias(String signingKeyAlias) {
        mSigningKeyAlias = signingKeyAlias;
        return this;
    }

    public String getSigningKeyPassword() {
        return mSigningKeyPassword;
    }

    public ProductFlavor setSigningKeyPassword(String signingKeyPassword) {
        mSigningKeyPassword = signingKeyPassword;
        return this;
    }

    public boolean isSigningReady() {
        return mSigningStoreLocation != null &&
                mSigningStorePassword != null &&
                mSigningKeyAlias != null &&
                mSigningKeyPassword != null;
    }

    /**
     * Merges the flavor on top of a base platform and returns a new object with the result.
     * @param base the flavor to merge on top of
     * @return a new merged product flavor
     */
    ProductFlavor mergeOver(@NonNull ProductFlavor base) {
        ProductFlavor flavor = new ProductFlavor("");

        flavor.mMinSdkVersion = chooseInt(mMinSdkVersion, base.mMinSdkVersion);
        flavor.mTargetSdkVersion = chooseInt(mTargetSdkVersion, base.mTargetSdkVersion);

        flavor.mVersionCode = chooseInt(mVersionCode, base.mVersionCode);
        flavor.mVersionName = chooseString(mVersionName, base.mVersionName);

        flavor.mPackageName = chooseString(mPackageName, base.mPackageName);

        flavor.mTestPackageName = chooseString(mTestPackageName, base.mTestPackageName);
        flavor.mTestInstrumentationRunner = chooseString(mTestInstrumentationRunner,
                base.mTestInstrumentationRunner);

        flavor.mSigningStoreLocation = chooseString(mSigningStoreLocation,
                base.mSigningStoreLocation);
        flavor.mSigningStorePassword = chooseString(mSigningStorePassword,
                base.mSigningStorePassword);
        flavor.mSigningKeyAlias = chooseString(mSigningKeyAlias, base.mSigningKeyAlias);
        flavor.mSigningKeyPassword = chooseString(mSigningKeyPassword, base.mSigningKeyPassword);

        return flavor;
    }

    private int chooseInt(int overlay, int base) {
        return overlay != -1 ? overlay : base;
    }

    private String chooseString(String overlay, String base) {
        return overlay != null ? overlay : base;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ProductFlavor that = (ProductFlavor) o;

        if (mName != null ? !mName.equals(that.mName) : that.mName != null) return false;
        if (mMinSdkVersion != that.mMinSdkVersion) return false;
        if (mTargetSdkVersion != that.mTargetSdkVersion) return false;
        if (mVersionCode != that.mVersionCode) return false;
        if (mPackageName != null ?
                !mPackageName.equals(that.mPackageName) :
                that.mPackageName != null)
            return false;
        if (mSigningKeyAlias != null ?
                !mSigningKeyAlias.equals(that.mSigningKeyAlias) :
                that.mSigningKeyAlias != null)
            return false;
        if (mSigningKeyPassword != null ?
                !mSigningKeyPassword.equals(that.mSigningKeyPassword) :
                that.mSigningKeyPassword != null)
            return false;
        if (mSigningStoreLocation != null ?
                !mSigningStoreLocation.equals(that.mSigningStoreLocation) :
                that.mSigningStoreLocation != null)
            return false;
        if (mSigningStorePassword != null ?
                !mSigningStorePassword.equals(that.mSigningStorePassword) :
                that.mSigningStorePassword != null)
            return false;
        if (mTestInstrumentationRunner != null ?
                !mTestInstrumentationRunner.equals(that.mTestInstrumentationRunner) :
                that.mTestInstrumentationRunner != null)
            return false;
        if (mTestPackageName != null ?
                !mTestPackageName.equals(that.mTestPackageName) : that.mTestPackageName != null)
            return false;
        if (mVersionName != null ?
                !mVersionName.equals(that.mVersionName) : that.mVersionName != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (mName != null ? mName.hashCode() : 0);
        result = 31 * result + mMinSdkVersion;
        result = 31 * result + mTargetSdkVersion;
        result = 31 * result + mVersionCode;
        result = 31 * result + (mVersionName != null ? mVersionName.hashCode() : 0);
        result = 31 * result + (mPackageName != null ? mPackageName.hashCode() : 0);
        result = 31 * result + (mTestPackageName != null ? mTestPackageName.hashCode() : 0);
        result = 31 * result + (mTestInstrumentationRunner != null ?
                mTestInstrumentationRunner.hashCode() : 0);
        result = 31 * result + (mSigningStoreLocation != null ?
                mSigningStoreLocation.hashCode() : 0);
        result = 31 * result + (mSigningStorePassword != null ?
                mSigningStorePassword.hashCode() : 0);
        result = 31 * result + (mSigningKeyAlias != null ? mSigningKeyAlias.hashCode() : 0);
        result = 31 * result + (mSigningKeyPassword != null ? mSigningKeyPassword.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", mName)
                .add("minSdkVersion", mMinSdkVersion)
                .add("targetSdkVersion", mTargetSdkVersion)
                .add("versionCode", mVersionCode)
                .add("versionName", mVersionName)
                .add("packageName", mPackageName)
                .add("testPackageName", mTestPackageName)
                .add("testInstrumentationRunner", mTestInstrumentationRunner)
                .add("signingStoreLocation", mSigningStoreLocation)
                .add("signingStorePassword", mSigningStorePassword)
                .add("signingKeyAlias", mSigningKeyAlias)
                .add("signingKeyPassword", mSigningKeyPassword)
                .omitNullValues()
                .toString();
    }

    /*
        release signing info (keystore, key alias, passwords,...).
        native abi filter
    */

}

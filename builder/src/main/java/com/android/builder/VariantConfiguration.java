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
import com.android.annotations.VisibleForTesting;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A Variant configuration.
 */
public class VariantConfiguration {

    private final static ManifestParser sManifestParser = new DefaultManifestParser();

    private final ProductFlavor mDefaultConfig;
    private final SourceSet mDefaultSourceSet;

    private final BuildType mBuildType;
    private final SourceSet mBuildTypeSourceSet;

    private final List<ProductFlavor> mFlavorConfigs = new ArrayList<ProductFlavor>();
    private final List<SourceSet> mFlavorSourceSets = new ArrayList<SourceSet>();

    private final Type mType;
    private ProductFlavor mMergedFlavor;

    private List<JarDependency> mJars;

    /** List of direct library project dependencies. Each object defines its own dependencies. */
    private final List<AndroidDependency> mDirectLibraryProjects =
            new ArrayList<AndroidDependency>();
    /** list of all library project dependencies in the flat list.
     * The order is based on the order needed to call aapt: earlier libraries override resources
     * of latter ones. */
    private final List<AndroidDependency> mFlatLibraryProjects = new ArrayList<AndroidDependency>();


    public static enum Type {
        DEFAULT, LIBRARY, TEST;
    }

    /**
     * Creates the configuration with the base source set, and whether it is a library.
     * @param defaultConfig
     * @param defaultSourceSet
     * @param buildType
     * @param buildTypeSourceSet
     * @param type
     */
    public VariantConfiguration(
            @NonNull ProductFlavor defaultConfig, @NonNull SourceSet defaultSourceSet,
            @NonNull BuildType buildType, @NonNull SourceSet buildTypeSourceSet,
            @NonNull Type type) {
        mDefaultConfig = defaultConfig;
        mDefaultSourceSet = defaultSourceSet;
        mBuildType = buildType;
        mBuildTypeSourceSet = buildTypeSourceSet;
        mType = type;

        mMergedFlavor = mDefaultConfig;

        validate();
    }

    /**
     * Add a new configured ProductFlavor.
     *
     * If multiple flavors are added, the priority follows the order they are added when it
     * comes to resolving Android resources overlays (ie earlier added flavors supersedes
     * latter added ones).
     *
     * @param sourceSet the configured product flavor
     */
    public void addProductFlavor(@NonNull ProductFlavor productFlavor, @NonNull SourceSet sourceSet) {
        mFlavorConfigs.add(productFlavor);
        mFlavorSourceSets.add(sourceSet);
        mMergedFlavor = productFlavor.mergeOver(mMergedFlavor);
    }

    public void setJarDependencies(List<JarDependency> jars) {
        mJars = jars;
    }

    /**
     * Set the Library Project dependencies.
     * @param directLibraryProjects list of direct dependencies. Each library object should contain
     *            its own dependencies.
     */
    public void setAndroidDependencies(List<AndroidDependency> directLibraryProjects) {
        mDirectLibraryProjects.addAll(directLibraryProjects);
        resolveIndirectLibraryDependencies(directLibraryProjects, mFlatLibraryProjects);
    }

    public ProductFlavor getDefaultConfig() {
        return mDefaultConfig;
    }

    public SourceSet getDefaultSourceSet() {
        return mDefaultSourceSet;
    }

    public ProductFlavor getMergedFlavor() {
        return mMergedFlavor;
    }

    public BuildType getBuildType() {
        return mBuildType;
    }

    public SourceSet getBuildTypeSourceSet() {
        return mBuildTypeSourceSet;
    }

    public boolean hasFlavors() {
        return !mFlavorConfigs.isEmpty();
    }

    /**
     * @Deprecated this is only valid until we move to more than one flavor
     */
    @Deprecated
    public ProductFlavor getFirstFlavor() {
        return mFlavorConfigs.get(0);
    }

    /**
     * @Deprecated this is only valid until we move to more than one flavor
     */
    @Deprecated
    public SourceSet getFirstFlavorSourceSet() {
        return mFlavorSourceSets.get(0);
    }

    public Iterable<ProductFlavor> getFlavorConfigs() {
        return mFlavorConfigs;
    }

    public Iterable<SourceSet> getFlavorSourceSets() {
        return mFlavorSourceSets;
    }

    public boolean hasLibraries() {
        return !mDirectLibraryProjects.isEmpty();
    }

    public Iterable<AndroidDependency> getDirectLibraries() {
        return mDirectLibraryProjects;
    }

    public Iterable<AndroidDependency> getFlatLibraries() {
        return mFlatLibraryProjects;
    }

    public Type getType() {
        return mType;
    }

    /**
     * Resolves a given list of libraries, finds out if they depend on other libraries, and
     * returns a flat list of all the direct and indirect dependencies in the proper order (first
     * is higher priority when calling aapt).
     * @param directDependencies the libraries to resolve
     * @param outFlatDependencies where to store all the libraries.
     */
    @VisibleForTesting
    void resolveIndirectLibraryDependencies(List<AndroidDependency> directDependencies,
                                            List<AndroidDependency> outFlatDependencies) {
        // loop in the inverse order to resolve dependencies on the libraries, so that if a library
        // is required by two higher level libraries it can be inserted in the correct place
        for (int i = directDependencies.size() - 1  ; i >= 0 ; i--) {
            AndroidDependency library = directDependencies.get(i);

            // get its libraries
            List<AndroidDependency> dependencies = library.getDependencies();

            // resolve the dependencies for those libraries
            resolveIndirectLibraryDependencies(dependencies, outFlatDependencies);

            // and add the current one (if needed) in front (higher priority)
            if (outFlatDependencies.contains(library) == false) {
                outFlatDependencies.add(0, library);
            }
        }
    }

    /**
     * Returns the package name for this variant. This could be coming from the manifest or
     * could be overridden through the product flavors.
     * @param testedVariant the tested variant. This is needed if this variant is of type
     *                      {@link Type#TEST}
     * @return the package
     */
    public String getPackageName(VariantConfiguration testedVariant) {
        String packageName;

        if (mType == Type.TEST) {
            packageName = getTestPackage(testedVariant);
        } else {
            packageName = getPackageOverride();
            if (packageName == null) {
                packageName = getPackageFromManifest();
            }
        }

        return packageName;
    }

    /**
     * Returns the package override values coming from the Product Flavor. If the package is not
     * overridden then this returns null.
     * @return the package override or null
     */
    public String getPackageOverride() {

        String packageName = mMergedFlavor.getPackageName();
        String packageSuffix = mBuildType.getPackageNameSuffix();

        if (packageSuffix != null) {
            if (packageName == null) {
                packageName = getPackageFromManifest();
            }

            if (packageSuffix.charAt(0) == '.') {
                packageName = packageName + packageSuffix;
            } else {
                packageName = packageName + '.' + packageSuffix;
            }
        }

        return packageName;
    }

    /**
     * Returns the package name of the test app.
     * @return the package name for the test app.
     */
    public String getTestPackage(VariantConfiguration testedVariant) {
        String testPackage = mMergedFlavor.getTestPackageName();
        if (testPackage == null) {
            String testedPackage = testedVariant.getPackageName(null);

            testPackage = testedPackage + ".test";
        }

        return testPackage;
    }

    /**
     * Reads the package name from the manifest.
     * @return
     */
    @VisibleForTesting
    String getPackageFromManifest() {
        File manifestLocation = mDefaultSourceSet.getAndroidManifest();
        return sManifestParser.getPackage(manifestLocation);
    }

    protected void validate() {
        if (mType != Type.TEST) {
            File manifest = mDefaultSourceSet.getAndroidManifest();
            if (!manifest.isFile()) {
                throw new IllegalArgumentException(
                        "Main Manifest missing from " + manifest.getAbsolutePath());
            }
        }
    }
}

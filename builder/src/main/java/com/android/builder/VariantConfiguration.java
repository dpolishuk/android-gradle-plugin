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
import com.android.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * A Variant configuration.
 */
public class VariantConfiguration {

    final static ManifestParser sManifestParser = new DefaultManifestParser();

    private final ProductFlavor mDefaultConfig;
    private final SourceProvider mDefaultSourceProvider;

    private final BuildType mBuildType;
    /** SourceProvider for the BuildType. Can be null */
    private final SourceProvider mBuildTypeSourceProvider;

    private final List<ProductFlavor> mFlavorConfigs = Lists.newArrayList();
    private final List<SourceProvider> mFlavorSourceProviders = Lists.newArrayList();

    private final Type mType;
    /** Optional tested config in case type is Type#TEST */
    private final VariantConfiguration mTestedConfig;
    /** An optional output that is only valid if the type is Type#LIBRARY so that the test
     * for the library can use the library as if it was a normal dependency. */
    private AndroidDependency mOutput;

    private ProductFlavor mMergedFlavor;

    private final Set<JarDependency> mJars = Sets.newHashSet();

    /** List of direct library dependencies. Each object defines its own dependencies. */
    private final List<AndroidDependency> mDirectLibraries = Lists.newArrayList();

    /** list of all library dependencies in a flat list.
     * The order is based on the order needed to call aapt: earlier libraries override resources
     * of latter ones. */
    private final List<AndroidDependency> mFlatLibraries = Lists.newArrayList();

    public static enum Type {
        DEFAULT, LIBRARY, TEST;
    }

    /**
     * Creates the configuration with the base source set.
     *
     * This creates a config with a {@link Type#DEFAULT} type.
     *
     * @param defaultConfig
     * @param defaultSourceProvider
     * @param buildType
     * @param buildTypeSourceProvider
     */
    public VariantConfiguration(
            @NonNull ProductFlavor defaultConfig, @NonNull SourceProvider defaultSourceProvider,
            @NonNull BuildType buildType, @NonNull SourceProvider buildTypeSourceProvider) {
        this(defaultConfig, defaultSourceProvider,
                buildType, buildTypeSourceProvider,
                Type.DEFAULT, null /*testedConfig*/);
    }

    /**
     * Creates the configuration with the base source set for a given {@link Type}.
     *
     * @param defaultConfig
     * @param defaultSourceProvider
     * @param buildType
     * @param buildTypeSourceProvider
     * @param type
     */
    public VariantConfiguration(
            @NonNull ProductFlavor defaultConfig, @NonNull SourceProvider defaultSourceProvider,
            @NonNull BuildType buildType, @NonNull SourceProvider buildTypeSourceProvider,
            @NonNull Type type) {
        this(defaultConfig, defaultSourceProvider,
                buildType, buildTypeSourceProvider,
                type, null /*testedConfig*/);
    }

    /**
     * Creates the configuration with the base source set, and whether it is a library.
     * @param defaultConfig
     * @param defaultSourceProvider
     * @param buildType
     * @param buildTypeSourceProvider
     * @param type
     * @param testedConfig
     */
    public VariantConfiguration(
            @NonNull ProductFlavor defaultConfig, @NonNull SourceProvider defaultSourceProvider,
            @NonNull BuildType buildType, SourceProvider buildTypeSourceProvider,
            @NonNull Type type, @Nullable VariantConfiguration testedConfig) {
        mDefaultConfig = checkNotNull(defaultConfig);
        mDefaultSourceProvider = checkNotNull(defaultSourceProvider);
        mBuildType = checkNotNull(buildType);
        mBuildTypeSourceProvider = buildTypeSourceProvider;
        mType = checkNotNull(type);
        mTestedConfig = testedConfig;
        checkState(mType != Type.TEST || mTestedConfig != null);

        mMergedFlavor = mDefaultConfig;

        if (testedConfig != null &&
                testedConfig.mType == Type.LIBRARY &&
                testedConfig.mOutput != null) {
            mDirectLibraries.add(testedConfig.mOutput);
        }

        validate();
    }

    /**
     * Add a new configured ProductFlavor.
     *
     * If multiple flavors are added, the priority follows the order they are added when it
     * comes to resolving Android resources overlays (ie earlier added flavors supersedes
     * latter added ones).
     *
     * @param sourceProvider the configured product flavor
     */
    public void addProductFlavor(@NonNull ProductFlavor productFlavor,
                                 @NonNull SourceProvider sourceProvider) {
        mFlavorConfigs.add(productFlavor);
        mFlavorSourceProviders.add(sourceProvider);
        mMergedFlavor = productFlavor.mergeOver(mMergedFlavor);
    }

    public void setJarDependencies(List<JarDependency> jars) {
        mJars.addAll(jars);
    }

    public Collection<JarDependency> getJars() {
        return mJars;
    }

    /**
     * Set the Library Project dependencies.
     * @param directLibraries list of direct dependencies. Each library object should contain
     *            its own dependencies.
     */
    public void setAndroidDependencies(@NonNull List<AndroidDependency> directLibraries) {
        if (directLibraries != null) {
            mDirectLibraries.addAll(directLibraries);
        }

        resolveIndirectLibraryDependencies(mDirectLibraries, mFlatLibraries);
    }

    public void setOutput(AndroidDependency output) {
        mOutput = output;
    }

    public ProductFlavor getDefaultConfig() {
        return mDefaultConfig;
    }

    public SourceProvider getDefaultSourceSet() {
        return mDefaultSourceProvider;
    }

    public ProductFlavor getMergedFlavor() {
        return mMergedFlavor;
    }

    public BuildType getBuildType() {
        return mBuildType;
    }

    /**
     * The SourceProvider for the BuildType. Can be null.
     */
    public SourceProvider getBuildTypeSourceSet() {
        return mBuildTypeSourceProvider;
    }

    public boolean hasFlavors() {
        return !mFlavorConfigs.isEmpty();
    }

    public Iterable<ProductFlavor> getFlavorConfigs() {
        return mFlavorConfigs;
    }

    public Iterable<SourceProvider> getFlavorSourceSets() {
        return mFlavorSourceProviders;
    }

    public boolean hasLibraries() {
        return !mDirectLibraries.isEmpty();
    }

    /**
     * Returns the direct library dependencies
     */
    @NonNull
    public List<AndroidDependency> getDirectLibraries() {
        return mDirectLibraries;
    }

    /**
     * Returns all the library dependencies, direct and transitive.
     */
    @NonNull
    public List<AndroidDependency> getAllLibraries() {
        return mFlatLibraries;
    }

    public List<File> getPackagedJars() {
        List<File> jars = Lists.newArrayListWithCapacity(mJars.size() + mFlatLibraries.size());

        for (JarDependency jar : mJars) {
            File jarFile = new File(jar.getLocation());
            if (jarFile.exists()) {
                jars.add(jarFile);
            }
        }

        for (AndroidDependency androidDependency : mFlatLibraries) {
            File libJar = androidDependency.getJarFile();
            if (libJar.exists()) {
                jars.add(libJar);
            }
        }

        return jars;
    }

    public Type getType() {
        return mType;
    }

    public VariantConfiguration getTestedConfig() {
        return mTestedConfig;
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
        if (directDependencies == null) {
            return;
        }
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
     * Returns the original package name before any overrides from flavors.
     * If the variant is a test variant, then the package name is the one coming from the
     * configuration of the tested variant, and this call is similar to #getPackageName()
     * @return the package name
     */
    public String getOriginalPackageName() {
        if (mType == VariantConfiguration.Type.TEST) {
            return getPackageName();
        }

        return getPackageFromManifest();
    }

    /**
     * Returns the package name for this variant. This could be coming from the manifest or
     * could be overridden through the product flavors.
     * @return the package
     */
    public String getPackageName() {
        String packageName;

        if (mType == Type.TEST) {
            packageName = mMergedFlavor.getTestPackageName();
            if (packageName == null) {
                String testedPackage = mTestedConfig.getPackageName();

                packageName = testedPackage + ".test";
            }
        } else {
            packageName = getPackageOverride();
            if (packageName == null) {
                packageName = getPackageFromManifest();
            }
        }

        return packageName;
    }

    public String getTestedPackageName() {
        if (mType == Type.TEST) {
            if (mTestedConfig.mType == Type.LIBRARY) {
                return getPackageName();
            } else {
                return mTestedConfig.getPackageName();
            }
        }

        return null;
    }

    /**
     * Returns the package override values coming from the Product Flavor. If the package is not
     * overridden then this returns null.
     * @return the package override or null
     */
    public String getPackageOverride() {
        String packageName = mMergedFlavor.getPackageName();
        String packageSuffix = mBuildType.getPackageNameSuffix();

        if (packageSuffix != null && packageSuffix.length() > 0) {
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

    private final static String DEFAULT_TEST_RUNNER = "android.test.InstrumentationTestRunner";

    /**
     * Returns the instrumentionRunner to use to test this variant, or if the
     * variant is a test, the one to use to test the tested variant.
     * @return the instrumentation test runner name
     */
    public String getInstrumentationRunner() {
        VariantConfiguration config = this;
        if (mType == Type.TEST) {
            config = getTestedConfig();
        }
        String runner = config.mMergedFlavor.getTestInstrumentationRunner();
        return runner != null ? runner : DEFAULT_TEST_RUNNER;
    }

    /**
     * Reads the package name from the manifest.
     */
    public String getPackageFromManifest() {
        File manifestLocation = mDefaultSourceProvider.getManifestFile();
        return sManifestParser.getPackage(manifestLocation);
    }

    /**
     * Return the minSdkVersion for this variant.
     *
     * This uses both the value from the manifest (if present), and the override coming
     * from the flavor(s) (if present).
     * @return the minSdkVersion
     */
    public int getMinSdkVersion() {
        if (mTestedConfig != null) {
            return mTestedConfig.getMinSdkVersion();
        }
        int minSdkVersion = mMergedFlavor.getMinSdkVersion();
        if (minSdkVersion == -1) {
            // read it from the main manifest
            File manifestLocation = mDefaultSourceProvider.getManifestFile();
            minSdkVersion = sManifestParser.getMinSdkVersion(manifestLocation);
        }

        return minSdkVersion;
    }

    public File getMainManifest() {
        File defaultManifest = mDefaultSourceProvider.getManifestFile();

        // this could not exist in a test project.
        if (defaultManifest != null && defaultManifest.isFile()) {
            return defaultManifest;
        }

        return null;
    }

    public List<File> getManifestOverlays() {
        List<File> inputs = Lists.newArrayList();

        if (mBuildTypeSourceProvider != null) {
            File typeLocation = mBuildTypeSourceProvider.getManifestFile();
            if (typeLocation != null && typeLocation.isFile()) {
                inputs.add(typeLocation);
            }
        }

        for (SourceProvider sourceProvider : mFlavorSourceProviders) {
            File f = sourceProvider.getManifestFile();
            if (f != null && f.isFile()) {
                inputs.add(f);
            }
        }

        return inputs;
    }

    /**
     * Returns the dynamic list of resource folders based on the configuration, its dependencies,
     * as well as tested config if applicable (test of a library).
     * @return a list of input resource folders.
     */
    public List<File> getResourceInputs() {
        List<File> inputs = Lists.newArrayList();

        if (mBuildTypeSourceProvider != null) {
            File typeResLocation = mBuildTypeSourceProvider.getResourcesDir();
            if (typeResLocation != null) {
                inputs.add(typeResLocation);
            }
        }

        for (SourceProvider sourceProvider : mFlavorSourceProviders) {
            File flavorResLocation = sourceProvider.getResourcesDir();
            if (flavorResLocation != null) {
                inputs.add(flavorResLocation);
            }
        }

        File mainResLocation = mDefaultSourceProvider.getResourcesDir();
        if (mainResLocation != null) {
            inputs.add(mainResLocation);
        }

        for (AndroidDependency dependency : mFlatLibraries) {
            File resFolder = dependency.getResFolder();
            if (resFolder != null) {
                inputs.add(resFolder);
            }
        }

        return inputs;
    }

    public List<File> getAidlSourceList() {
        List<File> sourceList = Lists.newArrayList();
        sourceList.add(mDefaultSourceProvider.getAidlDir());
        if (mType != Type.TEST) {
            sourceList.add(mBuildTypeSourceProvider.getAidlDir());
        }

        if (hasFlavors()) {
            for (SourceProvider flavorSourceSet : mFlavorSourceProviders) {
                sourceList.add(flavorSourceSet.getAidlDir());
            }
        }

        return sourceList;
    }

    /**
     * Returns all the aidl import folder that are outside of the current project.
     */
    public List<File> getAidlImports() {
        List<File> list = Lists.newArrayList();

        for (AndroidDependency lib : mFlatLibraries) {
            File aidlLib = lib.getAidlFolder();
            if (aidlLib != null && aidlLib.isDirectory()) {
                list.add(aidlLib);
            }
        }

        return list;
    }

    /**
     * Returns the compile classpath for this config. If the config tests a library, this
     * will include the classpath of the tested config
     */
    public Set<File> getCompileClasspath() {
        Set<File> classpath = Sets.newHashSet();

        for (AndroidDependency lib : mFlatLibraries) {
            classpath.add(lib.getJarFile());
        }

        for (JarDependency jar : mJars) {
            classpath.add(new File(jar.getLocation()));
        }

        return classpath;
    }

    public List<String> getBuildConfigLines() {
        List<String> fullList = Lists.newArrayList();

        List<String> list = mDefaultConfig.getBuildConfig();
        if (!list.isEmpty()) {
            fullList.add("// lines from default config.");
            fullList.addAll(list);
        }

        list = mBuildType.getBuildConfig();
        if (!list.isEmpty()) {
            fullList.add("// lines from build type: " + mBuildType.getName());
            fullList.addAll(list);
        }

        for (ProductFlavor flavor : mFlavorConfigs) {
            list = flavor.getBuildConfig();
            if (!list.isEmpty()) {
                fullList.add("// lines from product flavor: " + flavor.getName());
                fullList.addAll(list);
            }
        }

        return fullList;
    }

    protected void validate() {
        if (mType != Type.TEST) {
            File manifest = mDefaultSourceProvider.getManifestFile();
            if (!manifest.isFile()) {
                throw new IllegalArgumentException(
                        "Main Manifest missing from " + manifest.getAbsolutePath());
            }
        }
    }
}

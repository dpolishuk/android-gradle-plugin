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
import com.android.builder.packaging.DuplicateFileException;
import com.android.builder.packaging.JavaResourceProcessor;
import com.android.builder.packaging.Packager;
import com.android.builder.packaging.PackagerException;
import com.android.builder.packaging.SealedPackageException;
import com.android.builder.signing.DebugKeyHelper;
import com.android.builder.signing.KeytoolException;
import com.android.builder.signing.SigningInfo;
import com.android.manifmerger.ManifestMerger;
import com.android.manifmerger.MergerLog;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.IAndroidTarget.IOptionalLibrary;
import com.android.sdklib.io.FileOp;
import com.android.utils.ILogger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * This is the main builder class. It is given all the data to process the build (such as
 * {@link ProductFlavor}s, {@link BuildType} and dependencies) and use them when doing specific
 * build steps.
 *
 * To use:
 * create a builder with {@link #AndroidBuilder(SdkParser, ILogger, boolean)},
 * configure compile target with {@link #setTarget(String)}
 * configure build variant with {@link #setBuildVariant(ProductFlavorHolder, BuildTypeHolder)},
 * optionally add flavors with {@link #addProductFlavor(ProductFlavorHolder)},
 * configure dependencies with {@link #setAndroidDependencies(List)} and
 *     {@link #setJarDependencies(List)},
 *
 * then build steps can be done with
 * {@link #generateBuildConfig(String, java.util.List)}
 * {@link #mergeLibraryManifests(File, List, File)}
 * {@link #processResources(String, String, String, String, String, AaptOptions)}
 * {@link #convertBytecode(String, String, DexOptions)}
 * {@link #packageApk(String, String, String, String)}
 *
 * Java compilation is not handled but the builder provides the runtime classpath with
 * {@link #getRuntimeClasspath()}.
 */
public class AndroidBuilder {

    private final SdkParser mSdkParser;
    private final ILogger mLogger;
    private final ManifestParser mManifestParser;
    private final CommandLineRunner mCmdLineRunner;
    private final boolean mVerboseExec;

    private IAndroidTarget mTarget;

    private BuildTypeHolder mBuildTypeHolder;
    private ProductFlavorHolder mMainFlavorHolder;
    private List<ProductFlavorHolder> mFlavorHolderList;
    private ProductFlavor mMergedFlavor;

    private List<JarDependency> mJars;

    /** List of direct library project dependencies. Each object defines its own dependencies. */
    private final List<AndroidDependency> mDirectLibraryProjects =
            new ArrayList<AndroidDependency>();
    /** list of all library project dependencies in the flat list.
     * The order is based on the order needed to call aapt: earlier libraries override resources
     * of latter ones. */
    private final List<AndroidDependency> mFlatLibraryProjects = new ArrayList<AndroidDependency>();

    /**
     * Creates an AndroidBuilder
     * <p/>
     * This receives an {@link SdkParser} to provide the build with information about the SDK, as
     * well as an {@link ILogger} to display output.
     * <p/>
     * <var>verboseExec</var> is needed on top of the ILogger due to remote exec tools not being
     * able to output info and verbose messages separately.
     *
     * @param sdkParser
     * @param logger
     * @param verboseExec
     */
    public AndroidBuilder(@NonNull SdkParser sdkParser, ILogger logger, boolean verboseExec) {
        mSdkParser = sdkParser;
        mLogger = logger;
        mVerboseExec = verboseExec;
        mManifestParser = new DefaultManifestParser();
        mCmdLineRunner = new CommandLineRunner(mLogger);
    }

    @VisibleForTesting
    AndroidBuilder(
            @NonNull SdkParser sdkParser,
            @NonNull ManifestParser manifestParser,
            @NonNull CommandLineRunner cmdLineRunner,
            @NonNull ILogger logger,
            boolean verboseExec) {
        mSdkParser = sdkParser;
        mLogger = logger;
        mVerboseExec = verboseExec;
        mManifestParser = manifestParser;
        mCmdLineRunner = cmdLineRunner;
    }

    /**
     * Sets the compilation target hash string.
     *
     * @param target the compilation target
     *
     * @see IAndroidTarget#hashString()
     */
    public void setTarget(@NonNull String target) {
        mTarget = mSdkParser.resolveTarget(target, mLogger);

        if (mTarget == null) {
            throw new RuntimeException("Unknown target: " + target);
        }
    }

    /**
     * Sets the initial build variant by providing the main flavor and the build type.
     * @param mainFlavorHolder the main ProductFlavor
     * @param buildTypeHolder the Build Type.
     */
    public void setBuildVariant(
            @NonNull ProductFlavorHolder mainFlavorHolder,
            @NonNull BuildTypeHolder buildTypeHolder) {
        mMainFlavorHolder = mainFlavorHolder;
        mBuildTypeHolder = buildTypeHolder;
        mMergedFlavor = mMainFlavorHolder.getProductFlavor();
        validateMainFlavor();
    }

    /**
     * Add a new ProductFlavor.
     *
     * If multiple flavors are added, the priority follows the order they are added when it
     * comes to resolving Android resources overlays (ie earlier added flavors supersedes
     * latter added ones).
     *
     * @param productFlavorHolder the ProductFlavorHolder
     */
    public void addProductFlavor(ProductFlavorHolder productFlavorHolder) {
        if (mMainFlavorHolder == null) {
            throw new IllegalArgumentException(
                    "main flavor is null. setBuildVariant must be called first");
        }
        if (mFlavorHolderList == null) {
            mFlavorHolderList = new ArrayList<ProductFlavorHolder>();
        }

        mFlavorHolderList.add(productFlavorHolder);
        mMergedFlavor = productFlavorHolder.getProductFlavor().mergeOver(mMergedFlavor);
    }

    /**
     * Returns the runtime classpath to be used during compilation.
     */
    public List<String> getRuntimeClasspath() {
        if (mTarget == null) {
            throw new IllegalArgumentException("Target not set.");
        }

        List<String> classpath = new ArrayList<String>();

        classpath.add(mTarget.getPath(IAndroidTarget.ANDROID_JAR));

        // add optional libraries if any
        IOptionalLibrary[] libs = mTarget.getOptionalLibraries();
        if (libs != null) {
            for (IOptionalLibrary lib : libs) {
                classpath.add(lib.getJarPath());
            }
        }

        // add annotations.jar if needed.
        if (mTarget.getVersion().getApiLevel() <= 15) {
            classpath.add(mSdkParser.getAnnotationsJar());
        }

        return classpath;
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

    /**
     * Generate the BuildConfig class for the project.
     * @param sourceOutputDir directory where to put this. This is the source folder, not the
     *                        package folder.
     * @param additionalLines additional lines to put in the class. These must be valid Java lines.
     * @throws IOException
     */
    public void generateBuildConfig(
            @NonNull String sourceOutputDir,
            @Nullable List<String> additionalLines) throws IOException {
        if (mMainFlavorHolder == null || mBuildTypeHolder == null) {
            throw new IllegalArgumentException("No Product Flavor or Build Type set.");
        }
        if (mTarget == null) {
            throw new IllegalArgumentException("Target not set.");
        }

        File manifest = mMainFlavorHolder.getAndroidManifest();
        String manifestLocation = manifest.getAbsolutePath();

        String packageName = getPackageOverride(manifestLocation);
        if (packageName == null) {
            packageName = getPackageFromManifest(manifestLocation);
        }

        BuildConfigGenerator generator = new BuildConfigGenerator(
                sourceOutputDir, packageName, mBuildTypeHolder.getBuildType().isDebuggable());
        generator.generate(additionalLines);
    }

    /**
     * Pre-process resources. This crunches images and process 9-patches before they can
     * be packaged.
     * This is incremental.
     *
     * @param resOutputDir where the processed resources are stored.
     * @throws IOException
     * @throws InterruptedException
     */
    public void preprocessResources(@NonNull String resOutputDir)
            throws IOException, InterruptedException {
        if (mMainFlavorHolder == null || mBuildTypeHolder == null) {
            throw new IllegalArgumentException("No Product Flavor or Build Type set.");
        }
        if (mTarget == null) {
            throw new IllegalArgumentException("Target not set.");
        }

        // launch aapt: create the command line
        ArrayList<String> command = new ArrayList<String>();

        @SuppressWarnings("deprecation")
        String aaptPath = mTarget.getPath(IAndroidTarget.AAPT);

        command.add(aaptPath);
        command.add("crunch");

        if (mVerboseExec) {
            command.add("-v");
        }

        File typeResLocation = mBuildTypeHolder.getAndroidResources();
        if (typeResLocation != null && typeResLocation.isDirectory()) {
            command.add("-S");
            command.add(typeResLocation.getAbsolutePath());
        }

        for (ProductFlavorHolder holder : mFlavorHolderList) {
            File flavorResLocation = holder.getAndroidResources();
            if (flavorResLocation != null && flavorResLocation.isDirectory()) {
                command.add("-S");
                command.add(flavorResLocation.getAbsolutePath());
            }
        }

        File mainResLocation = mMainFlavorHolder.getAndroidResources();
        if (mainResLocation != null && mainResLocation.isDirectory()) {
            command.add("-S");
            command.add(mainResLocation.getAbsolutePath());
        }

        command.add("-C");
        command.add(resOutputDir);

        mCmdLineRunner.runCmdLine(command);
    }

    /**
     * Merges all the manifest from the BuildType and ProductFlavor(s) into a single manifest.
     *
     * TODO: figure out the order. Libraries first or buildtype/flavors first?
     *
     * @param outManifestLocation the output location for the merged manifest
     */
    public void mergeManifest(@NonNull String outManifestLocation) {
        if (mMainFlavorHolder == null || mBuildTypeHolder == null) {
            throw new IllegalArgumentException("No Product Flavor or Build Type set.");
        }
        if (mTarget == null) {
            throw new IllegalArgumentException("Target not set.");
        }

        try {
            File mainLocation = mMainFlavorHolder.getAndroidManifest();
            File typeLocation = mBuildTypeHolder.getAndroidManifest();
            if (typeLocation != null && typeLocation.isDirectory() == false) {
                typeLocation = null;
            }

            List<File> flavorManifests = new ArrayList<File>();
            for (ProductFlavorHolder holder : mFlavorHolderList) {
                File f = holder.getAndroidManifest();
                if (f != null && f.isDirectory()) {
                    flavorManifests.add(f);
                }
            }

            // if no manifest to merge, just copy to location
            if (typeLocation == null && flavorManifests.isEmpty() &&
                    mFlatLibraryProjects.isEmpty()) {
                new FileOp().copyFile(mainLocation, new File(outManifestLocation));
            } else {
                if (mFlatLibraryProjects.isEmpty()) {

                    File appMergeOut = new File(outManifestLocation);

                    List<File> manifests = new ArrayList<File>();
                    if (typeLocation != null) {
                        manifests.add(typeLocation);
                    }
                    manifests.addAll(flavorManifests);

                    ManifestMerger merger = new ManifestMerger(MergerLog.wrapSdkLog(mLogger));
                    if (merger.process(
                            appMergeOut,
                            mainLocation,
                            manifests.toArray(new File[manifests.size()])) == false) {
                        throw new RuntimeException();
                    }
                } else {

                    File appMergeOut = File.createTempFile("manifestMerge", ".xml");
                    appMergeOut.deleteOnExit();

                    // recursively merge all manifests starting with the leaves and up toward the
                    // root (the app)
                    mergeLibraryManifests(appMergeOut, mDirectLibraryProjects,
                            new File(outManifestLocation));
                    }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void mergeLibraryManifests(
            File mainManifest,
            List<AndroidDependency> libraries,
            File outManifest) throws IOException {

        List<File> manifests = new ArrayList<File>();
        for (AndroidDependency library : libraries) {
            List<AndroidDependency> subLibraries = library.getDependencies();
            if (subLibraries == null || subLibraries.size() == 0) {
                manifests.add(new File(library.getManifest()));
            } else {
                File mergeLibManifest = File.createTempFile("manifestMerge", ".xml");
                mergeLibManifest.deleteOnExit();

                mergeLibraryManifests(
                        new File(library.getManifest()), subLibraries, mergeLibManifest);

                manifests.add(mergeLibManifest);
            }
        }

        ManifestMerger merger = new ManifestMerger(MergerLog.wrapSdkLog(mLogger));
        if (merger.process(
                outManifest,
                mainManifest,
                manifests.toArray(new File[manifests.size()])) == false) {
            throw new RuntimeException();
        }
    }

    public void processResources(
            @NonNull String manifestFile,
            @Nullable String preprocessResDir,
            @Nullable String sourceOutputDir,
            @Nullable String resPackageOutput,
            @Nullable String proguardOutput,
            @NonNull AaptOptions options) throws IOException, InterruptedException {
        if (mMainFlavorHolder == null || mBuildTypeHolder == null) {
            throw new IllegalArgumentException("No Product Flavor or Build Type set.");
        }
        if (mTarget == null) {
            throw new IllegalArgumentException("Target not set.");
        }

        // if both output types are empty, then there's nothing to do and this is an error
        if (sourceOutputDir == null && resPackageOutput == null) {
            throw new IllegalArgumentException("no output provided for aapt task");
        }

        // launch aapt: create the command line
        ArrayList<String> command = new ArrayList<String>();

        @SuppressWarnings("deprecation")
        String aaptPath = mTarget.getPath(IAndroidTarget.AAPT);

        command.add(aaptPath);
        command.add("package");

        if (mVerboseExec) {
            command.add("-v");
        }

        command.add("-f");
        command.add("--no-crunch");

        // inputs
        command.add("-I");
        command.add(mTarget.getPath(IAndroidTarget.ANDROID_JAR));

        command.add("-M");
        command.add(manifestFile);

        // TODO: handle libraries!
        boolean useOverlay =  false;
        if (preprocessResDir != null) {
            command.add("-S");
            command.add(preprocessResDir);
            useOverlay = true;
        }

        File typeResLocation = mBuildTypeHolder.getAndroidResources();
        if (typeResLocation != null && typeResLocation.isDirectory()) {
            command.add("-S");
            command.add(typeResLocation.getAbsolutePath());
            useOverlay = true;
        }

        for (ProductFlavorHolder holder : mFlavorHolderList) {
            File flavorResLocation = holder.getAndroidResources();
            if (flavorResLocation != null && typeResLocation.isDirectory()) {
                command.add("-S");
                command.add(flavorResLocation.getAbsolutePath());
                useOverlay = true;
            }
        }

        File mainResLocation = mMainFlavorHolder.getAndroidResources();
        command.add("-S");
        command.add(mainResLocation.getAbsolutePath());

        if (useOverlay) {
            command.add("--auto-add-overlay");
        }

        // TODO support 2+ assets folders.
//        if (typeAssetsLocation != null) {
//            command.add("-A");
//            command.add(typeAssetsLocation);
//        }
//
//        if (flavorAssetsLocation != null) {
//            command.add("-A");
//            command.add(flavorAssetsLocation);
//        }

        File mainAssetsLocation = mMainFlavorHolder.getAndroidAssets();
        if (mainAssetsLocation != null && mainAssetsLocation.isDirectory()) {
            command.add("-A");
            command.add(mainAssetsLocation.getAbsolutePath());
        }

        // outputs

        if (sourceOutputDir != null) {
            command.add("-m");
            command.add("-J");
            command.add(sourceOutputDir);
        }

        if (resPackageOutput != null) {
            command.add("-F");
            command.add(resPackageOutput);

            if (proguardOutput != null) {
                command.add("-G");
                command.add(proguardOutput);
            }
        }

        // options controlled by build variants

        if (mBuildTypeHolder.getBuildType().isDebuggable()) {
            command.add("--debug-mode");
        }

        String packageOverride = getPackageOverride(manifestFile);
        if (packageOverride != null) {
            command.add("--rename-manifest-package");
            command.add(packageOverride);
            mLogger.verbose("Inserting package '%s' in AndroidManifest.xml", packageOverride);
        }

        boolean forceErrorOnReplace = false;

        int versionCode = mMergedFlavor.getVersionCode();
        if (versionCode != -1) {
            command.add("--version-code");
            command.add(Integer.toString(versionCode));
            mLogger.verbose("Inserting versionCode '%d' in AndroidManifest.xml", versionCode);
            forceErrorOnReplace = true;
        }

        String versionName = mMergedFlavor.getVersionName();
        if (versionName != null) {
            command.add("--version-name");
            command.add(versionName);
            mLogger.verbose("Inserting versionName '%s' in AndroidManifest.xml", versionName);
            forceErrorOnReplace = true;
        }

        int minSdkVersion = mMergedFlavor.getMinSdkVersion();
        if (minSdkVersion != -1) {
            command.add("--min-sdk-version");
            command.add(Integer.toString(minSdkVersion));
            mLogger.verbose("Inserting minSdkVersion '%d' in AndroidManifest.xml", minSdkVersion);
            forceErrorOnReplace = true;
        }

        int targetSdkVersion = mMergedFlavor.getTargetSdkVersion();
        if (targetSdkVersion != -1) {
            command.add("--target-sdk-version");
            command.add(Integer.toString(targetSdkVersion));
            mLogger.verbose("Inserting targetSdkVersion '%d' in AndroidManifest.xml",
                    targetSdkVersion);
            forceErrorOnReplace = true;
        }

        if (forceErrorOnReplace) {
            // TODO: force aapt to fail if replace of versionCode/Name or min/targetSdkVersion fails
            // Need to add the options to aapt first.
        }

        // AAPT options
        if (options.getIgnoreAssetsPattern() != null) {
            command.add("---ignore-assets");
            command.add(options.getIgnoreAssetsPattern());
        }

        List<String> noCompressList = options.getNoCompressList();
        if (noCompressList != null) {
            for (String noCompress : noCompressList) {
                command.add("0");
                command.add(noCompress);
            }
        }

        mCmdLineRunner.runCmdLine(command);
    }

    public void convertBytecode(
            @NonNull List<String> classesLocation,
            @NonNull String outDexFile,
            @NonNull DexOptions dexOptions) throws IOException, InterruptedException {
        if (mMainFlavorHolder == null || mBuildTypeHolder == null) {
            throw new IllegalArgumentException("No Product Flavor or Build Type set.");
        }
        if (mTarget == null) {
            throw new IllegalArgumentException("Target not set.");
        }

        // launch dx: create the command line
        ArrayList<String> command = new ArrayList<String>();

        @SuppressWarnings("deprecation")
        String dxPath = mTarget.getPath(IAndroidTarget.DX);
        command.add(dxPath);

        command.add("--dex");

        if (mVerboseExec) {
            command.add("--verbose");
        }

        command.add("--output");
        command.add(outDexFile);

        // TODO: handle dependencies
        // TODO: handle dex options

        mLogger.verbose("Input: " + classesLocation);

        command.addAll(classesLocation);

        mCmdLineRunner.runCmdLine(command);
    }

    /**
     * Packages the apk.
     * @param androidResPkgLocation
     * @param classesDexLocation
     * @param jniLibsLocation
     * @param outApkLocation
     */
    public void packageApk(
            @NonNull String androidResPkgLocation,
            @NonNull String classesDexLocation,
            @Nullable String jniLibsLocation,
            @NonNull String outApkLocation) throws DuplicateFileException {
        if (mMainFlavorHolder == null || mBuildTypeHolder == null) {
            throw new IllegalArgumentException("No Product Flavor or Build Type set.");
        }
        if (mTarget == null) {
            throw new IllegalArgumentException("Target not set.");
        }

        SigningInfo signingInfo = null;
        if (mBuildTypeHolder.getBuildType().isDebugSigningKey()) {
            try {
                String storeLocation = DebugKeyHelper.defaultDebugKeyStoreLocation();
                File storeFile = new File(storeLocation);
                if (storeFile.isDirectory()) {
                    throw new RuntimeException(
                            String.format("A folder is in the way of the debug keystore: %s",
                                    storeLocation));
                } else if (storeFile.exists() == false) {
                    if (DebugKeyHelper.createNewStore(
                            storeLocation, null /*storeType*/, mLogger) == false) {
                        throw new RuntimeException();
                    }
                }

                // load the key
                signingInfo = DebugKeyHelper.getDebugKey(storeLocation, null /*storeStype*/);

            } catch (AndroidLocationException e) {
                throw new RuntimeException(e);
            } catch (KeytoolException e) {
                throw new RuntimeException(e);
            } catch (FileNotFoundException e) {
                // this shouldn't happen as we have checked ahead of calling getDebugKey.
                throw new RuntimeException(e);
            }
        } else {
            // todo: get the signing info from the flavor.
        }

        try {
            Packager packager = new Packager(
                    outApkLocation, androidResPkgLocation, classesDexLocation,
                    signingInfo, mLogger);

            packager.setDebugJniMode(mBuildTypeHolder.getBuildType().isDebugJniBuild());

            // figure out conflicts!
            JavaResourceProcessor resProcessor = new JavaResourceProcessor(packager);

            Set<File> buildTypeJavaResLocations = mBuildTypeHolder.getJavaResources();
            for (File buildTypeJavaResLocation : buildTypeJavaResLocations) {
                if (buildTypeJavaResLocation != null && buildTypeJavaResLocation.isDirectory()) {
                    resProcessor.addSourceFolder(buildTypeJavaResLocation.getAbsolutePath());
                }
            }

            for (ProductFlavorHolder holder : mFlavorHolderList) {

                Set<File> flavorJavaResLocations = holder.getJavaResources();
                for (File flavorJavaResLocation : flavorJavaResLocations) {
                    if (flavorJavaResLocation != null && flavorJavaResLocation.isDirectory()) {
                        resProcessor.addSourceFolder(flavorJavaResLocation.getAbsolutePath());
                    }
                }
            }

            Set<File> mainJavaResLocations = mMainFlavorHolder.getJavaResources();
            for (File mainJavaResLocation : mainJavaResLocations) {
                if (mainJavaResLocation != null && mainJavaResLocation.isDirectory()) {
                    resProcessor.addSourceFolder(mainJavaResLocation.getAbsolutePath());
                }
            }

            // also add resources from library projects and jars
            if (jniLibsLocation != null) {
                packager.addNativeLibraries(jniLibsLocation);
            }

            packager.sealApk();
        } catch (PackagerException e) {
            throw new RuntimeException(e);
        } catch (SealedPackageException e) {
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting
    String getPackageOverride(@NonNull String manifestLocation) {
        String packageName = mMergedFlavor.getPackageName();
        String packageSuffix = mBuildTypeHolder.getBuildType().getPackageNameSuffix();

        if (packageSuffix != null) {
            if (packageName == null) {
                packageName = getPackageFromManifest(manifestLocation);
            }

            if (packageSuffix.charAt(0) == '.') {
                packageName = packageName + packageSuffix;
            } else {
                packageName = packageName + '.' + packageSuffix;
            }
        }

        return packageName;
    }

    @VisibleForTesting
    String getPackageFromManifest(@NonNull String manifestLocation) {
        return mManifestParser.getPackage(manifestLocation);
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

    protected void validateMainFlavor() {
        File manifest = mMainFlavorHolder.getAndroidManifest();
        if (!manifest.isFile()) {
            throw new IllegalArgumentException(
                    "Main Manifest missing from " + manifest.getAbsolutePath());
        }
    }

}

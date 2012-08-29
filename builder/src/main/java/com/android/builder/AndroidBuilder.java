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
import com.android.builder.signing.KeystoreHelper;
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
 * configure build variant with {@link #setBuildVariant(VariantConfiguration, VariantConfiguration)}
 *
 * then build steps can be done with
 * {@link #generateBuildConfig(String, java.util.List)}
 * {@link #processManifest(String)}
 * {@link #processResources(String, String, String, String, String, AaptOptions)}
 * {@link #convertBytecode(java.util.List, String, DexOptions)}
 * {@link #packageApk(String, String, String, String)}
 *
 * Java compilation is not handled but the builder provides the runtime classpath with
 * {@link #getRuntimeClasspath()}.
 */
public class AndroidBuilder {

    private final SdkParser mSdkParser;
    private final ILogger mLogger;
    private final CommandLineRunner mCmdLineRunner;
    private final boolean mVerboseExec;

    private IAndroidTarget mTarget;

    // config for the main app.
    private VariantConfiguration mVariant;
    // config for the tested app.
    private VariantConfiguration mTestedVariant;

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
        if (target == null) {
            throw new RuntimeException("Compilation target not set!");
        }
        mTarget = mSdkParser.resolveTarget(target, mLogger);

        if (mTarget == null) {
            throw new RuntimeException("Unknown target: " + target);
        }
    }

    /**
     * Sets the build variant.
     *
     * @param variant the configuration of the variant
     * @param testedVariant the configuration of the tested variant. only applicable if
     *                      the main config is a test variant.
     *
     */
    public void setBuildVariant(@NonNull VariantConfiguration variant,
                                @Nullable VariantConfiguration testedVariant) {
        mVariant = variant;
        mTestedVariant = testedVariant;
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
        if (mVariant == null) {
            throw new IllegalArgumentException("No Variant Configuration has been set.");
        }
        if (mTarget == null) {
            throw new IllegalArgumentException("Target not set.");
        }

        String packageName;
        if (mVariant.getType() == VariantConfiguration.Type.TEST) {
            packageName = mVariant.getPackageName(mTestedVariant);
        } else {
            packageName = mVariant.getPackageFromManifest();
        }

        BuildConfigGenerator generator = new BuildConfigGenerator(
                sourceOutputDir, packageName, mVariant.getBuildType().isDebuggable());
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
        if (mVariant == null) {
            throw new IllegalArgumentException("No Variant Configuration has been set.");
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

        boolean hasResToCrunch = false;

        if (mVariant.getBuildTypeSourceSet() != null) {
            File typeResLocation = mVariant.getBuildTypeSourceSet().getAndroidResources();
            if (typeResLocation != null && typeResLocation.isDirectory()) {
                command.add("-S");
                command.add(typeResLocation.getAbsolutePath());
                hasResToCrunch = true;
            }
        }

        for (SourceSet sourceSet : mVariant.getFlavorSourceSets()) {
            File flavorResLocation = sourceSet.getAndroidResources();
            if (flavorResLocation != null && flavorResLocation.isDirectory()) {
                command.add("-S");
                command.add(flavorResLocation.getAbsolutePath());
                hasResToCrunch = true;
            }
        }

        File mainResLocation = mVariant.getDefaultSourceSet().getAndroidResources();
        if (mainResLocation != null && mainResLocation.isDirectory()) {
            command.add("-S");
            command.add(mainResLocation.getAbsolutePath());
            hasResToCrunch = true;
        }

        command.add("-C");
        command.add(resOutputDir);

        if (hasResToCrunch) {
            mCmdLineRunner.runCmdLine(command);
        }
    }

    /**
     * Merges all the manifest from the BuildType and ProductFlavor(s) into a single manifest.
     *
     * TODO: figure out the order. Libraries first or buildtype/flavors first?
     *
     * @param outManifestLocation the output location for the merged manifest
     */
    public void processManifest(@NonNull String outManifestLocation) {
        if (mVariant == null) {
            throw new IllegalArgumentException("No Variant Configuration has been set.");
        }
        if (mTarget == null) {
            throw new IllegalArgumentException("Target not set.");
        }

        if (mTestedVariant != null) {
            generateTestManifest(outManifestLocation);
        } else {
            mergeManifest(outManifestLocation);
        }
    }

    private void generateTestManifest(String outManifestLocation) {
        TestManifestGenerator generator = new TestManifestGenerator(outManifestLocation,
                mVariant.getPackageName(mTestedVariant),
                mTestedVariant.getPackageName(null),
                mTestedVariant.getInstrumentationRunner());

        try {
            generator.generate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void mergeManifest(String outManifestLocation) {
        try {
            File mainLocation = mVariant.getDefaultSourceSet().getAndroidManifest();
            File typeLocation = mVariant.getBuildTypeSourceSet().getAndroidManifest();
            if (typeLocation != null && typeLocation.isDirectory() == false) {
                typeLocation = null;
            }

            List<File> flavorManifests = new ArrayList<File>();
            for (SourceSet sourceSet : mVariant.getFlavorSourceSets()) {
                File f = sourceSet.getAndroidManifest();
                if (f != null && f.isFile()) {
                    flavorManifests.add(f);
                }
            }

            // if no manifest to merge, just copy to location
            if (typeLocation == null && flavorManifests.isEmpty() && !mVariant.hasLibraries()) {
                new FileOp().copyFile(mainLocation, new File(outManifestLocation));
            } else {
                if (!mVariant.hasLibraries()) {

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
                    mergeLibraryManifests(appMergeOut, mVariant.getDirectLibraries(),
                            new File(outManifestLocation));
                    }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void mergeLibraryManifests(
            File mainManifest,
            Iterable<AndroidDependency> directLibraries,
            File outManifest) throws IOException {

        List<File> manifests = new ArrayList<File>();
        for (AndroidDependency library : directLibraries) {
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
        if (mVariant == null) {
            throw new IllegalArgumentException("No Variant Configuration has been set.");
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
            File preprocessResFile = new File(preprocessResDir);
            if (preprocessResFile.isDirectory()) {
                command.add("-S");
                command.add(preprocessResDir);
                useOverlay = true;
            }
        }

        if (mVariant.getBuildTypeSourceSet() != null) {
            File typeResLocation = mVariant.getBuildTypeSourceSet().getAndroidResources();
            if (typeResLocation != null && typeResLocation.isDirectory()) {
                command.add("-S");
                command.add(typeResLocation.getAbsolutePath());
                useOverlay = true;
            }
        }

        for (SourceSet sourceSet : mVariant.getFlavorSourceSets()) {
            File flavorResLocation = sourceSet.getAndroidResources();
            if (flavorResLocation != null && flavorResLocation.isDirectory()) {
                command.add("-S");
                command.add(flavorResLocation.getAbsolutePath());
                useOverlay = true;
            }
        }

        File mainResLocation = mVariant.getDefaultSourceSet().getAndroidResources();
        if (mainResLocation != null && mainResLocation.isDirectory()) {
            command.add("-S");
            command.add(mainResLocation.getAbsolutePath());
        }

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

        File mainAssetsLocation = mVariant.getDefaultSourceSet().getAndroidAssets();
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

        if (mVariant.getType() != VariantConfiguration.Type.LIBRARY && resPackageOutput != null) {
            command.add("-F");
            command.add(resPackageOutput);

            if (proguardOutput != null) {
                command.add("-G");
                command.add(proguardOutput);
            }
        }

        // options controlled by build variants

        if (mVariant.getBuildType().isDebuggable()) {
            command.add("--debug-mode");
        }

        if (mVariant.getType() == VariantConfiguration.Type.DEFAULT) {
            String packageOverride = mVariant.getPackageOverride();
            if (packageOverride != null) {
                command.add("--rename-manifest-package");
                command.add(packageOverride);
                mLogger.verbose("Inserting package '%s' in AndroidManifest.xml", packageOverride);
            }

            boolean forceErrorOnReplace = false;

            ProductFlavor mergedFlavor = mVariant.getMergedFlavor();

            int versionCode = mergedFlavor.getVersionCode();
            if (versionCode != -1) {
                command.add("--version-code");
                command.add(Integer.toString(versionCode));
                mLogger.verbose("Inserting versionCode '%d' in AndroidManifest.xml", versionCode);
                forceErrorOnReplace = true;
            }

            String versionName = mergedFlavor.getVersionName();
            if (versionName != null) {
                command.add("--version-name");
                command.add(versionName);
                mLogger.verbose("Inserting versionName '%s' in AndroidManifest.xml", versionName);
                forceErrorOnReplace = true;
            }

            int minSdkVersion = mergedFlavor.getMinSdkVersion();
            if (minSdkVersion != -1) {
                command.add("--min-sdk-version");
                command.add(Integer.toString(minSdkVersion));
                mLogger.verbose("Inserting minSdkVersion '%d' in AndroidManifest.xml",
                        minSdkVersion);
                forceErrorOnReplace = true;
            }

            int targetSdkVersion = mergedFlavor.getTargetSdkVersion();
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
        }

        // AAPT options
        String ignoreAssets = options.getIgnoreAssets();
        if (ignoreAssets != null) {
            command.add("---ignore-assets");
            command.add(ignoreAssets);
        }

        List<String> noCompressList = options.getNoCompress();
        if (noCompressList != null) {
            for (String noCompress : noCompressList) {
                command.add("-0");
                command.add(noCompress);
            }
        }

        mLogger.verbose("aapt command: %s", command.toString());

        mCmdLineRunner.runCmdLine(command);
    }

    public void convertBytecode(
            @NonNull List<String> classesLocation,
            @NonNull String outDexFile,
            @NonNull DexOptions dexOptions) throws IOException, InterruptedException {
        if (mVariant == null) {
            throw new IllegalArgumentException("No Variant Configuration has been set.");
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
        if (mVariant == null) {
            throw new IllegalArgumentException("No Variant Configuration has been set.");
        }
        if (mTarget == null) {
            throw new IllegalArgumentException("Target not set.");
        }

        BuildType buildType = mVariant.getBuildType();

        SigningInfo signingInfo = null;
        try {
            if (buildType.isDebugSigned()) {
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
            } else if (mVariant.getMergedFlavor().isSigningReady()) {
                ProductFlavor flavor = mVariant.getMergedFlavor();
                signingInfo = KeystoreHelper.getSigningInfo(
                        flavor.getSigningStoreLocation(),
                        flavor.getSigningStorePassword(),
                        null, /*storeStype*/
                        flavor.getSigningKeyAlias(),
                        flavor.getSigningKeyPassword());
            }
        } catch (AndroidLocationException e) {
            throw new RuntimeException(e);
        } catch (KeytoolException e) {
            throw new RuntimeException(e);
        } catch (FileNotFoundException e) {
            // this shouldn't happen as we have checked ahead of calling getDebugKey.
            throw new RuntimeException(e);
        }

        try {
            Packager packager = new Packager(
                    outApkLocation, androidResPkgLocation, classesDexLocation,
                    signingInfo, mLogger);

            packager.setDebugJniMode(buildType.isDebugJniBuild());

            // figure out conflicts!
            JavaResourceProcessor resProcessor = new JavaResourceProcessor(packager);

            if (mVariant.getBuildTypeSourceSet() != null) {
                Set<File> buildTypeJavaResLocations =
                        mVariant.getBuildTypeSourceSet().getJavaResources();
                for (File buildTypeJavaResLocation : buildTypeJavaResLocations) {
                    if (buildTypeJavaResLocation != null &&
                            buildTypeJavaResLocation.isDirectory()) {
                        resProcessor.addSourceFolder(buildTypeJavaResLocation.getAbsolutePath());
                    }
                }
            }

            for (SourceSet sourceSet : mVariant.getFlavorSourceSets()) {

                Set<File> flavorJavaResLocations = sourceSet.getJavaResources();
                for (File flavorJavaResLocation : flavorJavaResLocations) {
                    if (flavorJavaResLocation != null && flavorJavaResLocation.isDirectory()) {
                        resProcessor.addSourceFolder(flavorJavaResLocation.getAbsolutePath());
                    }
                }
            }

            Set<File> mainJavaResLocations = mVariant.getDefaultSourceSet().getJavaResources();
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
}

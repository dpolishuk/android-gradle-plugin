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

/**
 * This is the main builder class. It is given all the data to process the build (such as
 * {@link ProductFlavor}, {@link BuildType} and dependencies) and use them when doing specific
 * build steps.
 *
 * To use:
 * create a builder with {@link #AndroidBuilder(SdkParser, ILogger, boolean)},
 * configure compile target with {@link #setTarget(String)}
 * configure build variant with {@link #setBuildVariant(ProductFlavor, ProductFlavor, BuildType)},
 * configure dependencies with {@link #setAndroidDependencies(List)} and
 *     {@link #setJarDependencies(List)},
 *
 * then build steps can be done with
 * {@link #generateBuildConfig(String, String)}
 * {@link #mergeLibraryManifests(File, List, File)}
 * {@link #processResources(String, String, String, String, String, String, String, String, String, String, String, AaptOptions)}
 * {@link #convertBytecode(String, String, DexOptions)}
 * {@link #packageApk(String, String, String, String, String, String, String)}
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

    private ProductFlavor mProductFlavor;
    private BuildType mBuildType;

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
     * Sets the build variant by providing the main and custom flavors and the build type
     * @param mainFlavor
     * @param productFlavor
     * @param buildType
     */
    public void setBuildVariant(
            @NonNull ProductFlavor mainFlavor,
            @NonNull ProductFlavor productFlavor,
            @NonNull BuildType buildType) {
        mProductFlavor = productFlavor.mergeWith(mainFlavor);
        mBuildType = buildType;
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

    public void generateBuildConfig(
            @NonNull String manifestLocation,
            @NonNull String outGenLocation,
            @Nullable List<String> additionalLines) throws IOException {
        if (mProductFlavor == null || mBuildType == null) {
            throw new IllegalArgumentException("No Product Flavor or Build Type set.");
        }
        if (mTarget == null) {
            throw new IllegalArgumentException("Target not set.");
        }

        String packageName = getPackageOverride(manifestLocation);
        if (packageName == null) {
            packageName = getPackageFromManifest(manifestLocation);
        }

        BuildConfigGenerator generator = new BuildConfigGenerator(
                outGenLocation, packageName, mBuildType.isDebuggable());
        generator.generate(additionalLines);
    }

    public void preprocessResources(
            @NonNull String mainResLocation,
            @Nullable String flavorResLocation,
            @Nullable String typeResLocation,
            @NonNull String outResLocation) throws IOException, InterruptedException {
        if (mProductFlavor == null || mBuildType == null) {
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

        if (typeResLocation != null) {
            command.add("-S");
            command.add(typeResLocation);
        }

        if (flavorResLocation != null) {
            command.add("-S");
            command.add(flavorResLocation);
        }

        command.add("-S");
        command.add(mainResLocation);

        command.add("-C");
        command.add(outResLocation);

        mCmdLineRunner.runCmdLine(command);
    }

    public void mergeManifest(
            @NonNull String mainLocation,
            @Nullable String flavorLocation,
            @Nullable String typeLocation,
            @NonNull String outManifestLocation) {
        if (mProductFlavor == null || mBuildType == null) {
            throw new IllegalArgumentException("No Product Flavor or Build Type set.");
        }
        if (mTarget == null) {
            throw new IllegalArgumentException("Target not set.");
        }

        try {
            if (flavorLocation == null && typeLocation == null &&
                    mFlatLibraryProjects.size() == 0) {
                new FileOp().copyFile(new File(mainLocation), new File(outManifestLocation));
            } else {
                File appMergeOut;
                if (mFlatLibraryProjects.size() == 0) {
                    appMergeOut = new File(outManifestLocation);
                } else {
                    appMergeOut = File.createTempFile("manifestMerge", ".xml");
                    appMergeOut.deleteOnExit();
                }

                List<File> manifests = new ArrayList<File>();
                if (typeLocation != null) {
                    manifests.add(new File(typeLocation));
                }
                if (flavorLocation != null) {
                    manifests.add(new File(flavorLocation));
                }

                ManifestMerger merger = new ManifestMerger(MergerLog.wrapSdkLog(mLogger));
                if (merger.process(
                        appMergeOut,
                        new File(mainLocation),
                        manifests.toArray(new File[manifests.size()])) == false) {
                    throw new RuntimeException();
                }

                if (mFlatLibraryProjects.size() > 0) {
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
            @NonNull String manifestLocation,
            @NonNull String mainResLocation,
            @Nullable String flavorResLocation,
            @Nullable String typeResLocation,
            @Nullable String crunchedResLocation,
            @NonNull String mainAssetsLocation,
            @Nullable String flavorAssetsLocation,
            @Nullable String typeAssetsLocation,
            @Nullable String outGenLocation,
            @Nullable String outResPackageLocation,
            @NonNull String outProguardLocation,
            @NonNull AaptOptions options) throws IOException, InterruptedException {
        if (mProductFlavor == null || mBuildType == null) {
            throw new IllegalArgumentException("No Product Flavor or Build Type set.");
        }
        if (mTarget == null) {
            throw new IllegalArgumentException("Target not set.");
        }

        // if both output types are empty, then there's nothing to do and this is an error
        if (outGenLocation == null && outResPackageLocation == null) {
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
        command.add(manifestLocation);

        // TODO: handle libraries!
        boolean useOverlay =  false;
        if (crunchedResLocation != null) {
            command.add("-S");
            command.add(crunchedResLocation);
            useOverlay = true;
        }

        if (typeResLocation != null) {
            command.add("-S");
            command.add(typeResLocation);
            useOverlay = true;
        }

        if (flavorResLocation != null) {
            command.add("-S");
            command.add(flavorResLocation);
            useOverlay = true;
        }

        command.add("-S");
        command.add(mainResLocation);

        if (useOverlay) {
            command.add("--auto-add-overlay");
        }

        if (typeAssetsLocation != null) {
            command.add("-A");
            command.add(typeAssetsLocation);
        }

        if (flavorAssetsLocation != null) {
            command.add("-A");
            command.add(flavorAssetsLocation);
        }

        command.add("-A");
        command.add(mainAssetsLocation);

        // outputs

        if (outGenLocation != null) {
            command.add("-m");
            command.add("-J");
            command.add(outGenLocation);
        }

        if (outResPackageLocation != null) {
            command.add("-F");
            command.add(outResPackageLocation);

            command.add("-G");
            command.add(outProguardLocation);
        }

        // options controlled by build variants

        if (mBuildType.isDebuggable()) {
            command.add("--debug-mode");
        }

        String packageOverride = getPackageOverride(manifestLocation);
        if (packageOverride != null) {
            command.add("--rename-manifest-package");
            command.add(packageOverride);
            mLogger.verbose("Inserting package '%s' in AndroidManifest.xml", packageOverride);
        }

        boolean forceErrorOnReplace = false;

        int versionCode = mProductFlavor.getVersionCode();
        if (versionCode != -1) {
            command.add("--version-code");
            command.add(Integer.toString(versionCode));
            mLogger.verbose("Inserting versionCode '%d' in AndroidManifest.xml", versionCode);
            forceErrorOnReplace = true;
        }

        String versionName = mProductFlavor.getVersionName();
        if (versionName != null) {
            command.add("--version-name");
            command.add(versionName);
            mLogger.verbose("Inserting versionName '%s' in AndroidManifest.xml", versionName);
            forceErrorOnReplace = true;
        }

        int minSdkVersion = mProductFlavor.getMinSdkVersion();
        if (minSdkVersion != -1) {
            command.add("--min-sdk-version");
            command.add(Integer.toString(minSdkVersion));
            mLogger.verbose("Inserting minSdkVersion '%d' in AndroidManifest.xml", minSdkVersion);
            forceErrorOnReplace = true;
        }

        int targetSdkVersion = mProductFlavor.getTargetSdkVersion();
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
            @NonNull String classesLocation,
            @NonNull String outDexFile,
            @NonNull DexOptions dexOptions) throws IOException, InterruptedException {
        if (mProductFlavor == null || mBuildType == null) {
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

        if (mVerboseExec) {
            command.add("-v");
        }

        command.add("--output");
        command.add(outDexFile);

        // TODO: handle dependencies

        mLogger.verbose("Input: " + classesLocation);

        command.add(classesLocation);

        mCmdLineRunner.runCmdLine(command);
    }

    /**
     * Packages the apk.
     * @param androidResPkgLocation
     * @param classesDexLocation
     * @param mainJavaResLocation
     * @param flavorJavaResLocation
     * @param buildTypeJavaResLocation
     * @param jniLibsLocation
     * @param outApkLocation
     */
    public void packageApk(
            @NonNull String androidResPkgLocation,
            @NonNull String classesDexLocation,
            @NonNull String mainJavaResLocation,
            @Nullable String flavorJavaResLocation,
            @Nullable String buildTypeJavaResLocation,
            @NonNull String jniLibsLocation,
            @NonNull String outApkLocation) {
        if (mProductFlavor == null || mBuildType == null) {
            throw new IllegalArgumentException("No Product Flavor or Build Type set.");
        }
        if (mTarget == null) {
            throw new IllegalArgumentException("Target not set.");
        }

        SigningInfo signingInfo = null;
        if (mBuildType.isDebugSigningKey()) {
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

            packager.setDebugJniMode(mBuildType.isDebugJniBuild());

            // figure out conflicts!
            JavaResourceProcessor resProcessor = new JavaResourceProcessor(packager);
            resProcessor.addSourceFolder(buildTypeJavaResLocation);
            resProcessor.addSourceFolder(flavorJavaResLocation);
            resProcessor.addSourceFolder(mainJavaResLocation);

            // also add resources from library projects and jars

            packager.addNativeLibraries(jniLibsLocation);

            packager.sealApk();
        } catch (PackagerException e) {
            throw new RuntimeException(e);
        } catch (DuplicateFileException e) {
            throw new RuntimeException(e);
        } catch (SealedPackageException e) {
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting
    String getPackageOverride(@NonNull String manifestLocation) {
        String packageName = mProductFlavor.getPackageName();
        String packageSuffix = mBuildType.getPackageNameSuffix();

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

}

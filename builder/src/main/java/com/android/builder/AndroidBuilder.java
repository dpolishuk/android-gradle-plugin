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
import com.android.builder.internal.BuildConfigGenerator;
import com.android.builder.internal.CommandLineRunner;
import com.android.builder.internal.SymbolLoader;
import com.android.builder.internal.SymbolWriter;
import com.android.builder.internal.TestManifestGenerator;
import com.android.builder.internal.compiler.AidlProcessor;
import com.android.builder.internal.compiler.SourceGenerator;
import com.android.builder.internal.packaging.JavaResourceProcessor;
import com.android.builder.internal.packaging.Packager;
import com.android.builder.internal.signing.DebugKeyHelper;
import com.android.builder.internal.signing.KeystoreHelper;
import com.android.builder.internal.signing.KeytoolException;
import com.android.builder.internal.signing.SigningInfo;
import com.android.builder.packaging.DuplicateFileException;
import com.android.builder.packaging.PackagerException;
import com.android.builder.packaging.SealedPackageException;
import com.android.manifmerger.ManifestMerger;
import com.android.manifmerger.MergerLog;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.IAndroidTarget.IOptionalLibrary;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * This is the main builder class. It is given all the data to process the build (such as
 * {@link ProductFlavor}s, {@link BuildType} and dependencies) and use them when doing specific
 * build steps.
 *
 * To use:
 * create a builder with {@link #AndroidBuilder(SdkParser, ILogger, boolean)},
 * configure compile target with {@link #setTarget(String)}
 *
 * then build steps can be done with
 * {@link #generateBuildConfig(String, boolean, java.util.List, String)}
 * {@link #processManifest(java.io.File, java.util.List, java.util.List, int, String, int, int, String)}
 * {@link #processTestManifest(String, int, String, String, java.util.List, String)}
 * {@link #processResources(java.io.File, java.io.File, Iterable, java.io.File, java.util.List, String, String, String, String, String, com.android.builder.VariantConfiguration.Type, boolean, AaptOptions)}
 * {@link #compileAidl(java.util.List, java.io.File, java.util.List)}
 * {@link #convertByteCode(Iterable, Iterable, String, DexOptions)}
 * {@link #packageApk(String, String, java.util.List, String, String, boolean, boolean, String, String, String, String, String)}
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

    /**
     * Creates an AndroidBuilder
     * <p/>
     * This receives an {@link SdkParser} to provide the build with information about the SDK, as
     * well as an {@link ILogger} to display output.
     * <p/>
     * <var>verboseExec</var> is needed on top of the ILogger due to remote exec tools not being
     * able to output info and verbose messages separately.
     *
     * @param sdkParser the SdkParser
     * @param logger the Logger
     * @param verboseExec whether external tools are launched in verbose mode
     */
    public AndroidBuilder(
            @NonNull SdkParser sdkParser,
            @NonNull ILogger logger,
            boolean verboseExec) {
        mSdkParser = checkNotNull(sdkParser);
        mLogger = checkNotNull(logger);
        mVerboseExec = verboseExec;
        mCmdLineRunner = new CommandLineRunner(mLogger);
    }

    @VisibleForTesting
    AndroidBuilder(
            @NonNull SdkParser sdkParser,
            @NonNull CommandLineRunner cmdLineRunner,
            @NonNull ILogger logger,
            boolean verboseExec) {
        mSdkParser = checkNotNull(sdkParser);
        mCmdLineRunner = checkNotNull(cmdLineRunner);
        mLogger = checkNotNull(logger);
        mVerboseExec = verboseExec;
    }

    /**
     * Sets the compilation target hash string.
     *
     * @param target the compilation target
     *
     * @see IAndroidTarget#hashString()
     */
    public void setTarget(@NonNull String target) {
        checkNotNull(target, "target cannot be null.");

        mTarget = mSdkParser.resolveTarget(target, mLogger);

        if (mTarget == null) {
            throw new RuntimeException("Unknown target: " + target);
        }
    }

    public int getTargetApiLevel() {
        checkState(mTarget != null, "Target not set.");

        return mTarget.getVersion().getApiLevel();
    }

    /**
     * Returns the runtime classpath to be used during compilation.
     */
    public List<String> getRuntimeClasspath() {
        checkState(mTarget != null, "Target not set.");

        List<String> classpath = Lists.newArrayList();

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
     * @param packageName the package in which to generate the class
     * @param debuggable whether the app is considered debuggable
     * @param javaLines additional java lines to put in the class. These must be valid Java lines.
     * @param sourceOutputDir directory where to put this. This is the source folder, not the
     *                        package folder.
     * @throws IOException
     */
    public void generateBuildConfig(
            @NonNull String packageName,
                     boolean debuggable,
            @NonNull List<String> javaLines,
            @NonNull String sourceOutputDir) throws IOException {
        checkState(mTarget != null, "Target not set.");

        BuildConfigGenerator generator = new BuildConfigGenerator(
                sourceOutputDir, packageName, debuggable);
        generator.generate(javaLines);
    }

    /**
     * Process the images. This optimize the bitmaps and pre-processes the 9-patch files before
     * they can be packaged.
     * This is incremental.
     *
     * @param resOutputDir where the processed resources are stored.
     * @param inputs the input res folders
     * @throws IOException
     * @throws InterruptedException
     */
    public void processImages(@NonNull String resOutputDir, List<File> inputs)
            throws IOException, InterruptedException {
        checkState(mTarget != null, "Target not set.");
        checkNotNull(resOutputDir, "resOutputDir cannot be null.");

        if (inputs == null || inputs.isEmpty()) {
            return;
        }

        // launch aapt: create the command line
        ArrayList<String> command = Lists.newArrayList();

        @SuppressWarnings("deprecation")
        String aaptPath = mTarget.getPath(IAndroidTarget.AAPT);

        command.add(aaptPath);
        command.add("crunch");

        if (mVerboseExec) {
            command.add("-v");
        }

        boolean runCommand = false;
        for (File input : inputs) {
            if (input.isDirectory()) {
                command.add("-S");
                command.add(input.getAbsolutePath());
                runCommand = true;
            }
        }

        if (!runCommand) {
            return;
        }

        command.add("-C");
        command.add(resOutputDir);

        mLogger.info("processImages command: %s", command.toString());

        mCmdLineRunner.runCmdLine(command);
    }

    /**
     * Merges all the manifests into a single manifest
     *
     * @param mainManifest The main manifest of the application.
     * @param manifestOverlays manifest overlays coming from flavors and build types
     * @param libraries the library dependency graph
     * @param versionCode a version code to inject in the manifest or -1 to do nothing.
     * @param versionName a version name to inject in the manifest or null to do nothing.
     * @param minSdkVersion a minSdkVersion to inject in the manifest or -1 to do nothing.
     * @param targetSdkVersion a targetSdkVersion to inject in the manifest or -1 to do nothing.
     * @param outManifestLocation the output location for the merged manifest
     *
     * @see com.android.builder.VariantConfiguration#getMainManifest()
     * @see com.android.builder.VariantConfiguration#getManifestOverlays()
     * @see com.android.builder.VariantConfiguration#getDirectLibraries()
     * @see com.android.builder.VariantConfiguration#getMergedFlavor()
     * @see com.android.builder.ProductFlavor#getVersionCode()
     * @see com.android.builder.ProductFlavor#getVersionName()
     * @see com.android.builder.ProductFlavor#getMinSdkVersion()
     * @see com.android.builder.ProductFlavor#getTargetSdkVersion()
     */
    public void processManifest(
            @NonNull File mainManifest,
            @NonNull List<File> manifestOverlays,
            @NonNull List<ManifestDependency> libraries,
                     int versionCode,
                     String versionName,
                     int minSdkVersion,
                     int targetSdkVersion,
            @NonNull String outManifestLocation) {
        checkState(mTarget != null, "Target not set.");
        checkNotNull(mainManifest, "mainManifest cannot be null.");
        checkNotNull(manifestOverlays, "manifestOverlays cannot be null.");
        checkNotNull(libraries, "libraries cannot be null.");
        checkNotNull(outManifestLocation, "outManifestLocation cannot be null.");

        try {
            Map<String, String> attributeInjection = getAttributeInjectionMap(
                    versionCode, versionName, minSdkVersion, targetSdkVersion);

            if (manifestOverlays.isEmpty() && libraries.isEmpty()) {
                // if no manifest to merge, just copy to location, unless we have to inject
                // attributes
                if (attributeInjection.isEmpty()) {
                    Files.copy(mainManifest, new File(outManifestLocation));
                } else {
                    ManifestMerger merger = new ManifestMerger(MergerLog.wrapSdkLog(mLogger), null);
                    if (!merger.process(
                            new File(outManifestLocation),
                            mainManifest,
                            new File[0],
                            attributeInjection)) {
                        throw new RuntimeException();
                    }
                }
            } else {
                File outManifest = new File(outManifestLocation);

                // first merge the app manifest.
                if (!manifestOverlays.isEmpty()) {
                    File mainManifestOut = outManifest;

                    // if there is also libraries, put this in a temp file.
                    if (!libraries.isEmpty()) {
                        // TODO find better way of storing intermediary file?
                        mainManifestOut = File.createTempFile("manifestMerge", ".xml");
                        mainManifestOut.deleteOnExit();
                    }

                    ManifestMerger merger = new ManifestMerger(MergerLog.wrapSdkLog(mLogger), null);
                    if (!merger.process(
                            mainManifestOut,
                            mainManifest,
                            manifestOverlays.toArray(new File[manifestOverlays.size()]),
                            attributeInjection)) {
                        throw new RuntimeException();
                    }

                    // now the main manifest is the newly merged one
                    mainManifest = mainManifestOut;
                    // and the attributes have been inject, no need to do it below
                    attributeInjection = null;
                }

                if (!libraries.isEmpty()) {
                    // recursively merge all manifests starting with the leaves and up toward the
                    // root (the app)
                    mergeLibraryManifests(mainManifest, libraries,
                            new File(outManifestLocation), attributeInjection);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }    }

    /**
     * Creates the manifest for a test variant
     *
     * @param testPackageName the package name of the test application
     * @param minSdkVersion the minSdkVersion of the test application
     * @param testedPackageName the package name of the tested application
     * @param instrumentationRunner the name of the instrumentation runner
     * @param libraries the library dependency graph
     * @param outManifestLocation the output location for the merged manifest
     *
     * @see com.android.builder.VariantConfiguration#getPackageName()
     * @see com.android.builder.VariantConfiguration#getTestedConfig()
     * @see com.android.builder.VariantConfiguration#getMinSdkVersion()
     * @see com.android.builder.VariantConfiguration#getTestedPackageName()
     * @see com.android.builder.VariantConfiguration#getInstrumentationRunner()
     * @see com.android.builder.VariantConfiguration#getDirectLibraries()
     */
    public void processTestManifest(
            @NonNull String testPackageName,
                     int minSdkVersion,
            @NonNull String testedPackageName,
            @NonNull String instrumentationRunner,
            @NonNull List<ManifestDependency> libraries,
            @NonNull String outManifestLocation) {
        checkState(mTarget != null, "Target not set.");
        checkNotNull(testPackageName, "testPackageName cannot be null.");
        checkNotNull(testedPackageName, "testedPackageName cannot be null.");
        checkNotNull(instrumentationRunner, "instrumentationRunner cannot be null.");
        checkNotNull(libraries, "libraries cannot be null.");
        checkNotNull(outManifestLocation, "outManifestLocation cannot be null.");

        if (!libraries.isEmpty()) {
            try {
                // create the test manifest, merge the libraries in it
                File generatedTestManifest = File.createTempFile("manifestMerge", ".xml");

                generateTestManifest(
                        testPackageName,
                        minSdkVersion,
                        testedPackageName,
                        instrumentationRunner,
                        generatedTestManifest.getAbsolutePath());

                mergeLibraryManifests(
                        generatedTestManifest,
                        libraries,
                        new File(outManifestLocation),
                        null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            generateTestManifest(
                    testPackageName,
                    minSdkVersion,
                    testedPackageName,
                    instrumentationRunner,
                    outManifestLocation);
        }
    }

    private void generateTestManifest(
            String testPackageName,
            int minSdkVersion,
            String testedPackageName,
            String instrumentationRunner,
            String outManifestLocation) {
        TestManifestGenerator generator = new TestManifestGenerator(
                outManifestLocation,
                testPackageName,
                minSdkVersion,
                testedPackageName,
                instrumentationRunner);
        try {
            generator.generate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> getAttributeInjectionMap(
            int versionCode,
            String versionName,
            int minSdkVersion,
            int targetSdkVersion) {

        Map<String, String> attributeInjection = Maps.newHashMap();

        if (versionCode != -1) {
            attributeInjection.put(
                    "/manifest|http://schemas.android.com/apk/res/android versionCode",
                    Integer.toString(versionCode));
        }

        if (versionName != null) {
            attributeInjection.put(
                    "/manifest|http://schemas.android.com/apk/res/android versionName",
                    versionName);
        }

        if (minSdkVersion != -1) {
            attributeInjection.put(
                    "/manifest/uses-sdk|http://schemas.android.com/apk/res/android minSdkVersion",
                    Integer.toString(minSdkVersion));
        }

        if (targetSdkVersion != -1) {
            attributeInjection.put(
                    "/manifest/uses-sdk|http://schemas.android.com/apk/res/android targetSdkVersion",
                    Integer.toString(targetSdkVersion));
        }
        return attributeInjection;
    }

    /**
     * Merges library manifests into a main manifest.
     * @param mainManifest the main manifest
     * @param directLibraries the libraries to merge
     * @param outManifest the output file
     * @throws IOException
     */
    private void mergeLibraryManifests(
            File mainManifest,
            Iterable<ManifestDependency> directLibraries,
            File outManifest, Map<String, String> attributeInjection) throws IOException {

        List<File> manifests = Lists.newArrayList();
        for (ManifestDependency library : directLibraries) {
            List<ManifestDependency> subLibraries = library.getManifestDependencies();
            if (subLibraries == null || subLibraries.size() == 0) {
                manifests.add(library.getManifest());
            } else {
                File mergeLibManifest = File.createTempFile("manifestMerge", ".xml");
                mergeLibManifest.deleteOnExit();

                // don't insert the attribute injection into libraries
                mergeLibraryManifests(
                        library.getManifest(), subLibraries, mergeLibManifest, null);

                manifests.add(mergeLibManifest);
            }
        }

        ManifestMerger merger = new ManifestMerger(MergerLog.wrapSdkLog(mLogger), null);
        if (!merger.process(
                outManifest,
                mainManifest,
                manifests.toArray(new File[manifests.size()]), attributeInjection)) {
            throw new RuntimeException();
        }
    }

    /**
     * Process the resources and generate R.java and/or the packaged resources.
     *
     * TODO support 2+ assets folders.
     *
     * @param manifestFile the location of the manifest file
     * @param preprocessResDir the pre-processed folder
     * @param resInputs the res folder inputs
     * @param assetsDir the main asset folder
     * @param libraries the flat list of libraries
     * @param sourceOutputDir optional source folder to generate R.java
     * @param resPackageOutput optional filepath for packaged resources
     * @param proguardOutput optional filepath for proguard file to generate
     * @param type the type of the variant being built
     * @param debuggable whether the app is debuggable
     * @param options the {@link AaptOptions}
     *
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public void processResources(
            @NonNull  File manifestFile,
            @Nullable File preprocessResDir,
            @NonNull  Iterable<File> resInputs,
            @Nullable File assetsDir,
            @NonNull  List<SymbolFileProvider> libraries,
            @Nullable String packageOverride,
            @Nullable String sourceOutputDir,
            @Nullable String symbolOutputDir,
            @Nullable String resPackageOutput,
            @Nullable String proguardOutput,
                      VariantConfiguration.Type type,
                      boolean debuggable,
            @NonNull  AaptOptions options)
            throws IOException, InterruptedException {

        checkState(mTarget != null, "Target not set.");
        checkNotNull(manifestFile, "manifestFile cannot be null.");
        checkNotNull(resInputs, "resInputs cannot be null.");
        checkNotNull(libraries, "libraries cannot be null.");
        checkNotNull(options, "options cannot be null.");
        // if both output types are empty, then there's nothing to do and this is an error
        checkArgument(sourceOutputDir != null || resPackageOutput != null,
                "No output provided for aapt task");

        // launch aapt: create the command line
        ArrayList<String> command = Lists.newArrayList();

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
        command.add(manifestFile.getAbsolutePath());

        if (preprocessResDir != null && preprocessResDir.isDirectory()) {
            command.add("-S");
            command.add(preprocessResDir.getAbsolutePath());
        }

        for (File resFolder : resInputs) {
            if (resFolder.isDirectory()) {
                command.add("-S");
                command.add(resFolder.getAbsolutePath());
            }
        }

        command.add("--auto-add-overlay");


//        if (typeAssetsLocation != null) {
//            command.add("-A");
//            command.add(typeAssetsLocation);
//        }
//
//        if (flavorAssetsLocation != null) {
//            command.add("-A");
//            command.add(flavorAssetsLocation);
//        }

        if (assetsDir != null && assetsDir.isDirectory()) {
            command.add("-A");
            command.add(assetsDir.getAbsolutePath());
        }

        // outputs

        if (sourceOutputDir != null) {
            command.add("-m");
            command.add("-J");
            command.add(sourceOutputDir);
        }

        if (type != VariantConfiguration.Type.LIBRARY && resPackageOutput != null) {
            command.add("-F");
            command.add(resPackageOutput);

            if (proguardOutput != null) {
                command.add("-G");
                command.add(proguardOutput);
            }
        }

        // options controlled by build variants

        if (debuggable) {
            command.add("--debug-mode");
        }

        if (type == VariantConfiguration.Type.DEFAULT) {
            if (packageOverride != null) {
                command.add("--rename-manifest-package");
                command.add(packageOverride);
                mLogger.verbose("Inserting package '%s' in AndroidManifest.xml", packageOverride);
            }
        }

        // library specific options
        if (type == VariantConfiguration.Type.LIBRARY) {
            command.add("--non-constant-id");
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

        if (symbolOutputDir != null &&
                (type == VariantConfiguration.Type.LIBRARY || !libraries.isEmpty())) {
            command.add("--output-text-symbols");
            command.add(symbolOutputDir);
        }

        mLogger.info("aapt command: %s", command.toString());

        mCmdLineRunner.runCmdLine(command);

        // now if the project has libraries, R needs to be created for each libraries,
        // but only if the current project is not a library.
        if (type != VariantConfiguration.Type.LIBRARY && !libraries.isEmpty()) {
            SymbolLoader symbolValues = null;

            for (SymbolFileProvider lib : libraries) {
                File rFile = lib.getSymbolFile();
                // if the library has no resource, this file won't exist.
                if (rFile.isFile()) {
                    // load the values if that's not already been done.
                    // Doing it lazily allow us to support the case where there's no
                    // resources anywhere.
                    if (symbolValues == null) {
                        symbolValues = new SymbolLoader(new File(symbolOutputDir, "R.txt"),
                                mLogger);
                        symbolValues.load();
                    }

                    SymbolLoader symbols = new SymbolLoader(rFile, mLogger);
                    symbols.load();

                    String packageName = VariantConfiguration.sManifestParser.getPackage(
                            lib.getManifest());

                    SymbolWriter writer = new SymbolWriter(sourceOutputDir, packageName,
                            symbols, symbolValues);
                    writer.write();
                }
            }
        }
    }

    /**
     * Compiles all the aidl files.
     *
     * @param sourceFolders all the source folders to find files to compile
     * @param sourceOutputDir the output dir in which to generate the source code
     * @param importFolders import folders
     * @throws IOException
     * @throws InterruptedException
     */
    public void compileAidl(@NonNull List<File> sourceFolders,
                            @NonNull File sourceOutputDir,
                            @NonNull List<File> importFolders)
            throws IOException, InterruptedException {
        checkState(mTarget != null, "Target not set.");
        checkNotNull(sourceFolders, "sourceFolders cannot be null.");
        checkNotNull(sourceOutputDir, "sourceOutputDir cannot be null.");
        checkNotNull(importFolders, "importFolders cannot be null.");

        SourceGenerator compiler = new SourceGenerator(mLogger);

        @SuppressWarnings("deprecation")
        String aidlPath = mTarget.getPath(IAndroidTarget.AIDL);

        AidlProcessor processor = new AidlProcessor(
                aidlPath,
                mTarget.getPath(IAndroidTarget.ANDROID_AIDL),
                importFolders,
                mCmdLineRunner);

        compiler.processFiles(processor, sourceFolders, sourceOutputDir);
    }

    /**
     * Converts the bytecode to Dalvik format
     * @param classesLocation the location of the compiler output
     * @param libraries the list of libraries
     * @param outDexFile the location of the output classes.dex file
     * @param dexOptions dex options
     * @throws IOException
     * @throws InterruptedException
     */
    public void convertByteCode(
            @NonNull Iterable<File> classesLocation,
            @NonNull Iterable<File> libraries,
            @NonNull String outDexFile,
            @NonNull DexOptions dexOptions) throws IOException, InterruptedException {
        checkState(mTarget != null, "Target not set.");
        checkNotNull(classesLocation, "classesLocation cannot be null.");
        checkNotNull(libraries, "libraries cannot be null.");
        checkNotNull(outDexFile, "outDexFile cannot be null.");
        checkNotNull(dexOptions, "dexOptions cannot be null.");

        // launch dx: create the command line
        ArrayList<String> command = Lists.newArrayList();

        @SuppressWarnings("deprecation")
        String dxPath = mTarget.getPath(IAndroidTarget.DX);
        command.add(dxPath);

        command.add("--dex");

        if (mVerboseExec) {
            command.add("--verbose");
        }

        if (dexOptions.isCoreLibrary()) {
            command.add("--core-library");
        }

        command.add("--output");
        command.add(outDexFile);

        List<String> classesList = Lists.newArrayList();
        for (File f : classesLocation) {
            if (f != null && f.exists()) {
                classesList.add(f.getAbsolutePath());
            }
        }

        List<String> libraryList = Lists.newArrayList();
        for (File f : libraries) {
            if (f != null && f.exists()) {
                libraryList.add(f.getAbsolutePath());
            }
        }


        mLogger.info("dx command: %s", command.toString());

        mLogger.verbose("Dex class inputs: " + classesList);

        command.addAll(classesList);

        mLogger.verbose("Dex library inputs: " + libraryList);

        command.addAll(libraryList);

        mCmdLineRunner.runCmdLine(command);
    }

    /**
     * Packages the apk.
     *
     * @param androidResPkgLocation the location of the packaged resource file
     * @param classesDexLocation the location of the classes.dex file
     * @param packagedJars the jars that are packaged (libraries + jar dependencies)
     * @param javaResourcesLocation the processed Java resource folder
     * @param jniLibsLocation the location of the compiled JNI libraries
     * @param debugSigned whether the app is signed with the debug key
     * @param debugJni whether the app should include jni debug data
     * @param signingStoreLocation signing store location (if not debug signed)
     * @param signingStorePassword signing store password (if not debug signed)
     * @param signingKeyAlias signing key alias (if not debug signed)
     * @param signingKeyPassword signing key password (if not debug signed)
     * @param outApkLocation location of the APK.
     * @throws DuplicateFileException
     *
     * @see com.android.builder.VariantConfiguration#getPackagedJars()
     */
    public void packageApk(
            @NonNull String androidResPkgLocation,
            @NonNull String classesDexLocation,
            @NonNull List<File> packagedJars,
            @Nullable String javaResourcesLocation,
            @Nullable String jniLibsLocation,
            boolean debugSigned,
            boolean debugJni,
            @Nullable String signingStoreLocation,
            @Nullable String signingStorePassword,
            @Nullable String signingKeyAlias,
            @Nullable String signingKeyPassword,
            @NonNull String outApkLocation) throws DuplicateFileException {
        checkState(mTarget != null, "Target not set.");
        checkNotNull(androidResPkgLocation, "androidResPkgLocation cannot be null.");
        checkNotNull(classesDexLocation, "classesDexLocation cannot be null.");
        checkNotNull(outApkLocation, "outApkLocation cannot be null.");

        SigningInfo signingInfo = null;
        try {
            if (debugSigned) {
                String storeLocation = DebugKeyHelper.defaultDebugKeyStoreLocation();
                File storeFile = new File(storeLocation);
                if (storeFile.isDirectory()) {
                    throw new RuntimeException(
                            String.format("A folder is in the way of the debug keystore: %s",
                                    storeLocation));
                } else if (!storeFile.exists()) {
                    if (!DebugKeyHelper.createNewStore(
                            storeLocation, null /*storeType*/, mLogger)) {
                        throw new RuntimeException();
                    }
                }

                // load the key
                signingInfo = DebugKeyHelper.getDebugKey(storeLocation, null /*storeStype*/);
            } else if (signingStoreLocation != null &&
                    signingStorePassword != null &&
                    signingKeyAlias != null &&
                    signingKeyPassword != null) {
                signingInfo = KeystoreHelper.getSigningInfo(
                        signingStoreLocation,
                        signingStorePassword,
                        null, /*storeStype*/
                        signingKeyAlias,
                        signingKeyPassword);
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

            packager.setDebugJniMode(debugJni);

            // figure out conflicts!
            JavaResourceProcessor resProcessor = new JavaResourceProcessor(packager);

            if (javaResourcesLocation != null) {
                resProcessor.addSourceFolder(javaResourcesLocation);
            }

            // add the resources from the jar files.
            for (File jar : packagedJars) {
                packager.addResourcesFromJar(jar);
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

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
package com.android.build.gradle

import com.android.SdkConstants
import com.android.build.gradle.internal.AndroidDependencyImpl
import com.android.build.gradle.internal.ApplicationVariant
import com.android.build.gradle.internal.ConfigurationDependencies
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.ProductionAppVariant
import com.android.build.gradle.internal.TestAppVariant
import com.android.builder.AndroidBuilder
import com.android.builder.AndroidDependency
import com.android.builder.DefaultSdkParser
import com.android.builder.JarDependency
import com.android.builder.ProductFlavor
import com.android.builder.SdkParser
import com.android.builder.SourceProvider
import com.android.builder.VariantConfiguration
import com.android.utils.ILogger
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult
import org.gradle.api.internal.plugins.ProcessResources
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.util.GUtil

/**
 * Base class for all Android plugins
 */
abstract class BasePlugin {

    public final static String INSTALL_GROUP = "Install"

    private final Map<Object, AndroidBuilder> builders = [:]

    final List<ApplicationVariant> variants = []
    final Map<AndroidDependencyImpl, PrepareLibraryTask> prepareTaskMap = [:]

    protected Project project
    protected File sdkDir
    private DefaultSdkParser androidSdkParser
    private LoggerWrapper loggerWrapper

    private ProductFlavorData defaultConfigData
    protected AndroidSourceSet mainSourceSet
    protected AndroidSourceSet testSourceSet

    protected Task uninstallAll
    protected Task assembleTest

    abstract String getTarget()

    protected void apply(Project project) {
        this.project = project
        project.apply plugin: JavaBasePlugin

        project.tasks.assemble.description =
            "Assembles all variants of all applications and secondary packages."

        findSdk(project)

        uninstallAll = project.tasks.add("uninstallAll")
        uninstallAll.description = "Uninstall all applications."
        uninstallAll.group = INSTALL_GROUP
    }

    protected setDefaultConfig(ProductFlavor defaultConfig,
                               NamedDomainObjectContainer<AndroidSourceSet> sourceSets) {
        mainSourceSet = sourceSets.create("main")
        testSourceSet = sourceSets.create("test")

        defaultConfigData = new ProductFlavorData(defaultConfig, mainSourceSet,
                testSourceSet, project)
    }

    ProductFlavorData getDefaultConfigData() {
        return defaultConfigData
    }

    SdkParser getSdkParser() {
        if (androidSdkParser == null) {
            androidSdkParser = new DefaultSdkParser(sdkDir.absolutePath)
        }

        return androidSdkParser;
    }

    ILogger getLogger() {
        if (loggerWrapper == null) {
            loggerWrapper = new LoggerWrapper(project.logger)
        }

        return loggerWrapper
    }

    boolean isVerbose() {
        return project.logger.isEnabled(LogLevel.DEBUG)
    }

    AndroidBuilder getAndroidBuilder(ApplicationVariant variant) {
        AndroidBuilder androidBuilder = builders.get(variant)

        if (androidBuilder == null) {
            androidBuilder = variant.createBuilder(this)
            builders.put(variant, androidBuilder)
        }

        return androidBuilder
    }

    private void findSdk(Project project) {
        def rootDir = project.rootDir
        def localProperties = new File(rootDir, SdkConstants.FN_LOCAL_PROPERTIES)
        if (localProperties.exists()) {
            Properties properties = new Properties()
            localProperties.withInputStream { instr ->
                properties.load(instr)
            }
            def sdkDirProp = properties.getProperty('sdk.dir')
            if (!sdkDirProp) {
                throw new RuntimeException("No sdk.dir property defined in local.properties file.")
            }
            sdkDir = new File(sdkDirProp)
        } else {
            def envVar = System.getenv("ANDROID_HOME")
            if (envVar != null) {
                sdkDir = new File(envVar)
            }
        }

        if (sdkDir == null) {
            throw new RuntimeException(
                    "SDK location not found. Define location with sdk.dir in the local.properties file or with an ANDROID_HOME environment variable.")
        }

        if (!sdkDir.directory) {
            throw new RuntimeException(
                    "The SDK directory '$sdkDir' specified in local.properties does not exist.")
        }
    }

    protected String getRuntimeJars(ApplicationVariant variant) {
        AndroidBuilder androidBuilder = getAndroidBuilder(variant)

        return androidBuilder.runtimeClasspath.join(File.pathSeparator)
    }

    protected ProcessManifestTask createProcessManifestTask(ApplicationVariant variant,
                                                        String manifestOurDir) {
        def processManifestTask = project.tasks.add("process${variant.name}Manifest",
                ProcessManifestTask)
        processManifestTask.plugin = this
        processManifestTask.variant = variant
        processManifestTask.configObjects = variant.configObjects
        processManifestTask.conventionMapping.inputManifests = { variant.config.manifestInputs }
        processManifestTask.conventionMapping.processedManifest = {
            project.file(
                    "$project.buildDir/${manifestOurDir}/$variant.dirName/AndroidManifest.xml")
        }

        return processManifestTask
    }

    protected CrunchResourcesTask createCrunchResTask(ApplicationVariant variant) {
        def crunchTask = project.tasks.add("crunch${variant.name}Res", CrunchResourcesTask)
        crunchTask.plugin = this
        crunchTask.variant = variant
        crunchTask.configObjects = variant.configObjects
        crunchTask.conventionMapping.resDirectories = { variant.config.resourceInputs }
        crunchTask.conventionMapping.outputDir = {
            project.file("$project.buildDir/res/$variant.dirName")
        }

        return crunchTask
    }

    protected GenerateBuildConfigTask createBuildConfigTask(ApplicationVariant variant,
                                                            ProcessManifestTask processManifestTask) {
        def generateBuildConfigTask = project.tasks.add(
                "generate${variant.name}BuildConfig", GenerateBuildConfigTask)
        if (processManifestTask != null) {
            // This is in case the manifest is generated
            generateBuildConfigTask.dependsOn processManifestTask
        }
        generateBuildConfigTask.plugin = this
        generateBuildConfigTask.variant = variant
        generateBuildConfigTask.configObjects = variant.configObjects
        generateBuildConfigTask.optionalJavaLines = variant.buildConfigLines
        generateBuildConfigTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/${variant.dirName}")
        }
        return generateBuildConfigTask
    }

    protected ProcessResourcesTask createProcessResTask(ApplicationVariant variant,
                                                    ProcessManifestTask processManifestTask,
                                                    CrunchResourcesTask crunchTask) {
        def processResources = project.tasks.add("process${variant.name}Res", ProcessResourcesTask)
        processResources.dependsOn processManifestTask
        processResources.plugin = this
        processResources.variant = variant
        processResources.configObjects = variant.configObjects
        processResources.conventionMapping.manifestFile = { processManifestTask.processedManifest }
        // TODO: unify with generateBuilderConfig, compileAidl, and library packaging somehow?
        processResources.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/$variant.dirName")
        }
        processResources.conventionMapping.textSymbolDir = {
            project.file("$project.buildDir/symbols/$variant.dirName")
        }
        processResources.conventionMapping.packageFile = {
            project.file(
                    "$project.buildDir/libs/${project.archivesBaseName}-${variant.baseName}.ap_")
        }
        if (variant.runProguard) {
            processResources.conventionMapping.proguardFile = {
                project.file("$project.buildDir/proguard/${variant.dirName}/rules.txt")
            }
        }

        if (crunchTask != null) {
            processResources.dependsOn crunchTask
            processResources.conventionMapping.crunchDir = { crunchTask.outputDir }
            processResources.conventionMapping.resDirectories = { crunchTask.resDirectories }
        } else {
            processResources.conventionMapping.resDirectories = { variant.config.resourceInputs }
        }

        processResources.aaptOptions = extension.aaptOptions
        return processResources
    }

    protected void createProcessJavaResTask(ApplicationVariant variant) {
        VariantConfiguration config = variant.config

        Copy processResources = project.getTasks().add("process${variant.name}JavaRes",
                ProcessResources.class);

        // set the input
        processResources.from(((AndroidSourceSet) config.defaultSourceSet).resources)

        if (config.getType() != VariantConfiguration.Type.TEST) {
            processResources.from(((AndroidSourceSet) config.buildTypeSourceSet).resources)
        }
        if (config.hasFlavors()) {
            for (SourceProvider flavorSourceSet : config.flavorSourceSets) {
                processResources.from(((AndroidSourceSet) flavorSourceSet).resources)
            }
        }

        processResources.conventionMapping.destinationDir = {
            project.file("$project.buildDir/javaResources/$variant.dirName")
        }

        variant.processJavaResources = processResources
    }

    protected CompileAidlTask createAidlTask(ApplicationVariant variant) {

        VariantConfiguration config = variant.config

        def compileTask = project.tasks.add("compile${variant.name}Aidl", CompileAidlTask)
        compileTask.plugin = this
        compileTask.variant = variant
        compileTask.configObjects = variant.configObjects

        List<Object> sourceList = new ArrayList<Object>();
        sourceList.add(config.defaultSourceSet.aidlDir)
        if (config.getType() != VariantConfiguration.Type.TEST) {
            sourceList.add(config.buildTypeSourceSet.aidlDir)
        }
        if (config.hasFlavors()) {
            for (SourceProvider flavorSourceSet : config.flavorSourceSets) {
                sourceList.add(flavorSourceSet.aidlDir)
            }
        }

        compileTask.sourceDirs = sourceList
        compileTask.importDirs = variant.config.aidlImports

        compileTask.conventionMapping.sourceOutputDir = {
            project.file("$project.buildDir/source/$variant.dirName")
        }

        return compileTask
    }

    protected void createCompileTask(ApplicationVariant variant,
                                     ApplicationVariant testedVariant,
                                     ProcessResourcesTask processResources,
                                     GenerateBuildConfigTask generateBuildConfigTask,
                                     CompileAidlTask aidlTask) {
        def compileTask = project.tasks.add("compile${variant.name}", JavaCompile)
        compileTask.dependsOn processResources, generateBuildConfigTask, aidlTask

        VariantConfiguration config = variant.config

        List<Object> sourceList = new ArrayList<Object>();
        sourceList.add(((AndroidSourceSet) config.defaultSourceSet).java)
        sourceList.add({ processResources.sourceOutputDir })
        if (config.getType() != VariantConfiguration.Type.TEST) {
            sourceList.add(((AndroidSourceSet) config.buildTypeSourceSet).java)
        }
        if (config.hasFlavors()) {
            for (SourceProvider flavorSourceSet : config.flavorSourceSets) {
                sourceList.add(((AndroidSourceSet) flavorSourceSet).java)
            }
        }
        compileTask.source = sourceList.toArray()

        if (testedVariant != null) {
            compileTask.classpath = project.files({config.compileClasspath}) + testedVariant.compileTask.classpath + testedVariant.compileTask.outputs.files
        } else {
            compileTask.classpath = project.files({config.compileClasspath})
        }

        // TODO - dependency information for the compile classpath is being lost.
        // Add a temporary approximation
        compileTask.dependsOn project.configurations.compile.buildDependencies

        compileTask.conventionMapping.destinationDir = {
            project.file("$project.buildDir/classes/$variant.dirName")
        }
        compileTask.conventionMapping.dependencyCacheDir = {
            project.file("$project.buildDir/dependency-cache/$variant.dirName")
        }

        // set source/target compatibility
        // TODO: fix?
        JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention.class);

        compileTask.conventionMapping.sourceCompatibility = {
            convention.sourceCompatibility.toString()
        }
        compileTask.conventionMapping.targetCompatibility = {
            convention.targetCompatibility.toString()
        }

        // setup the bootclasspath just before the task actually runs since this will
        // force the sdk to be parsed.
        compileTask.doFirst {
            compileTask.options.bootClasspath = getRuntimeJars(variant)
        }

        // Wire up the outputs
        variant.resourcePackage = project.files({processResources.packageFile}) {
            builtBy processResources
        }
        variant.compileTask = compileTask
    }

    protected void createTestTasks(TestAppVariant variant, ProductionAppVariant testedVariant,
                                   List<ConfigurationDependencies> configDependencies) {

        def prepareDependenciesTask = createPrepareDependenciesTask(variant, configDependencies)

        // Add a task to process the manifest
        def processManifestTask = createProcessManifestTask(variant, "manifests")
        // TODO - move this
        processManifestTask.dependsOn prepareDependenciesTask

        // Add a task to crunch resource files
        def crunchTask = createCrunchResTask(variant)

        if (testedVariant.config.type == VariantConfiguration.Type.LIBRARY) {
            // in this case the tested library must be fully built before test can be built!
            if (testedVariant.assembleTask != null) {
                processManifestTask.dependsOn testedVariant.assembleTask
                crunchTask.dependsOn testedVariant.assembleTask
            }
        }

        // Add a task to create the BuildConfig class
        def generateBuildConfigTask = createBuildConfigTask(variant, processManifestTask)

        // Add a task to generate resource source files
        def processResources = createProcessResTask(variant, processManifestTask, crunchTask)

        // process java resources
        createProcessJavaResTask(variant)

        def compileAidl = createAidlTask(variant)
        // TODO - move this
        compileAidl.dependsOn prepareDependenciesTask

        // Add a task to compile the test application
        createCompileTask(variant, testedVariant, processResources, generateBuildConfigTask,
                compileAidl)

        addPackageTasks(variant, null)

        if (assembleTest != null) {
            assembleTest.dependsOn variant.assembleTask
        }

        // create the check task for this test
        def checkTask = project.tasks.add("check${testedVariant.name}", DefaultTask)
        checkTask.description = "Installs and runs the checks for Build ${testedVariant.name}."
        checkTask.group = JavaBasePlugin.VERIFICATION_GROUP

        checkTask.dependsOn testedVariant.assembleTask, variant.assembleTask
        project.tasks.check.dependsOn checkTask

        // now run the test.
        def runTestsTask = project.tasks.add("run${testedVariant.name}Tests", RunTestsTask)
        runTestsTask.description = "Runs the checks for Build ${testedVariant.name}. Must be installed on device."
        runTestsTask.group = JavaBasePlugin.VERIFICATION_GROUP
        runTestsTask.sdkDir = sdkDir
        runTestsTask.variant = variant
        checkTask.doLast { runTestsTask }

        // TODO: don't rely on dependsOn which isn't reliable for execution order.
        if (testedVariant.config.type == VariantConfiguration.Type.DEFAULT) {
            checkTask.dependsOn testedVariant.installTask, variant.installTask, runTestsTask, testedVariant.uninstallTask, variant.uninstallTask
        } else {
            checkTask.dependsOn variant.installTask, runTestsTask, variant.uninstallTask
        }
    }

    /**
     * Creates the packaging tasks for the given Variant.
     * @param variant the variant.
     * @param assembleTask an optional assembleTask to be used. If null a new one is created. The
     *                assembleTask is always set in the Variant.
     */
    protected void addPackageTasks(ApplicationVariant variant, Task assembleTask) {
        // Add a dex task
        def dexTaskName = "dex${variant.name}"
        def dexTask = project.tasks.add(dexTaskName, DexTask)
        dexTask.dependsOn variant.compileTask
        dexTask.plugin = this
        dexTask.variant = variant
        dexTask.conventionMapping.libraries = { project.files({ variant.config.packagedJars }) }
        dexTask.conventionMapping.sourceFiles = { variant.compileTask.outputs.files }
        dexTask.conventionMapping.outputFile = {
            project.file(
                    "${project.buildDir}/libs/${project.archivesBaseName}-${variant.baseName}.dex")
        }
        dexTask.dexOptions = extension.dexOptions

        // Add a task to generate application package
        def packageApp = project.tasks.add("package${variant.name}", PackageApplicationTask)
        packageApp.dependsOn variant.resourcePackage, dexTask, variant.processJavaResources
        packageApp.plugin = this
        packageApp.variant = variant
        packageApp.configObjects = variant.configObjects

        def signedApk = variant.isSigned()

        def apkName = signedApk ?
            "${project.archivesBaseName}-${variant.baseName}-unaligned.apk" :
            "${project.archivesBaseName}-${variant.baseName}-unsigned.apk"

        packageApp.conventionMapping.outputFile = {
            project.file("$project.buildDir/apk/${apkName}")
        }
        packageApp.conventionMapping.resourceFile = { variant.resourcePackage.singleFile }
        packageApp.conventionMapping.dexFile = { dexTask.outputFile }
        packageApp.conventionMapping.javaResourceDir = {
            variant.processJavaResources.destinationDir
        }

        def appTask = packageApp

        if (signedApk) {
            if (variant.zipAlign) {
                // Add a task to zip align application package
                def alignApp = project.tasks.add("zipalign${variant.name}", ZipAlignTask)
                alignApp.dependsOn packageApp
                alignApp.conventionMapping.inputFile = { packageApp.outputFile }
                alignApp.conventionMapping.outputFile = {
                    project.file(
                            "$project.buildDir/apk/${project.archivesBaseName}-${variant.baseName}.apk")
                }
                alignApp.sdkDir = sdkDir

                appTask = alignApp
            }

            // Add a task to install the application package
            def installTask = project.tasks.add("install${variant.name}", InstallTask)
            installTask.description = "Installs the " + variant.description
            installTask.group = INSTALL_GROUP
            installTask.dependsOn appTask
            installTask.conventionMapping.packageFile = { appTask.outputFile }
            installTask.sdkDir = sdkDir

            variant.installTask = installTask
        }

        // Add an assemble task
        if (assembleTask == null) {
            assembleTask = project.tasks.add("assemble${variant.name}")
            assembleTask.description = "Assembles the " + variant.description
            assembleTask.group = org.gradle.api.plugins.BasePlugin.BUILD_GROUP
        }
        assembleTask.dependsOn appTask
        variant.assembleTask = assembleTask

        // add an uninstall task
        def uninstallTask = project.tasks.add("uninstall${variant.name}", UninstallTask)
        uninstallTask.description = "Uninstalls the " + variant.description
        uninstallTask.group = INSTALL_GROUP
        uninstallTask.variant = variant
        uninstallTask.sdkDir = sdkDir

        variant.uninstallTask = uninstallTask
        uninstallAll.dependsOn uninstallTask
    }

    protected void createDependencyReportTask() {
        def androidDependencyTask = project.tasks.add("androidDependencies", AndroidDependencyTask)
        androidDependencyTask.setDescription("Displays the Android dependencies of the project")
        androidDependencyTask.setVariants(variants)
        androidDependencyTask.setGroup("Help")
    }

    PrepareDependenciesTask createPrepareDependenciesTask(ApplicationVariant variant,
            List<ConfigurationDependencies> configDependenciesList) {
        def prepareDependenciesTask = project.tasks.add("prepare${variant.name}Dependencies",
                PrepareDependenciesTask)
        prepareDependenciesTask.plugin = this
        prepareDependenciesTask.variant = variant

        // for all libraries required by the configurations of this variant, make this task
        // depend on all the tasks preparing these libraries.
        for (ConfigurationDependencies configDependencies : configDependenciesList) {

            for (AndroidDependencyImpl lib : configDependencies.libraries) {
                addDependencyToPrepareTask(prepareDependenciesTask, lib)
            }
        }

        return prepareDependenciesTask
    }

    def addDependencyToPrepareTask(PrepareDependenciesTask prepareDependenciesTask,
                                   AndroidDependencyImpl lib) {
        def prepareLibTask = prepareTaskMap.get(lib)
        if (prepareLibTask != null) {
            prepareDependenciesTask.dependsOn prepareLibTask
        }

        for (AndroidDependencyImpl childLib : lib.dependencies) {
            addDependencyToPrepareTask(prepareDependenciesTask, childLib)
        }
    }

    def resolveDependencies(List<ConfigurationDependencies> configs) {
        Map<ModuleVersionIdentifier, List<AndroidDependency>> modules = [:]
        Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts = [:]
        Multimap<AndroidDependency, ConfigurationDependencies> reverseMap = ArrayListMultimap.create()

        // start with the default config and its test
        resolveDependencyForConfig(defaultConfigData, modules, artifacts, reverseMap)
        resolveDependencyForConfig(defaultConfigData.testConfigDependencies, modules, artifacts,
                reverseMap)

        // and then loop on all the other configs
        for (ConfigurationDependencies config : configs) {
            resolveDependencyForConfig(config, modules, artifacts, reverseMap)
            if (config.testConfigDependencies != null) {
                resolveDependencyForConfig(config.testConfigDependencies, modules, artifacts,
                        reverseMap)
            }
        }

        modules.values().each { List list ->
            if (!list.isEmpty()) {
                // get the first item only
                AndroidDependencyImpl androidDependency = (AndroidDependencyImpl) list.get(0)

                String bundleName = GUtil.toCamelCase(androidDependency.name.replaceAll("\\:", " "))

                def prepareLibraryTask = project.tasks.add("prepare${bundleName}Library",
                        PrepareLibraryTask)
                prepareLibraryTask.description = "Prepare ${androidDependency.name}"
                prepareLibraryTask.bundle = androidDependency.bundle
                prepareLibraryTask.explodedDir = androidDependency.bundleFolder

                // Use the reverse map to find all the configurations that included this android
                // library so that we can make sure they are built.
                List<ConfigurationDependencies> configDepList = reverseMap.get(androidDependency)
                if (configDepList != null && !configDepList.isEmpty()) {
                    for (ConfigurationDependencies configDependencies: configDepList) {
                        prepareLibraryTask.dependsOn configDependencies.configuration.buildDependencies
                    }
                }

                prepareTaskMap.put(androidDependency, prepareLibraryTask)
            }
        }
    }

    def resolveDependencyForConfig(
            ConfigurationDependencies configDependencies,
            Map<ModuleVersionIdentifier, List<AndroidDependency>> modules,
            Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts,
            Multimap<AndroidDependency, ConfigurationDependencies> reverseMap) {

        def compileClasspath = configDependencies.configuration

        // TODO - shouldn't need to do this - fix this in Gradle
        ensureConfigured(compileClasspath)

        def checker = new DependencyChecker(logger)

        // TODO - defer downloading until required -- This is hard to do as we need the info to build the variant config.
        List<AndroidDependency> bundles = []
        List<JarDependency> jars = []
        collectArtifacts(compileClasspath, artifacts)
        compileClasspath.resolvedConfiguration.resolutionResult.root.dependencies.each { ResolvedDependencyResult dep ->
            addDependency(dep.selected, checker, configDependencies, bundles, jars, modules,
                    artifacts, reverseMap)
        }

        configDependencies.libraries = bundles
        configDependencies.jars = jars

        // TODO - filter bundles out of source set classpath

        configureBuild(configDependencies)
    }

    def ensureConfigured(Configuration config) {
        config.allDependencies.withType(ProjectDependency).each { dep ->
            project.evaluationDependsOn(dep.dependencyProject.path)
            ensureConfigured(dep.projectConfiguration)
        }
    }

    def collectArtifacts(Configuration configuration, Map<ModuleVersionIdentifier,
                         List<ResolvedArtifact>> artifacts) {
        configuration.resolvedConfiguration.resolvedArtifacts.each { ResolvedArtifact artifact ->
            def id = artifact.moduleVersion.id
            List<ResolvedArtifact> moduleArtifacts = artifacts[id]
            if (moduleArtifacts == null) {
                moduleArtifacts = []
                artifacts[id] = moduleArtifacts
            }
            moduleArtifacts << artifact
        }
    }

    def addDependency(ResolvedModuleVersionResult moduleVersion,
                      DependencyChecker checker,
                      ConfigurationDependencies configDependencies,
                      Collection<AndroidDependency> bundles,
                      Collection<JarDependency> jars,
                      Map<ModuleVersionIdentifier, List<AndroidDependency>> modules,
                      Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts,
                      Multimap<AndroidDependency, ConfigurationDependencies> reverseMap) {
        def id = moduleVersion.id
        if (checker.excluded(id)) {
            return
        }

        List<AndroidDependency> bundlesForThisModule = modules[id]
        if (bundlesForThisModule == null) {
            bundlesForThisModule = []
            modules[id] = bundlesForThisModule

            def nestedBundles = []
            moduleVersion.dependencies.each { ResolvedDependencyResult dep ->
                addDependency(dep.selected, checker, configDependencies, nestedBundles,
                        jars, modules, artifacts, reverseMap)
            }

            def moduleArtifacts = artifacts[id]
            moduleArtifacts?.each { artifact ->
                if (artifact.type == 'alb') {
                    def explodedDir = project.file(
                            "$project.buildDir/exploded-bundles/$artifact.file.name")
                    AndroidDependencyImpl adep = new AndroidDependencyImpl(
                            id.group + ":" + id.name + ":" + id.version,
                            explodedDir, nestedBundles, artifact.file)
                    bundlesForThisModule << adep
                    reverseMap.put(adep, configDependencies)
                } else {
                    // TODO - need the correct values for the boolean flags
                    jars << new JarDependency(artifact.file.absolutePath, true, true, true)
                }
            }

            if (bundlesForThisModule.empty && !nestedBundles.empty) {
                throw new GradleException("Module version $id depends on libraries but is not a library itself")
            }
        } else {
            for (AndroidDependency adep : bundlesForThisModule) {
                reverseMap.put(adep, configDependencies)
            }
        }

        bundles.addAll(bundlesForThisModule)
    }

    private void configureBuild(ConfigurationDependencies configurationDependencies) {
        def configuration = configurationDependencies.configuration

        addDependsOnTaskInOtherProjects(
                project.getTasks().getByName(JavaBasePlugin.BUILD_NEEDED_TASK_NAME), true,
                JavaBasePlugin.BUILD_NEEDED_TASK_NAME, "compile");
        addDependsOnTaskInOtherProjects(
                project.getTasks().getByName(JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME), false,
                JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME, "compile");
    }

    /**
     * Adds a dependency on tasks with the specified name in other projects.  The other projects
     * are determined from project lib dependencies using the specified configuration name.
     * These may be projects this project depends on or projects that depend on this project
     * based on the useDependOn argument.
     *
     * @param task Task to add dependencies to
     * @param useDependedOn if true, add tasks from projects this project depends on, otherwise
     * use projects that depend on this one.
     * @param otherProjectTaskName name of task in other projects
     * @param configurationName name of configuration to use to find the other projects
     */
    private void addDependsOnTaskInOtherProjects(final Task task, boolean useDependedOn,
                                                 String otherProjectTaskName,
                                                 String configurationName) {
        Project project = task.getProject();
        final Configuration configuration = project.getConfigurations().getByName(
                configurationName);
        task.dependsOn(configuration.getTaskDependencyFromProjectDependency(
                useDependedOn, otherProjectTaskName));
    }
}


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

import com.android.build.gradle.internal.AaptOptionsImpl
import com.android.builder.SymbolFileProvider
import com.android.builder.VariantConfiguration
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class ProcessResourcesTask extends BaseTask {

    @InputFile
    File manifestFile

    @InputDirectory @Optional
    File preprocessResDir

    @InputFiles
    Iterable<File> resDirectories

    @InputDirectory @Optional
    File assetsDir

    @Nested
    List<SymbolFileProvider> libraries

    @Input @Optional
    String packageOverride

    @OutputDirectory @Optional
    File sourceOutputDir

    @OutputDirectory @Optional
    File textSymbolDir

    @OutputFile @Optional
    File packageFile

    @OutputFile @Optional
    File proguardFile

    // this doesn't change from one build to another, so no need to annotate
    VariantConfiguration.Type type

    @Input
    boolean debuggable

    @Nested
    AaptOptionsImpl aaptOptions

    @TaskAction
    void generate() {

        getBuilder().processResources(
                getManifestFile(),
                getPreprocessResDir(),
                getResDirectories(),
                getAssetsDir(),
                getLibraries(),
                getPackageOverride(),
                getSourceOutputDir()?.absolutePath,
                getTextSymbolDir()?.absolutePath,
                getPackageFile()?.absolutePath,
                getProguardFile()?.absolutePath,
                getType(),
                getDebuggable(),
                getAaptOptions())
    }
}

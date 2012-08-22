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

import com.android.builder.AaptOptions
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.InputFile

class ProcessResources extends BaseAndroidTask {

    @InputFile
    File manifestFile

    @InputDirectory @Optional
    File crunchDir

    @OutputDirectory @Optional
    File sourceOutputDir

    @OutputFile @Optional
    File packageFile

    @OutputFile @Optional
    File proguardFile

    @Input
    AaptOptions aaptOptions

    @TaskAction
    void generate() {

        provider.androidBuilder.processResources(
                getManifestFile().absolutePath,
                getCrunchDir()?.absolutePath,
                getSourceOutputDir()?.absolutePath,
                getPackageFile()?.absolutePath,
                getProguardFile()?.absolutePath,
                getAaptOptions())
    }
}

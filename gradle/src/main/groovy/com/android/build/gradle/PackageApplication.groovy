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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*

class PackageApplication extends DefaultTask {
    @OutputFile
    File outputFile

    @Input
    File sdkDir

    @InputFile
    File resourceFile

    @InputFile
    File dexFile

    @TaskAction
    void generate() {
        def antJar = new File(getSdkDir(), "tools/lib/anttasks.jar")
        ant.taskdef(resource: "anttasks.properties", classpath: antJar)
        ant.apkbuilder(apkFilepath: getOutputFile(),
                resourcefile: project.fileResolver.withBaseDir(getOutputFile().parentFile).resolveAsRelativePath(getResourceFile()),
                outfolder: getOutputFile().getParentFile(),
                debugsigning: true) {
            dex(path: getDexFile())
        }
    }
}

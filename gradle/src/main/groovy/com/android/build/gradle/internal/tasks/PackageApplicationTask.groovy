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
package com.android.build.gradle.internal.tasks

import com.android.build.gradle.tasks.PackageApplication
import com.android.builder.packaging.DuplicateFileException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

public class PackageApplicationTask extends PackageApplication {

    @InputFiles
    List<File> packagedJars

    @Input
    boolean debugSigned

    @Input
    boolean debugJni

    @Input @Optional
    String signingStoreLocation

    @Input @Optional
    String signingStorePassword

    @Input @Optional
    String signingKeyAlias

    @Input @Optional
    String signingKeyPassword

    @TaskAction
    void generate() {

        try {
            getLogger().info("JNI PATH: " + getJniDir()?.absolutePath)

            getBuilder().packageApk(
                    getResourceFile().absolutePath,
                    getDexFile().absolutePath,
                    getPackagedJars(),
                    getJavaResourceDir()?.absolutePath,
                    getJniDir()?.absolutePath,
                    getDebugSigned(),
                    getDebugJni(),
                    getSigningStoreLocation(),
                    getSigningStorePassword(),
                    getSigningKeyAlias(),
                    getSigningKeyPassword(),
                    getOutputFile().absolutePath)
        } catch (DuplicateFileException e) {
            def logger = getLogger()
            logger.error("Error: duplicate files during packaging of APK " + getOutputFile().absolutePath)
            logger.error("\tPath in archive: " + e.archivePath)
            logger.error("\tOrigin 1: " + e.file1)
            logger.error("\tOrigin 2: " + e.file2)
            throw new RuntimeException();
        }
    }
}

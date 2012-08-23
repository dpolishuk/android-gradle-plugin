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
package com.android.build.gradle.internal

import org.gradle.api.tasks.SourceSet
import com.android.builder.PathProvider

/**
 * Base Dimension providing an implementation of PathProvider for a given SourceSet.
 */
class BaseDimension implements PathProvider {

    final SourceSet mainSource
    private final String baseDir
    private final String name


    BaseDimension(SourceSet mainSource, String baseDir, String name) {
        this.mainSource = mainSource
        this.baseDir = baseDir
        this.name = name
    }

    @Override
    Set<File> getJavaSource() {
        return mainSource.allJava.srcDirs
    }

    @Override
    Set<File> getJavaResources() {
        return mainSource.resources.srcDirs
    }

    @Override
    File getAndroidResources() {
        // FIXME: make this configurable by the SourceSet
        return new File("$baseDir/src/$name/res")
    }

    @Override
    File getAndroidAssets() {
        // FIXME: make this configurable by the SourceSet
        return new File("$baseDir/src/$name/assets")
    }

    @Override
    File getAndroidManifest() {
        // FIXME: make this configurable by the SourceSet
        return new File("$baseDir/src/$name/AndroidManifest.xml")
    }

    @Override
    File getAidlSource() {
        // FIXME: make this configurable by the SourceSet
        return new File("$baseDir/src/$name/aidl")
    }

    @Override
    File getRenderscriptSource() {
        // FIXME: make this configurable by the SourceSet
        return new File("$baseDir/src/$name/rs")
    }

    @Override
    File getNativeSource() {
        // FIXME: make this configurable by the SourceSet
        return new File("$baseDir/src/$name/jni")
    }
}

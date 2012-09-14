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

import com.android.builder.AndroidDependency

class AndroidDependencyImpl implements AndroidDependency {
    private final File explodedBundle

    AndroidDependencyImpl(File explodedBundle) {
        this.explodedBundle = explodedBundle
    }

    @Override
    File getJarFile() {
        return new File(explodedBundle, "classes.jar")
    }

    @Override
    File getManifest() {
        return new File(explodedBundle, "AndroidManifest.xml")
    }

    @Override
    List<AndroidDependency> getDependencies() {
        return []
    }

    @Override
    File getAidlFolder() {
        return new File(explodedBundle, "aidl")
    }

    @Override
    File getResFolder() {
        return new File(explodedBundle, "res")
    }

    @Override
    File getAssetsFolder() {
        // TODO - implement
        return null;
    }

    @Override
    File getJniFolder() {
        // TODO - implement
        return null
    }

    @Override
    File getLintJar() {
        // TODO - implement
        return null
    }

    @Override
    File getProguardRules() {
        // TODO - implement
        return null
    }
}




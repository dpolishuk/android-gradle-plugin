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

import java.io.File;

/**
 * Implementation of SourceProvider for testing that provides the default convention paths.
 */
class MockSourceProvider implements SourceProvider {

    public MockSourceProvider(String root) {
        mRoot = root;
    }

    private final String mRoot;

    @Override
    public File getResourcesDir() {
        return new File(mRoot, "res");
    }

    @Override
    public File getAssetsDir() {
        return new File(mRoot, "assets");
    }

    @Override
    public File getManifestFile() {
        return new File(mRoot, "AndroidManifest.xml");
    }

    @Override
    public File getAidlDir() {
        return new File(mRoot, "aidl");
    }

    @Override
    public File getRenderscriptDir() {
        return new File(mRoot, "rs");
    }

    @Override
    public File getJniDir() {
        return new File(mRoot, "jni");
    }
}

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

package com.android.build.gradle.internal;

import com.android.builder.TextSymbolProvider;
import org.gradle.api.tasks.InputFile;

import java.io.File;

/**
 * Implementation of TextSymbolProvider that can be used as a Task input.
 */
public class TextSymbolProviderImpl implements TextSymbolProvider {

    @InputFile
    private File manifest;

    @InputFile
    private File textSymbol;

    TextSymbolProviderImpl(File manifest, File textSymbol) {
        this.manifest = manifest;
        this.textSymbol = textSymbol;
    }

    @Override
    public File getManifest() {
        return manifest;
    }

    @Override
    public File getTextSymbol() {
        return textSymbol;
    }
}

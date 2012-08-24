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

import com.android.builder.BuildType
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import com.android.builder.ProductFlavor

/**
 * Base android extension for all android plugins.
 */
class BaseAndroidExtension {

    String target
    final ProductFlavor defaultConfig = new ProductFlavor("main");

    final NamedDomainObjectContainer<BuildType> buildTypes

    BaseAndroidExtension(NamedDomainObjectContainer<BuildType> buildTypes) {
        this.buildTypes = buildTypes
    }

    void buildTypes(Action<? super NamedDomainObjectContainer<BuildType>> action) {
        action.execute(buildTypes)
    }

    void defaultConfig(Action<ProductFlavor> action) {
        action.execute(defaultConfig)
    }
}

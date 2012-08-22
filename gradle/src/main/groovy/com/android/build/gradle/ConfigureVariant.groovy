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

import com.android.builder.AndroidBuilder
import com.android.builder.BuildType
import com.android.builder.DefaultSdkParser
import com.android.builder.ProductFlavor
import com.android.utils.StdLogger
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 */
class ConfigureVariant extends DefaultTask {
    AndroidPlugin plugin

    @Input
    BuildType buildType

    @Input
    ProductFlavor mainProductFlavor

    @Input
    ProductFlavor productFlavor

    @TaskAction
    void generate() {
        plugin.androidBuilder = new AndroidBuilder(new DefaultSdkParser(sdkDir),
                    new StdLogger(SdkLogger.Level.VERBOSE), true)

        plugin.androidBuilder.setTarget(plugin.extension.target)
        plugin.androidBuilder.setBuildVariant(mainProductFlavor, productFlavor, buildType)
    }
}

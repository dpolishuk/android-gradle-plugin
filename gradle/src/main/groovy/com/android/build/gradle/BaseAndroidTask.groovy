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

import com.android.build.gradle.internal.ApplicationVariant
import com.android.builder.AndroidBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

abstract class BaseAndroidTask extends DefaultTask {

    AndroidBasePlugin plugin
    ApplicationVariant variant

    @Input @Optional
    Iterable<Object> configObjects

    protected AndroidBuilder getBuilder() {
        AndroidBuilder androidBuilder = plugin.getAndroidBuilder(variant);

        if (androidBuilder == null) {
            androidBuilder = variant.createBuilder(plugin)
            plugin.setAndroidBuilder(variant, androidBuilder)
        }

        return androidBuilder
    }
}

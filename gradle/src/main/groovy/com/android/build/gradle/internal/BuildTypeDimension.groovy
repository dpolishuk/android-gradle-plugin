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

import com.android.builder.BuildType
import org.gradle.api.tasks.SourceSet
import com.android.builder.BuildTypeHolder

class BuildTypeDimension extends BaseDimension implements BuildTypeHolder {
    final BuildType buildType
    final Set<ProductionAppVariant> variants = []

    BuildTypeDimension(BuildType buildType, SourceSet mainSource, String baseDir) {
        super(mainSource, baseDir, buildType.name)
        this.buildType = buildType
    }

    String getName() {
        return buildType.name
    }

    String getAssembleTaskName() {
        return "assemble${buildType.name.capitalize()}"
    }
}

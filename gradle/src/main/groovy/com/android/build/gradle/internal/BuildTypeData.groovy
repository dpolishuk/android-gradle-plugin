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

import com.android.build.gradle.AndroidSourceSet
import com.android.builder.BuildType
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Class containing a BuildType and associated data (Sourceset for instance).
 */
class BuildTypeData extends ConfigurationDependencies {

    final BuildType buildType

    final Task assembleTask

    BuildTypeData(BuildType buildType, AndroidSourceSet sourceSet, Project project) {
        super(project, sourceSet, ConfigType.BUILDTYPE)

        this.buildType = buildType

        assembleTask = project.tasks.add("assemble${buildType.name.capitalize()}")
        assembleTask.description = "Assembles all ${buildType.name.capitalize()} builds"
        assembleTask.group = "Build"
    }
}

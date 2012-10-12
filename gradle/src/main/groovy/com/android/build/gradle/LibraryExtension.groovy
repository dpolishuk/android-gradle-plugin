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

import com.android.build.gradle.internal.BuildTypeFactory.BuildTypeDsl
import com.android.builder.BuildType
import org.gradle.api.Action
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator

/**
 * Extension for 'library' project.
 */
public class LibraryExtension extends BaseExtension {

    final BuildType debug
    final BuildType release

    LibraryExtension(ProjectInternal project, Instantiator instantiator) {
        super(project, instantiator)

        debug = instantiator.newInstance(BuildTypeDsl.class, BuildType.DEBUG)
        release = instantiator.newInstance(BuildTypeDsl.class, BuildType.RELEASE)
    }

    void debug(Action<BuildType> action) {
        action.execute(debug);
    }

    void release(Action<BuildType> action) {
        action.execute(release);
    }
}

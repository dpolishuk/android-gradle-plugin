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
import com.android.builder.BuildTypeHolder
import com.android.builder.ProductFlavorHolder
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidLibraryPlugin extends AndroidBasePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        super.apply(project)

        def buildTypes = project.container(BuildType)

        project.extensions.create('android', AndroidLibraryExtension, buildTypes)

        buildTypes.whenObjectAdded { BuildType buildType ->
            addBuildType(buildType)
        }
        buildTypes.whenObjectRemoved {
            throw new UnsupportedOperationException("Removing build types is not implemented yet.")
        }

        buildTypes.create(BuildType.DEBUG)
        buildTypes.create(BuildType.RELEASE)
    }

    void addBuildType(BuildType buildType) {
        def assemble = project.tasks.add("assemble${buildType.name.capitalize()}")

        project.tasks.assemble.dependsOn assemble
    }

    @Override
    String getTarget() {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    ProductFlavorHolder getMainFlavor() {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    BuildTypeHolder getDebugType() {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }
}

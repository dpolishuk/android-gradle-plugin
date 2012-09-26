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

import com.android.utils.Pair
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.TaskAction

class PrepareDependenciesTask extends BaseAndroidTask {
    final Map<File, File> bundles = [:]
    final Set<Pair<Integer, String>> androidDependencies = []

    void add(File bundle, File explodedDir) {
        bundles[bundle] = explodedDir
    }

    void addDependency(Pair<Integer, String> api) {
        androidDependencies.add(api)
    }

    @InputFiles
    Iterable<File> getBundles() {
        return bundles.keySet()
    }


    @OutputDirectories
    Iterable<File> getExplodedBundles() {
        return bundles.values()
    }

    @TaskAction
    def prepare() {
        bundles.each { bundle, explodedDir ->
            project.copy {
                from project.zipTree(bundle)
                into explodedDir
            }
        }

        // TODO check against variant's minSdkVersion
        if (!androidDependencies.isEmpty()) {
            def builder = getBuilder();
            def target = builder.getTargetApiLevel()
            for (Pair<Integer, String> dependency : androidDependencies) {
                if (dependency.getFirst() > target) {
                    String parentDependency = dependency.getSecond()
                    if (parentDependency != null) {
                        throw new RuntimeException(String.format(
                                "ERROR: %s depends on Android API level %d, but project target is API level %d",
                                parentDependency, dependency.getFirst(), target))
                    } else {
                        throw new RuntimeException(String.format(
                                "ERROR: project depends on Android API level %d, but project target is API level %d",
                                dependency.getFirst(), target))
                    }
                }
            }
        }
    }
}

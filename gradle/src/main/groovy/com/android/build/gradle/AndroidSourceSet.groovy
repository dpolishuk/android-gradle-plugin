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

import org.gradle.api.file.SourceDirectorySet

/**
 */
public interface AndroidSourceSet {

    /**
     * Returns the name of this source set.
     *
     * @return The name. Never returns null.
     */
    String getName();

    /**
     * Returns the Java resources which are to be copied into the javaResources output directory.
     *
     * @return the java resources. Never returns null.
     */
    SourceDirectorySet getResources();

    /**
     * Configures the Java resources for this set.
     *
     * <p>The given closure is used to configure the {@link SourceDirectorySet} which contains the
     * java resources.
     *
     * @param configureClosure The closure to use to configure the javaResources.
     * @return this
     */
    AndroidSourceSet resources(Closure configureClosure);

    /**
     * Returns the Java source which is to be compiled by the Java compiler into the class output
     * directory.
     *
     * @return the Java source. Never returns null.
     */
    SourceDirectorySet getJava();

    /**
     * Configures the Java source for this set.
     *
     * <p>The given closure is used to configure the {@link SourceDirectorySet} which contains the
     * Java source.
     *
     * @param configureClosure The closure to use to configure the Java source.
     * @return this
     */
    AndroidSourceSet java(Closure configureClosure);

    /**
     * All Java source files for this source set. This includes, for example, source which is
     * directly compiled, and source which is indirectly compiled through joint compilation.
     *
     * @return the Java source. Never returns null.
     */
    SourceDirectorySet getAllJava();

    /**
     * All source files for this source set.
     *
     * @return the source. Never returns null.
     */
    SourceDirectorySet getAllSource();

    /**
     * Returns the name of the compile configuration for this source set.
     * @return The configuration name
     */
    String getCompileConfigurationName();

    /**
     * Returns the name of the runtime configuration for this source set.
     * @return The runtime configuration name
     */
    String getPackageConfigurationName();

    AndroidSourceFile getManifest();
    AndroidSourceSet manifest(Closure configureClosure);

    AndroidSourceDirectory getRes();
    AndroidSourceSet res(Closure configureClosure);

    AndroidSourceDirectory getAssets();
    AndroidSourceSet assets(Closure configureClosure);

    AndroidSourceDirectory getAidl();
    AndroidSourceSet aidl(Closure configureClosure);

    AndroidSourceDirectory getRenderscript();
    AndroidSourceSet renderscript(Closure configureClosure);

    AndroidSourceDirectory getJni();
    AndroidSourceSet jni(Closure configureClosure);

}

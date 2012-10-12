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

import com.android.build.gradle.internal.AaptOptionsImpl
import com.android.build.gradle.internal.AndroidSourceSetFactory
import com.android.build.gradle.internal.DexOptionsImpl
import com.android.build.gradle.internal.GroupableProductFlavor
import com.android.build.gradle.internal.ProductFlavorDsl
import com.android.builder.ProductFlavor
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator

/**
 * Base android extension for all android plugins.
 */
class BaseExtension {

    String target

    final ProductFlavor defaultConfig
    final AaptOptionsImpl aaptOptions
    final DexOptionsImpl dexOptions

    /**
     * The source sets container.
     */
    final NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer

    BaseExtension(ProjectInternal project, Instantiator instantiator) {
        defaultConfig = instantiator.newInstance(ProductFlavorDsl.class, "main")

        aaptOptions = instantiator.newInstance(AaptOptionsImpl.class)
        dexOptions = instantiator.newInstance(DexOptionsImpl.class)

        sourceSetsContainer = project.container(AndroidSourceSet,
                new AndroidSourceSetFactory(instantiator, project.fileResolver))

        sourceSetsContainer.whenObjectAdded { AndroidSourceSet sourceSet ->
            ConfigurationContainer configurations = project.getConfigurations()

            Configuration compileConfiguration = configurations.findByName(
                    sourceSet.getCompileConfigurationName())
            if (compileConfiguration == null) {
                compileConfiguration = configurations.add(sourceSet.getCompileConfigurationName())
            }
            compileConfiguration.setVisible(false);
            compileConfiguration.setDescription(
                    String.format("Classpath for compiling the %s sources.", sourceSet.getName()))

            Configuration packageConfiguration = configurations.findByName(
                    sourceSet.getPackageConfigurationName())
            if (packageConfiguration == null) {
                packageConfiguration = configurations.add(sourceSet.getPackageConfigurationName())
            }
            packageConfiguration.setVisible(false)
            packageConfiguration.extendsFrom(compileConfiguration)
            packageConfiguration.setDescription(
                    String.format("Classpath packaged with the compiled %s classes.",
                            sourceSet.getName()));

            sourceSet.getJava().srcDir(String.format("src/%s/java", sourceSet.getName()))
            sourceSet.getResources().srcDir(
                    String.format("src/%s/resources", sourceSet.getName()))
            sourceSet.getRes().srcDir(String.format("src/%s/res", sourceSet.getName()))
            sourceSet.getAssets().srcDir(String.format("src/%s/assets", sourceSet.getName()))
            sourceSet.getManifest().srcFile(
                    String.format("src/%s/AndroidManifest.xml", sourceSet.getName()))
            sourceSet.getAidl().srcDir(String.format("src/%s/aidl", sourceSet.getName()))
            sourceSet.getRenderscript().srcDir(String.format("src/%s/rs", sourceSet.getName()))
            sourceSet.getJni().srcDir(String.format("src/%s/jni", sourceSet.getName()))
        }
    }

    void sourceSets(Action<NamedDomainObjectContainer<AndroidSourceSet>> action) {
        action.execute(sourceSetsContainer)
    }

    void defaultConfig(Action<GroupableProductFlavor> action) {
        action.execute(defaultConfig)
    }

    void aaptOptions(Action<AaptOptionsImpl> action) {
        action.execute(aaptOptions)
    }

    void dexOptions(Action<DexOptionsImpl> action) {
        action.execute(dexOptions)
    }


}

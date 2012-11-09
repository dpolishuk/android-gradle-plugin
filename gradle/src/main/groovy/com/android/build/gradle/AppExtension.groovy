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
import com.android.builder.ProductFlavor
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator

/**
 * Extension for 'application' project.
 */
public class AppExtension extends BaseExtension {

    final NamedDomainObjectContainer<ProductFlavor> productFlavors
    final NamedDomainObjectContainer<BuildType> buildTypes

    List<String> flavorGroupList
    String testBuildType = "debug"

    AppExtension(AppPlugin plugin, ProjectInternal project, Instantiator instantiator,
                 NamedDomainObjectContainer<BuildType> buildTypes,
                 NamedDomainObjectContainer<ProductFlavor> productFlavors) {
        super(plugin, project, instantiator)
        this.buildTypes = buildTypes
        this.productFlavors = productFlavors
    }

    void buildTypes(Action<? super NamedDomainObjectContainer<BuildType>> action) {
        action.execute(buildTypes)
    }

    void productFlavors(Action<? super NamedDomainObjectContainer<ProductFlavor>> action) {
        action.execute(productFlavors)
    }

    public void flavorGroups(String... groups) {
        flavorGroupList = Arrays.asList(groups)
    }
}

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
import org.gradle.api.Project
import org.gradle.api.Task

class ProductFlavorData extends ConfigurationDependencies {

    final ProductFlavorDsl productFlavor

    final AndroidSourceSet testSourceSet

    Task assembleTask

    ProductFlavorData(ProductFlavorDsl productFlavor,
                      AndroidSourceSet sourceSet, AndroidSourceSet testSourceSet,
                      Project project, ConfigType type) {
        super(project, sourceSet, type)

        this.productFlavor = productFlavor
        this.testSourceSet = testSourceSet

        setTestConfigDependencies(
                new ConfigurationDependencies(project, testSourceSet, type))
    }

    ProductFlavorData(ProductFlavorDsl productFlavor,
                      AndroidSourceSet sourceSet, AndroidSourceSet testSourceSet,
                      Project project) {
        this(productFlavor, sourceSet, testSourceSet, project, ConfigType.FLAVOR)
    }


    public static String getFlavoredName(ProductFlavorData[] flavorDataArray, boolean capitalized) {
        StringBuilder builder = new StringBuilder()
        for (ProductFlavorData data : flavorDataArray) {
            builder.append(capitalized ?
                data.productFlavor.name.capitalize() :
                data.productFlavor.name)
        }

        return builder.toString()
    }
}

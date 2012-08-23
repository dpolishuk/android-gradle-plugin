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

import com.android.build.gradle.AndroidBasePlugin
import com.android.builder.AndroidBuilder
import com.android.builder.BuildTypeHolder
import com.android.builder.ProductFlavorHolder
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.compile.Compile

class ProductionAppVariant implements ApplicationVariant {
    final String name
    final BuildTypeHolder buildTypeHolder
    final ProductFlavorHolder productFlavorHolder
    FileCollection runtimeClasspath
    FileCollection resourcePackage
    Compile compileTask

    ProductionAppVariant(BuildTypeHolder buildTypeHolder, ProductFlavorHolder productFlavorHolder) {
        this.name = "${productFlavorHolder.productFlavor.name.capitalize()}${buildTypeHolder.buildType.name.capitalize()}"
        this.buildTypeHolder = buildTypeHolder
        this.productFlavorHolder = productFlavorHolder
    }

    String getDescription() {
        return "$productFlavorHolder.productFlavor.name $buildTypeHolder.buildType.name"
    }

    String getDirName() {
        return "$productFlavorHolder.productFlavor.name/$buildTypeHolder.buildType.name"
    }

    String getBaseName() {
        return "$productFlavorHolder.productFlavor.name-$buildTypeHolder.buildType.name"
    }

    @Override
    boolean getZipAlign() {
        return buildTypeHolder.buildType.zipAlign
    }

    @Override
    boolean isSigned() {
        return buildTypeHolder.buildType.debugSigned ||
                productFlavorHolder.productFlavor.isSigningReady()
    }

    @Override
    AndroidBuilder createBuilder(AndroidBasePlugin androidBasePlugin) {
        AndroidBuilder androidBuilder = new AndroidBuilder(
                androidBasePlugin.sdkParser,
                androidBasePlugin.logger,
                androidBasePlugin.verbose)

        androidBuilder.setTarget(androidBasePlugin.target)

        androidBuilder.setBuildVariant(androidBasePlugin.mainFlavor, buildTypeHolder)

        if (productFlavorHolder != null) {
            androidBuilder.addProductFlavor(productFlavorHolder)
        }

        return androidBuilder
    }
}

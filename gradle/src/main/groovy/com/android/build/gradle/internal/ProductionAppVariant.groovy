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
import com.android.builder.VariantConfiguration
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.compile.Compile

class ProductionAppVariant implements ApplicationVariant {
    final String name
    final VariantConfiguration variant
    FileCollection runtimeClasspath
    FileCollection resourcePackage
    Compile compileTask
    Task assembleTask

    ProductionAppVariant(VariantConfiguration variant) {
        this.variant = variant
        if (variant.hasFlavors()) {
            this.name = "${variant.firstFlavor.name.capitalize()}${variant.buildType.name.capitalize()}"
        } else {
            this.name = "${variant.buildType.name.capitalize()}"
        }
    }

    String getDescription() {
        if (variant.hasFlavors()) {
            return "${variant.buildType.name.capitalize()} build for flavor ${variant.firstFlavor.name.capitalize()}"
        } else {
            return "${variant.buildType.name.capitalize()} build"
        }
    }

    String getDirName() {
        if (variant.hasFlavors()) {
            return "$variant.firstFlavor.name/$variant.buildType.name"
        } else {
            return "$variant.buildType.name"
        }
    }

    String getBaseName() {
        if (variant.hasFlavors()) {
            return "$variant.firstFlavor.name-$variant.buildType.name"
        } else {
            return "$variant.buildType.name"
        }
    }

    @Override
    boolean getZipAlign() {
        return variant.buildType.zipAlign
    }

    @Override
    boolean isSigned() {
        return variant.buildType.debugSigned ||
                variant.mergedFlavor.isSigningReady()
    }

    @Override
    List<String> getRunCommand() {
        throw new UnsupportedOperationException()
    }

    String getPackage() {
        return variant.getPackageName(null)
    }

    @Override
    AndroidBuilder createBuilder(AndroidBasePlugin androidBasePlugin) {
        AndroidBuilder androidBuilder = new AndroidBuilder(
                androidBasePlugin.sdkParser,
                androidBasePlugin.logger,
                androidBasePlugin.verbose)

        androidBuilder.setTarget(androidBasePlugin.target)
        androidBuilder.setBuildVariant(variant, null /*testedVariant*/)

        return androidBuilder
    }
}

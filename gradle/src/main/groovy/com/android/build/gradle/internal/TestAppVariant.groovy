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
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.compile.Compile
import com.android.builder.VariantConfiguration

class TestAppVariant implements ApplicationVariant {
    final String name
    final VariantConfiguration variant
    final VariantConfiguration testedVariant
    FileCollection runtimeClasspath
    FileCollection resourcePackage
    Compile compileTask

    TestAppVariant(VariantConfiguration variant, VariantConfiguration testedVariant) {
        this.variant = variant
        this.testedVariant = testedVariant
        if (variant.hasFlavors()) {
            this.name = "${variant.firstFlavor.name.capitalize()}Test"
        } else {
            this.name = "Test"
        }
    }

    @Override
    String getDescription() {
        if (variant.hasFlavors()) {
            return "Test build for the ${variant.firstFlavor.name.capitalize()}${variant.buildType.name.capitalize()} build"
        } else {
            return "Test for the ${variant.buildType.name.capitalize()} build"
        }
    }

    String getDirName() {
        if (variant.hasFlavors()) {
            return "$variant.firstFlavor.name/test"
        } else {
            return "test"
        }
    }

    String getBaseName() {
        if (variant.hasFlavors()) {
            return "$variant.firstFlavor.name-test"
        } else {
            return "test"
        }
    }

    @Override
    boolean getZipAlign() {
        return false
    }

    @Override
    boolean isSigned() {
        return true;
    }

    @Override
    List<String> getRunCommand() {
        String[] args = [ "shell", "am", "instrument", "-w",
                variant.getPackageName(testedVariant) + "/" + testedVariant.instrumentationRunner]

        return Arrays.asList(args)
    }

    @Override
    String getPackage() {
        return variant.getPackageName(testedVariant)
    }

    @Override
    AndroidBuilder createBuilder(AndroidBasePlugin androidBasePlugin) {
        AndroidBuilder androidBuilder = new AndroidBuilder(
                androidBasePlugin.sdkParser,
                androidBasePlugin.logger,
                androidBasePlugin.verbose)

        androidBuilder.setTarget(androidBasePlugin.target)
        androidBuilder.setBuildVariant(variant, testedVariant)

        return androidBuilder
    }

}

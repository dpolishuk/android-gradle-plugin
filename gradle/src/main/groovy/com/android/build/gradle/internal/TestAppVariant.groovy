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

import com.android.builder.ProductFlavor
import org.gradle.api.file.FileCollection

class TestAppVariant implements ApplicationVariant {
    final String name
    final ProductFlavor productFlavor
    FileCollection runtimeClasspath
    FileCollection resourcePackage

    TestAppVariant(ProductFlavor productFlavor) {
        this.name = "${productFlavor.name.capitalize()}Test"
        this.productFlavor = productFlavor
    }

    @Override
    String getDescription() {
        return "$productFlavor.name test"
    }

    String getDirName() {
        return "${productFlavor.name}/test"
    }

    String getBaseName() {
        return "$productFlavor.name-test"
    }

    @Override
    boolean getZipAlign() {
        return false
    }
}

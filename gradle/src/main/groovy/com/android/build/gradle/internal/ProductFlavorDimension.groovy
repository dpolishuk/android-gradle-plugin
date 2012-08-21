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
import org.gradle.api.tasks.SourceSet

class ProductFlavorDimension {
    final ProductFlavor productFlavor
    final Set<ProductionAppVariant> variants = []
    final SourceSet mainSource
    final SourceSet testSource
    ProductionAppVariant debugVariant

    ProductFlavorDimension(ProductFlavor productFlavor, SourceSet mainSource, SourceSet testSource) {
        this.productFlavor = productFlavor
        this.mainSource = mainSource
        this.testSource = testSource
    }

    String getName() {
        return productFlavor.name
    }

    String getAssembleTaskName() {
        return "assemble${productFlavor.name.capitalize()}"
    }
}

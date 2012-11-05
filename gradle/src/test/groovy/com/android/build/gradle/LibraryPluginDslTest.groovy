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

import com.android.build.gradle.internal.BaseTest
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

/**
 * Tests for the public DSL of the App plugin ("android-library")
 */
public class LibraryPluginDslTest extends BaseTest {

    @Override
    protected void setUp() throws Exception {
        BasePlugin.TEST_SDK_DIR = new File("foo")
    }

    public void testBasic() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android-library'

        project.android {
            target "android-15"
        }

        Set<BuildVariant> variants = project.android.buildVariants
        assertEquals(2, variants.size())

        Set<BuildVariant> testVariants = project.android.testBuildVariants
        assertEquals(1, testVariants.size())

        checkTestedVariant("Debug", "Test", variants, testVariants)
        checkNonTestedVariant("Release", variants)
    }

    private void checkTestedVariant(String variantName, String testedVariantName,
                                    Set<BuildVariant> variants, Set<BuildVariant> testVariants) {
        BuildVariant variant = findVariant(variants, variantName)
        assertNotNull(variant)
        assertNotNull(variant.testVariant)
        assertEquals(testedVariantName, variant.testVariant.name)
        assertEquals(variant.testVariant, findVariant(testVariants, testedVariantName))
        checkLibraryTasks(variant)
        checkTestTasks(variant.testVariant)
    }

    private void checkNonTestedVariant(String variantName, Set<BuildVariant> variants) {
        BuildVariant variant = findVariant(variants, variantName)
        assertNotNull(variant)
        assertNull(variant.testVariant)
        checkLibraryTasks(variant)
    }

    private void checkTestTasks(BuildVariant variant) {
        assertNotNull(variant.processManifest)
        assertNotNull(variant.aidlCompile)
        assertNotNull(variant.processImages)
        assertNotNull(variant.processResources)
        assertNotNull(variant.generateBuildConfig)
        assertNotNull(variant.javaCompile)
        assertNotNull(variant.processJavaResources)
        assertNotNull(variant.dex)
        assertNotNull(variant.packageApplication)

        assertNotNull(variant.assemble)
        assertNotNull(variant.uninstall)

        assertNull(variant.zipAlign)

        if (variant.buildType.debugSigned || variant.mergedConfig.isSigningReady()) {
            assertNotNull(variant.install)
        } else {
            assertNull(variant.install)
        }

        assertNotNull(variant.runTests)
    }

    private void checkLibraryTasks(BuildVariant variant) {
        assertNotNull(variant.processManifest)
        assertNotNull(variant.aidlCompile)
        assertNotNull(variant.processResources)
        assertNotNull(variant.generateBuildConfig)
        assertNotNull(variant.javaCompile)
        assertNotNull(variant.processJavaResources)

        assertNotNull(variant.assemble)

        assertNull(variant.dex)
        assertNull(variant.packageApplication)
        assertNull(variant.zipAlign)
        assertNull(variant.install)
        assertNull(variant.uninstall)
        assertNull(variant.runTests)
    }

    private BuildVariant findVariant(Collection<BuildVariant> variants, String name) {
        for (BuildVariant variant : variants) {
            if (name.equals(variant.name)) {
                return variant
            }
        }

        return null
    }
}
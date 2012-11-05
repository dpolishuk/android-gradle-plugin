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
 * Tests for the public DSL of the App plugin ("android")
 */
public class AppPluginDslTest extends BaseTest {

    @Override
    protected void setUp() throws Exception {
        BasePlugin.TEST_SDK_DIR = new File("foo")
    }

    public void testBasic() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

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

    public void testBuildTypes() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

        project.android {
            target "android-15"
            testBuildType "staging"

            buildTypes {
                staging {
                    debugSigned true
                }
            }
        }

        // does not include tests
        Set<BuildVariant> variants = project.android.buildVariants
        assertEquals(3, variants.size())

        Set<BuildVariant> testVariants = project.android.testBuildVariants
        assertEquals(1, testVariants.size())

        checkTestedVariant("Staging", "Test", variants, testVariants)

        checkNonTestedVariant("Debug", variants)
        checkNonTestedVariant("Release", variants)
    }

    public void testFlavors() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

        project.android {
            target "android-15"

            productFlavors {
                flavor1 {

                }
                flavor2 {

                }
            }
        }

        // does not include tests
        Set<BuildVariant> variants = project.android.buildVariants
        assertEquals(4, variants.size())

        Set<BuildVariant> testVariants = project.android.testBuildVariants
        assertEquals(2, testVariants.size())

        checkTestedVariant("Flavor1Debug", "Flavor1Test", variants, testVariants)
        checkTestedVariant("Flavor2Debug", "Flavor2Test", variants, testVariants)

        checkNonTestedVariant("Flavor1Release", variants)
        checkNonTestedVariant("Flavor2Release", variants)
    }

    public void testMultiFlavors() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

        project.android {
            target "android-15"

            flavorGroups   "group1", "group2"

            productFlavors {
                f1 {
                    flavorGroup   "group1"
                }
                f2 {
                    flavorGroup   "group1"
                }

                fa {
                    flavorGroup   "group2"
                }
                fb {
                    flavorGroup   "group2"
                }
                fc {
                    flavorGroup   "group2"
                }
            }
        }

        // does not include tests
        Set<BuildVariant> variants = project.android.buildVariants
        assertEquals(12, variants.size())

        Set<BuildVariant> testVariants = project.android.testBuildVariants
        assertEquals(6, testVariants.size())

        checkTestedVariant("F1FaDebug", "F1FaTest", variants, testVariants)
        checkTestedVariant("F1FbDebug", "F1FbTest", variants, testVariants)
        checkTestedVariant("F1FcDebug", "F1FcTest", variants, testVariants)
        checkTestedVariant("F2FaDebug", "F2FaTest", variants, testVariants)
        checkTestedVariant("F2FbDebug", "F2FbTest", variants, testVariants)
        checkTestedVariant("F2FcDebug", "F2FcTest", variants, testVariants)

        checkNonTestedVariant("F1FaRelease", variants)
        checkNonTestedVariant("F1FbRelease", variants)
        checkNonTestedVariant("F1FcRelease", variants)
        checkNonTestedVariant("F2FaRelease", variants)
        checkNonTestedVariant("F2FbRelease", variants)
        checkNonTestedVariant("F2FcRelease", variants)
    }

    private void checkTestedVariant(String variantName, String testedVariantName,
                                    Set<BuildVariant> variants, Set<BuildVariant> testVariants) {
        BuildVariant variant = findVariant(variants, variantName)
        assertNotNull(variant)
        assertNotNull(variant.testVariant)
        assertEquals(testedVariantName, variant.testVariant.name)
        assertEquals(variant.testVariant, findVariant(testVariants, testedVariantName))
        checkTasks(variant, false)
        checkTasks(variant.testVariant, true)
    }

    private void checkNonTestedVariant(String variantName, Set<BuildVariant> variants) {
        BuildVariant variant = findVariant(variants, variantName)
        assertNotNull(variant)
        assertNull(variant.testVariant)
        checkTasks(variant, false)
    }

    private void checkTasks(BuildVariant variant, boolean testVariant) {
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

        if (variant.buildType.debugSigned || variant.mergedConfig.isSigningReady()) {
            assertNotNull(variant.install)

            // tested variant are never zipAligned.
            if (!testVariant && variant.buildType.zipAlign) {
                assertNotNull(variant.zipAlign)
            } else {
                assertNull(variant.zipAlign)
            }
        } else {
            assertNull(variant.install)
        }

        if (testVariant) {
            assertNotNull(variant.runTests)
        } else {
            assertNull(variant.runTests)
        }
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
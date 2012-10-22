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

import com.android.build.gradle.internal.ApplicationVariant
import com.android.build.gradle.internal.BaseTest
import com.android.build.gradle.internal.PluginHolder
import com.android.builder.BuildType
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

/**
 * Tests for the internal workings of the app plugin ("android")
 */
public class AppPluginInternalTest extends BaseTest {

    @Override
    protected void setUp() throws Exception {
        BasePlugin.TEST_SDK_DIR = new File("foo")
        AppPlugin.pluginHolder = new PluginHolder();
    }

    public void testBasic() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

        project.android {
            target "android-15"
        }

        AppPlugin plugin = AppPlugin.pluginHolder.plugin
        plugin.createAndroidTasks()

        assertEquals(2, plugin.buildTypes.size())
        assertNotNull(plugin.buildTypes.get(BuildType.DEBUG))
        assertNotNull(plugin.buildTypes.get(BuildType.RELEASE))
        assertEquals(0, plugin.productFlavors.size())


        List<ApplicationVariant> variants = plugin.variants
        assertEquals(3, variants.size()) // includes the test variant(s)

        ApplicationVariant debugVariant = findVariant(variants, "Debug")
        assertNotNull(debugVariant)

        ApplicationVariant release = findVariant(variants, "Release")
        assertNotNull(release)

        ApplicationVariant test = findVariant(variants, "Test")
        assertNotNull(test)
    }

    public void testDefaultConfig() {
        Project project = ProjectBuilder.builder().withProjectDir(
                new File(testDir, "basic")).build()

        project.apply plugin: 'android'

        project.android {
            target "android-15"

            defaultConfig {
                versionCode 1
                versionName "2.0"
                minSdkVersion 2
                targetSdkVersion 3

                signingStoreLocation "aa"
                signingStorePassword "bb"
                signingKeyAlias "cc"
                signingKeyPassword "dd"
            }
        }

        AppPlugin plugin = AppPlugin.pluginHolder.plugin
        plugin.createAndroidTasks()

        assertEquals(1, plugin.extension.defaultConfig.versionCode)
        assertEquals(2, plugin.extension.defaultConfig.minSdkVersion)
        assertEquals(3, plugin.extension.defaultConfig.targetSdkVersion)
        assertEquals("2.0", plugin.extension.defaultConfig.versionName)

        assertEquals("aa", plugin.extension.defaultConfig.signingStoreLocation)
        assertEquals("bb", plugin.extension.defaultConfig.signingStorePassword)
        assertEquals("cc", plugin.extension.defaultConfig.signingKeyAlias)
        assertEquals("dd", plugin.extension.defaultConfig.signingKeyPassword)
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

        AppPlugin plugin = AppPlugin.pluginHolder.plugin
        plugin.createAndroidTasks()

        assertEquals(3, plugin.buildTypes.size())

        List<ApplicationVariant> variants = plugin.variants
        assertEquals(4, variants.size()) // includes the test variant(s)

        ApplicationVariant debugVariant = findVariant(variants, "Debug")
        assertNotNull(debugVariant)

        ApplicationVariant releaseVariant = findVariant(variants, "Release")
        assertNotNull(releaseVariant)

        ApplicationVariant stagingVariant = findVariant(variants, "Staging")
        assertNotNull(stagingVariant)

        ApplicationVariant testVariant = findVariant(variants, "Test")
        assertNotNull(testVariant)
        assertEquals("staging", testVariant.config.buildType.name)
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

        AppPlugin plugin = AppPlugin.pluginHolder.plugin
        plugin.createAndroidTasks()

        assertEquals(2, plugin.productFlavors.size())

        List<ApplicationVariant> variants = plugin.variants
        assertEquals(6, variants.size()) // includes the test variant(s)

        assertNotNull(findVariant(variants, "Flavor1Debug"))
        assertNotNull(findVariant(variants, "Flavor1Release"))
        assertNotNull(findVariant(variants, "Flavor1Test"))
        assertNotNull(findVariant(variants, "Flavor2Debug"))
        assertNotNull(findVariant(variants, "Flavor2Release"))
        assertNotNull(findVariant(variants, "Flavor2Test"))
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

        AppPlugin plugin = AppPlugin.pluginHolder.plugin
        plugin.createAndroidTasks()

        assertEquals(5, plugin.productFlavors.size())

        List<ApplicationVariant> variants = plugin.variants
        assertEquals(18, variants.size())   // includes the test variant(s)

        assertNotNull(findVariant(variants, "F1FaDebug"))
        assertNotNull(findVariant(variants, "F1FbDebug"))
        assertNotNull(findVariant(variants, "F1FcDebug"))
        assertNotNull(findVariant(variants, "F2FaDebug"))
        assertNotNull(findVariant(variants, "F2FbDebug"))
        assertNotNull(findVariant(variants, "F2FcDebug"))
        assertNotNull(findVariant(variants, "F1FaRelease"))
        assertNotNull(findVariant(variants, "F1FbRelease"))
        assertNotNull(findVariant(variants, "F1FcRelease"))
        assertNotNull(findVariant(variants, "F2FaRelease"))
        assertNotNull(findVariant(variants, "F2FbRelease"))
        assertNotNull(findVariant(variants, "F2FcRelease"))
        assertNotNull(findVariant(variants, "F1FaTest"))
        assertNotNull(findVariant(variants, "F1FbTest"))
        assertNotNull(findVariant(variants, "F1FcTest"))
        assertNotNull(findVariant(variants, "F2FaTest"))
        assertNotNull(findVariant(variants, "F2FbTest"))
        assertNotNull(findVariant(variants, "F2FcTest"))
    }

    private ApplicationVariant findVariant(Collection<ApplicationVariant> variants, String name) {
        for (ApplicationVariant variant : variants) {
            if (name.equals(variant.name)) {
                return variant
            }
        }

        return null
    }
}
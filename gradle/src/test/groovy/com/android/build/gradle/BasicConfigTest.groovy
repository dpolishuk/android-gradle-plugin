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

import com.android.build.gradle.internal.PluginHolder
import com.android.builder.BuildType
import junit.framework.TestCase
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

import java.security.CodeSource

public class BasicConfigTest extends TestCase {

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
        assertEquals(3, plugin.variants.size()) // includes the test variant(s)
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

            buildTypes {
                staging {

                }
            }
        }

        AppPlugin plugin = AppPlugin.pluginHolder.plugin
        plugin.createAndroidTasks()

        assertEquals(3, plugin.buildTypes.size())
        assertEquals(4, plugin.variants.size())  // includes the test variant(s)
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
        assertEquals(6, plugin.variants.size()) // includes the test variant(s)
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
        assertEquals(18, plugin.variants.size()) // includes the test variant(s)
    }


    /**
     * Returns the Android source tree root dir.
     * @return the root dir or null if it couldn't be computed.
     */
    private File getTestDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource()
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = new File(location.toURI())
                assertTrue(dir.getPath(), dir.exists())
                System.out.println(dir.absolutePath)

                File rootDir = dir.getParentFile().getParentFile().getParentFile().getParentFile()

                return new File(rootDir, "tests")
            } catch (URISyntaxException e) {
                fail(e.getLocalizedMessage())
            }
        }

        fail("Fail to get tests folder")
    }
}
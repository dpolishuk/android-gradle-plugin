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

import junit.framework.TestCase

import java.security.CodeSource

/**
 * Base class for tests.
 */
public abstract class BaseTest extends TestCase {

    /**
     * Returns the gradle plugin test folder.
     */
    protected File getRootDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource()
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = new File(location.toURI())
                assertTrue(dir.getPath(), dir.exists())

                return dir.getParentFile().getParentFile().getParentFile().getParentFile()
            } catch (URISyntaxException e) {
                fail(e.getLocalizedMessage())
            }
        }

        fail("Fail to get tests folder")
    }

    /**
     * Returns the root folder for the tests projects.
     */
    protected File getTestDir() {
        File rootDir = getRootDir()
        return new File(rootDir, "tests")
    }

    /**
     * Returns the SDK folder as built from the Android source tree.
     * @return
     */
    protected File getSdkDir() {
        // get the gradle project root dir.
        File rootDir = getRootDir()

        // go up twice and get the root Android dir.
//        File androidRootDir = rootDir.getParentFile().getParentFile()
        return new File("/home/dp/Downloads/android-sdk-linux/")

//        // get the sdk folder
//        File sdk = new File(androidRootDir, "out" + File.separatorChar + "host" + File.separatorChar + "darwin-x86" + File.separatorChar + "sdk")
//
//        File[] files = sdk.listFiles(new FilenameFilter() {
//
//            @Override
//            boolean accept(File file, String s) {
//                return s.startsWith("android-sdk_") && new File(file,s ).isDirectory()
//            }
//        })
//
//        if (files.length == 1) {
//            return files[0]
//        }
    }
}

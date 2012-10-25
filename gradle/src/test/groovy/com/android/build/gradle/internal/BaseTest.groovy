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
     * Returns the Android source tree root dir.
     * @return the root dir or null if it couldn't be computed.
     */
    protected File getTestDir() {
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

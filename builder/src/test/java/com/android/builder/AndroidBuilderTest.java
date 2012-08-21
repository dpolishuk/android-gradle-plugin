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

package com.android.builder;

import com.android.utils.StdLogger;

import junit.framework.TestCase;

public class AndroidBuilderTest extends TestCase {

    private ProductFlavor mMain;
    private ProductFlavor mFlavor;
    private BuildType mDebug;

    private static class ManifestParserMock implements ManifestParser {

        private final String mPackageName;

        ManifestParserMock(String packageName) {
            mPackageName = packageName;
        }

        @Override
        public String getPackage(String manifestFile) {
            return mPackageName;
        }
    }

    @Override
    protected void setUp() throws Exception {
        mMain = new ProductFlavor("main");
        mFlavor = new ProductFlavor("flavor");
        mDebug = new BuildType("debug");
    }

    public void testPackageOverrideNone() {
        AndroidBuilder builder = new AndroidBuilder(new DefaultSdkParser(""),
                new StdLogger(StdLogger.Level.ERROR), false /*verboseExec*/);

        builder.setBuildVariant(mMain, mFlavor, mDebug);

        assertNull(builder.getPackageOverride(""));
    }

    public void testPackageOverridePackageFromFlavor() {
        AndroidBuilder builder = new AndroidBuilder(new DefaultSdkParser(""),
                new StdLogger(StdLogger.Level.ERROR), false /*verboseExec*/);

        mFlavor.setPackageName("foo.bar");

        builder.setBuildVariant(mMain, mFlavor, mDebug);

        assertEquals("foo.bar", builder.getPackageOverride(""));
    }

    public void testPackageOverridePackageFromFlavorWithSuffix() {
        AndroidBuilder builder = new AndroidBuilder(new DefaultSdkParser(""),
                new StdLogger(StdLogger.Level.ERROR), false /*verboseExec*/);

        mFlavor.setPackageName("foo.bar");
        mDebug.setPackageNameSuffix(".fortytwo");

        builder.setBuildVariant(mMain, mFlavor, mDebug);

        assertEquals("foo.bar.fortytwo", builder.getPackageOverride(""));
    }

    public void testPackageOverridePackageFromFlavorWithSuffix2() {
        AndroidBuilder builder = new AndroidBuilder(new DefaultSdkParser(""),
                new StdLogger(StdLogger.Level.ERROR), false /*verboseExec*/);

        mFlavor.setPackageName("foo.bar");
        mDebug.setPackageNameSuffix("fortytwo");

        builder.setBuildVariant(mMain, mFlavor, mDebug);

        assertEquals("foo.bar.fortytwo", builder.getPackageOverride(""));
    }

    public void testPackageOverridePackageWithSuffixOnly() {
        StdLogger logger = new StdLogger(StdLogger.Level.ERROR);

        AndroidBuilder builder = new AndroidBuilder(
                new DefaultSdkParser(""),
                new ManifestParserMock("fake.package.name"),
                new CommandLineRunner(logger),
                logger,
                false /*verboseExec*/);

        mDebug.setPackageNameSuffix("fortytwo");

        builder.setBuildVariant(mMain, mFlavor, mDebug);

        assertEquals("fake.package.name.fortytwo", builder.getPackageOverride(""));
    }
}

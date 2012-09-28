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
import com.android.utils.ILogger
import org.gradle.api.artifacts.ModuleVersionIdentifier

/**
 * Checks for dependencies to ensure Android compatibility
 */
class DependencyChecker {

    final ApplicationVariant variant
    final logger
    final int minSdkVersion

    DependencyChecker(ApplicationVariant variant, ILogger logger) {
        this.variant = variant
        this.logger = logger;
        this.minSdkVersion = variant.config.getMinSdkVersion()
    }

    private boolean excluded(ModuleVersionIdentifier id) {
        if (id.group == 'com.google.android' && id.name == 'android') {
            int moduleLevel = getApiLevelFromMavenArtifact(id.version)

            if (minSdkVersion < moduleLevel) {
                throw new RuntimeException(String.format(
                        "ERROR: Android API level %d is in the dependency graph, but minSdkVersion for '%s' is %d",
                        moduleLevel, variant.name, minSdkVersion))
            }

            logger.info("Ignoring Android API artifact: " + id)
            return true
        }

        if ((id.group == 'org.apache.httpcomponents' && id.name == 'httpclient') ||
                (id.group == 'xpp3' && id.name == 'xpp3') ||
                (id.group == 'commons-logging' && id.name == 'commons-logging') ||
                (id.group == 'xerces' && id.name == 'xmlParserAPIs')) {

            logger.warning(
                    "WARNING: Dependency %s is ignored as it may be conflicting with the internal version provided by Android.\n" +
                    "         In case of problem, please repackage with jarjar to change the class packages",
                    id)
            return true;
        }

        if (id.group == 'org.json' && id.name == 'json') {
            logger.warning(
                    "WARNING: Dependency %s is ignored as it may be conflicting with the internal version provided by Android.\n" +
                            "         In case of problem, please repackage with jarjar to change the class packages",
                    id)
            return true
        }

        if (id.group == 'org.khronos' && id.name == 'opengl-api') {
            logger.warning(
                    "WARNING: Dependency %s is ignored as it may be conflicting with the internal version provided by Android.\n" +
                            "         In case of problem, please repackage with jarjar to change the class packages",
                    id)
            return true
        }

        if (id.group == 'org.bouncycastle' && id.name.startsWith("bcprov")) {
            if (minSdkVersion >= 11) {
                // this is when the version of BouncyCastle inside Android was jarjar'ed so this
                // is fine
                return false;
            }

            // TODO check which version of BC is used by the platform and the app.
            throw new RuntimeException(String.format(
                    "ERROR: Dependency %s is conflicting with the internal version provided by Android. To use, please repackage with jarjar to change the class packages",
                    id))
        }

        return false
    }


    private int getApiLevelFromMavenArtifact(String version) {
        switch (version) {
            case "1.5_r3":
            case "1.5_r4":
                return 3;
            case "1.6_r2":
                return 4;
            case "2.1_r1":
            case "2.1.2":
                return 7;
            case "2.2.1":
                return 8;
            case "2.3.1":
                return 9;
            case "2.3.3":
                return 10;
            case "4.0.1.2":
                return 14;
            case "4.1.1.4":
                return 15;
        }
    }
}

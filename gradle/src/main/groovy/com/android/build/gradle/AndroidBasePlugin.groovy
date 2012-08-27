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
import com.android.build.gradle.internal.ProductFlavorData
import com.android.builder.AndroidBuilder
import com.android.builder.DefaultSdkParser
import com.android.builder.ProductFlavor
import com.android.builder.SdkParser
import com.android.utils.ILogger
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.SourceSet

/**
 * Base class for all Android plugins
 */
abstract class AndroidBasePlugin {

    private final Map<Object, AndroidBuilder> builders = [:]

    protected Project project
    protected File sdkDir
    private DefaultSdkParser androidSdkParser
    private AndroidLogger androidLogger

    private ProductFlavorData defaultConfigData
    protected SourceSet mainSourceSet
    protected SourceSet testSourceSet

    abstract String getTarget()

    protected void apply(Project project) {
        this.project = project
        project.apply plugin: JavaBasePlugin

        mainSourceSet = project.sourceSets.add("main")
        testSourceSet = project.sourceSets.add("test")

        project.tasks.assemble.description = "Assembles all variants of all applications, and secondary packages."
    }

    protected setDefaultConfig(ProductFlavor defaultConfig) {
        defaultConfigData = new ProductFlavorData(defaultConfig, mainSourceSet,
                testSourceSet, project)
    }

    ProductFlavorData getDefaultConfigData() {
        return defaultConfigData
    }

    SdkParser getSdkParser() {
        if (androidSdkParser == null) {
            androidSdkParser = new DefaultSdkParser(sdkDir.absolutePath)
        }

        return androidSdkParser;
    }

    ILogger getLogger() {
        if (androidLogger == null) {
            androidLogger = new AndroidLogger(project.logger)
        }

        return androidLogger
    }

    boolean isVerbose() {
        return project.logger.isEnabled(LogLevel.DEBUG)
    }

    AndroidBuilder getAndroidBuilder(Object key) {
        return builders.get(key)
    }

    void setAndroidBuilder(Object key, AndroidBuilder androidBuilder) {
        builders.put(key, androidBuilder)
    }

    protected void findSdk(Project project) {
        def localProperties = project.file("local.properties")
        if (localProperties.exists()) {
            Properties properties = new Properties()
            localProperties.withInputStream { instr ->
                properties.load(instr)
            }
            def sdkDirProp = properties.getProperty('sdk.dir')
            if (!sdkDirProp) {
                throw new RuntimeException("No sdk.dir property defined in local.properties file.")
            }
            sdkDir = new File(sdkDirProp)
        } else {
            def envVar = System.getenv("ANDROID_HOME")
            if (envVar != null) {
                sdkDir = new File(envVar)
            }
        }

        if (sdkDir == null) {
            throw new RuntimeException("SDK location not found. Define location with sdk.dir in the local.properties file or with an ANDROID_HOME environment variable.")
        }

        if (!sdkDir.directory) {
            throw new RuntimeException("The SDK directory '$sdkDir' specified in local.properties does not exist.")
        }
    }

    protected String getRuntimeJars(ApplicationVariant variant) {
        AndroidBuilder androidBuilder = getAndroidBuilder(variant)

        return androidBuilder.runtimeClasspath.join(":")
    }
}

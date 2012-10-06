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

package com.android.build.gradle.internal;

import com.android.build.gradle.AndroidSourceDirectory;
import com.android.build.gradle.AndroidSourceFile;
import com.android.build.gradle.AndroidSourceSet;
import com.android.builder.SourceProvider;
import groovy.lang.Closure;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.GUtil;

import java.io.File;

/**
 */
public class DefaultAndroidSourceSet implements AndroidSourceSet, SourceProvider {
    private final String name;
    private final SourceDirectorySet javaSource;
    private final SourceDirectorySet allJavaSource;
    private final SourceDirectorySet javaResources;
    private final AndroidSourceFile manifest;
    private final AndroidSourceDirectory assets;
    private final AndroidSourceDirectory resources;
    private final AndroidSourceDirectory aidl;
    private final AndroidSourceDirectory renderscript;
    private final AndroidSourceDirectory jni;
    private final String displayName;
    private final SourceDirectorySet allSource;

    public DefaultAndroidSourceSet(String name, FileResolver fileResolver) {
        this.name = name;
        displayName = GUtil.toWords(this.name);

        String javaSrcDisplayName = String.format("%s Java source", displayName);

        javaSource = new DefaultSourceDirectorySet(javaSrcDisplayName, fileResolver);
        javaSource.getFilter().include("**/*.java");

        allJavaSource = new DefaultSourceDirectorySet(javaSrcDisplayName, fileResolver);
        allJavaSource.getFilter().include("**/*.java");
        allJavaSource.source(javaSource);

        String javaResourcesDisplayName = String.format("%s Java resources", displayName);
        javaResources = new DefaultSourceDirectorySet(javaResourcesDisplayName, fileResolver);
        javaResources.getFilter().exclude(new Spec<FileTreeElement>() {
            public boolean isSatisfiedBy(FileTreeElement element) {
                return javaSource.contains(element.getFile());
            }
        });

        String allSourceDisplayName = String.format("%s source", displayName);
        allSource = new DefaultSourceDirectorySet(allSourceDisplayName, fileResolver);
        allSource.source(javaResources);
        allSource.source(javaSource);

        String manifestDisplayName = String.format("%s manifest", displayName);
        manifest = new DefaultAndroidSourceFile(manifestDisplayName, fileResolver);

        String assetsDisplayName = String.format("%s assets", displayName);
        assets = new DefaultAndroidSourceDirectory(assetsDisplayName, fileResolver);

        String resourcesDisplayName = String.format("%s resources", displayName);
        resources = new DefaultAndroidSourceDirectory(resourcesDisplayName, fileResolver);

        String aidlDisplayName = String.format("%s aidl", displayName);
        aidl = new DefaultAndroidSourceDirectory(aidlDisplayName, fileResolver);

        String renderscriptDisplayName = String.format("%s renderscript", displayName);
        renderscript = new DefaultAndroidSourceDirectory(renderscriptDisplayName, fileResolver);

        String jniDisplayName = String.format("%s jni", displayName);
        jni = new DefaultAndroidSourceDirectory(jniDisplayName, fileResolver);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("source set %s", getDisplayName());
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getCompileConfigurationName() {
        if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
            return "compile";
        } else {
            return String.format("%sCompile", name);
        }
    }

    @Override
    public String getPackageConfigurationName() {
        if (name.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
            return "package";
        } else {
            return String.format("%sPackage", name);
        }
    }

    @Override
    public AndroidSourceFile getManifest() {
        return manifest;
    }

    @Override
    public AndroidSourceSet manifest(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getManifest());
        return this;
    }

    @Override
    public AndroidSourceDirectory getResources() {
        return resources;
    }

    @Override
    public AndroidSourceSet resources(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getResources());
        return this;
    }

    @Override
    public AndroidSourceDirectory getAssets() {
        return assets;
    }

    @Override
    public AndroidSourceSet assets(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getAssets());
        return this;
    }

    @Override
    public AndroidSourceDirectory getAidl() {
        return aidl;
    }

    @Override
    public AndroidSourceSet aidl(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getAidl());
        return this;
    }

    @Override
    public AndroidSourceDirectory getRenderscript() {
        return renderscript;
    }

    @Override
    public AndroidSourceSet renderscript(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getRenderscript());
        return this;
    }

    @Override
    public AndroidSourceDirectory getJni() {
        return jni;
    }

    @Override
    public AndroidSourceSet jni(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getJni());
        return this;
    }

    @Override
    public SourceDirectorySet getJava() {
        return javaSource;
    }

    @Override
    public AndroidSourceSet java(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getJava());
        return this;
    }

    @Override
    public SourceDirectorySet getAllJava() {
        return allJavaSource;
    }

    @Override
    public SourceDirectorySet getJavaResources() {
        return javaResources;
    }

    @Override
    public AndroidSourceSet javaResources(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getJavaResources());
        return this;
    }

    @Override
    public SourceDirectorySet getAllSource() {
        return allSource;
    }

    // --- SourceProvider

    @Override
    public File getManifestFile() {
        return getManifest().getFile();
    }

    @Override
    public File getAidlDir() {
        return getAidl().getDirectory();
    }

    @Override
    public File getRenderscriptDir() {
        return getRenderscript().getDirectory();
    }

    @Override
    public File getJniDir() {
        return getJni().getDirectory();
    }

    @Override
    public File getResourcesDir() {
        return getResources().getDirectory();
    }

    @Override
    public File getAssetsDir() {
        return getAssets().getDirectory();
    }
}

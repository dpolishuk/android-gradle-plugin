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

import com.android.builder.AndroidDependency;
import com.android.builder.BundleDependency;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.diagnostics.internal.GraphRenderer;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.logging.StyledTextOutput;
import org.gradle.util.GUtil;

import java.io.IOException;
import java.util.List;

import static org.gradle.logging.StyledTextOutput.Style.Description;
import static org.gradle.logging.StyledTextOutput.Style.Identifier;
import static org.gradle.logging.StyledTextOutput.Style.Info;

/**
 * android version of the AsciiReportRenderer that outputs Android Library dependencies.
 */
public class AndroidAsciiReportRenderer extends TextReportRenderer {
    private boolean hasConfigs;
    private boolean hasCyclicDependencies;
    private GraphRenderer renderer;

    @Override
    public void startProject(Project project) {
        super.startProject(project);
        hasConfigs = false;
        hasCyclicDependencies = false;
    }

    @Override
    public void completeProject(Project project) {
        if (!hasConfigs) {
            getTextOutput().withStyle(Info).println("No dependencies");
        }
        super.completeProject(project);
    }

    public void startVariant(final ApplicationVariant variant) {
        if (hasConfigs) {
            getTextOutput().println();
        }
        hasConfigs = true;
        renderer = new GraphRenderer(getTextOutput());
        renderer.visit(new Action<StyledTextOutput>() {
            public void execute(StyledTextOutput styledTextOutput) {
                getTextOutput().withStyle(Identifier).text(variant.getName());
                getTextOutput().withStyle(Description).text("");
            }
        }, true);
    }

    private String getDescription(Configuration configuration) {
        return GUtil.isTrue(
                configuration.getDescription()) ? " - " + configuration.getDescription() : "";
    }

    public void completeConfiguration(ApplicationVariant variant) {}

    public void render(ApplicationVariant variant) throws IOException {
        List<AndroidDependency> libraries = variant.getVariantConfiguration().getDirectLibraries();

        renderNow(libraries);
    }

    void renderNow(List<AndroidDependency> libraries) {
        if (libraries.isEmpty()) {
            getTextOutput().withStyle(Info).text("No dependencies");
            getTextOutput().println();
            return;
        }

        renderChildren(libraries);
    }

    public void complete() throws IOException {
        if (hasCyclicDependencies) {
            getTextOutput().withStyle(Info).println(
                    "\n(*) - dependencies omitted (listed previously)");
        }

        super.complete();
    }

    private void render(final AndroidDependency lib, boolean lastChild) {
        renderer.visit(new Action<StyledTextOutput>() {
            public void execute(StyledTextOutput styledTextOutput) {
                getTextOutput().text(((BundleDependency)lib).getName());
            }
        }, lastChild);

        renderChildren(lib.getDependencies());
    }

    private void renderChildren(List<AndroidDependency> libraries) {
        renderer.startChildren();
        for (int i = 0; i < libraries.size(); i++) {
            AndroidDependency lib = libraries.get(i);
            render(lib, i == libraries.size() - 1);
        }
        renderer.completeChildren();
    }
}

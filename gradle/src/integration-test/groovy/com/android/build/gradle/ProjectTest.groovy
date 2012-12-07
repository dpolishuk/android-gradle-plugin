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
import com.android.sdklib.internal.project.ProjectProperties
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy
import com.google.common.collect.Sets
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

import javax.imageio.ImageIO
import java.awt.image.BufferedImage

/**
 */
class ProjectTest extends BaseTest {

    private File testDir
    private File sdkDir
    private static Set<String> builtProjects = Sets.newHashSet()

    @Override
    protected void setUp() throws Exception {
        testDir = getTestDir()
        sdkDir = getSdkDir()
    }

    void testAidl() {
        buildProject("aidl")
    }

    void testApi() {
        buildProject("api")
    }

    void testAppLibTest() {
        buildProject("applibtest")
    }

    void testBasic() {
        buildProject("basic")
    }

    void testDependencies() {
        buildProject("dependencies")
    }

    void testFlavored() {
        buildProject("flavored")
    }

    void testFlavorLib() {
        buildProject("flavorlib")
    }

    void testFlavors() {
        buildProject("flavors")
    }

    void testlibsTest() {
        buildProject("libsTest")
    }

    void testMigrated() {
        buildProject("migrated")
    }

    void testMultiProject() {
        buildProject("multiproject")
    }

    void testOverlay1() {
        File project = buildProject("overlay1")
        File drawableOutput = new File(project, "build/res/debug/drawable" )

        checkImageColor(drawableOutput, "no_overlay.png", (int) 0xFF00FF00)
        checkImageColor(drawableOutput, "type_overlay.png", (int) 0xFF00FF00)
    }

    void testOverlay2() {
        File project = buildProject("overlay2")
        File drawableOutput = new File(project, "build/res/one/debug/drawable" )

        checkImageColor(drawableOutput, "no_overlay.png", (int) 0xFF00FF00)
        checkImageColor(drawableOutput, "type_overlay.png", (int) 0xFF00FF00)
        checkImageColor(drawableOutput, "flavor_overlay.png", (int) 0xFF00FF00)
        checkImageColor(drawableOutput, "type_flavor_overlay.png", (int) 0xFF00FF00)
    }

    void testRepo() {
        // this is not an actual project, but we add it so that the catch-all below doesn't
        // try to build it again
        builtProjects.add("repo")

        File repo = new File(testDir, "repo")

        try {
            buildProject(new File(repo, "util"), "clean", "uploadArchives")
            buildProject(new File(repo, "baseLibrary"), "clean", "uploadArchives")
            buildProject(new File(repo, "library"), "clean", "uploadArchives")
            buildProject(new File(repo, "app"), "clean", "assemble")
        } finally {
            // clean up the test repository.
            File testrepo = new File(repo, "testrepo")
            testrepo.deleteDir()
        }
    }

    void testTicTacToe() {
        buildProject("tictactoe")
    }

    void testOtherProjects() {
        File[] projects = testDir.listFiles()
        for (File project : projects) {
            String name = project.name
            if (!builtProjects.contains(name)) {
                buildProject(name)
            }
        }
    }

    private File buildProject(String name) {
        File project = new File(testDir, name)
        builtProjects.add(name)

        buildProject(project, "clean", "assemble")

        return project;
    }

    private void buildProject(File project, String... tasks) {
        File localProp = createLocalProp(project)

        try {

            GradleConnector connector = GradleConnector.newConnector()

            ProjectConnection connection = connector
                    .useGradleVersion("1.2")
                    .forProjectDirectory(project)
                    .connect()

            connection.newBuild().forTasks(tasks).run()
        } finally {
            localProp.delete()
        }
    }


    private File createLocalProp(File project) {
        ProjectPropertiesWorkingCopy localProp = ProjectProperties.create(
                project.absolutePath, ProjectProperties.PropertyType.LOCAL)
        localProp.setProperty(ProjectProperties.PROPERTY_SDK, sdkDir.absolutePath)
        localProp.save()

        return (File) localProp.file
    }

    private void checkImageColor(File folder, String fileName, int expectedColor) {
        File f = new File(folder, fileName)
        assertTrue("File '" + f.getAbsolutePath() + "' does not exist.", f.isFile())

        BufferedImage image = ImageIO.read(f)
        int rgb = image.getRGB(0, 0)
        assertEquals(String.format("Expected: 0x%08X, actual: 0x%08X for file %s",
                expectedColor, rgb, f),
                expectedColor, rgb);
    }
}

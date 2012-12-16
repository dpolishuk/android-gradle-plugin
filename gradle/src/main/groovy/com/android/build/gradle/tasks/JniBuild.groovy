package com.android.build.gradle.tasks

import com.android.build.gradle.internal.tasks.BaseTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * User: Dmitry Polishuk <dmitry.polishuk@gmail.com>
 * Date: 12/10/12
 * Time: 1:16 AM
 */
class JniBuild extends BaseTask {
    @Input
    File ndkDir

    @TaskAction
    void generate() {
        project.exec {
            executable = new File(ndkDir, "ndk-build")
            args 'all'
        }
    }
}

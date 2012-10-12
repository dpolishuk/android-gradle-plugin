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

import com.android.utils.ILogger
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger

/**
 * Implementation of Android's {@link ILogger} over gradle's {@link Logger}.
 */
class LoggerWrapper implements ILogger {

    private final Logger logger

    LoggerWrapper(Logger logger) {
        this.logger = logger;
    }

    @Override
    void error(Throwable throwable, String s, Object... objects) {
        if (objects != null && objects.length > 0) {
            s = String.format(s, objects)
        }

        if (throwable == null) {
            logger.log(LogLevel.ERROR, s)

        } else {
            logger.log(LogLevel.ERROR, s, throwable)
        }
    }

    @Override
    void warning(String s, Object... objects) {
        if (objects == null || objects.length == 0) {
            logger.log(LogLevel.WARN, s)
        } else {
            logger.log(LogLevel.WARN, String.format(s, objects))
        }
    }

    @Override
    void info(String s, Object... objects) {
        if (objects == null || objects.length == 0) {
            logger.log(LogLevel.INFO, s)
        } else {
            logger.log(LogLevel.INFO, String.format(s, objects))
        }
    }

    @Override
    void verbose(String s, Object... objects) {
        if (objects == null || objects.length == 0) {
            logger.log(LogLevel.INFO, s)

        } else {
            logger.log(LogLevel.INFO, String.format(s, objects))
        }
    }
}

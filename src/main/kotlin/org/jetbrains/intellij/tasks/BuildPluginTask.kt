// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.intellij.IntelliJPluginConstants

/**
 * Assembles a plugin and prepares ZIP archive for deployment.
 *
 * @see [Zip]
 */
abstract class BuildPluginTask : Zip() {

    init {
        group = IntelliJPluginConstants.PLUGIN_GROUP_NAME
        description = "Assembles plugin and prepares ZIP archive for deployment."
    }
}

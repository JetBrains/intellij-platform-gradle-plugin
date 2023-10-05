// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import java.io.File

@CacheableTask
abstract class DownloadIdeaProductReleasesXmlTask : Sync() {

    /**
     * Represents the property that holds the URL for releases.
     * Default value: [IntelliJPluginConstants.IDEA_PRODUCTS_RELEASES_URL]
     */
    @get:Input
    @get:Optional
    abstract val releasesUrl: Property<String>

    @get:Internal
    abstract val inputFile: Property<File>

    init {
        group = PLUGIN_GROUP_NAME
        description = "Downloads XML files containing the IntelliJ IDEA product release information."
    }
}

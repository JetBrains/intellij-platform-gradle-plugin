// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.Sync
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants
import org.jetbrains.intellij.platform.gradleplugin.logCategory

@CacheableTask
abstract class DownloadAndroidStudioProductReleasesXmlTask : Sync() {

    private val context = logCategory()

    /**
     * Represents the property that holds the URL for releases.
     * Default value: [IntelliJPluginConstants.ANDROID_STUDIO_PRODUCTS_RELEASES_URL]
     */
    @get:Input
    @get:Optional
    abstract val releasesUrl: Property<String>

    init {
        group = IntelliJPluginConstants.PLUGIN_GROUP_NAME
        description = "Downloads XML files containing the Android Studio product release information."
    }
}

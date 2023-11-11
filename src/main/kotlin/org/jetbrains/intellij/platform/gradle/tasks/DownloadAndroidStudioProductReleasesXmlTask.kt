// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.Sync
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Locations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks

@CacheableTask
abstract class DownloadAndroidStudioProductReleasesXmlTask : Sync() {

    /**
     * Represents the property that holds the URL for releases.
     * Default value: [Locations.ANDROID_STUDIO_PRODUCTS_RELEASES_URL]
     */
    @get:Input
    @get:Optional
    abstract val releasesUrl: Property<String>

    init {
        group = PLUGIN_GROUP_NAME
        description = "Downloads XML files containing the Android Studio product release information."
    }

    companion object {
        fun register(project: Project) =
            project.registerTask<DownloadAndroidStudioProductReleasesXmlTask>(Tasks.DOWNLOAD_ANDROID_STUDIO_PRODUCT_RELEASES_XML) {
                releasesUrl.convention(Locations.ANDROID_STUDIO_PRODUCTS_RELEASES_URL)

                from(releasesUrl.map {
                    project.resolveResourceFromUrl(it)
                }) {
                    rename { "android_studio_product_releases.xml" }
                }
                into(temporaryDir)
            }
    }
}

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
abstract class DownloadIdeaProductReleasesXmlTask : Sync() {

    /**
     * Represents the property that holds the URL for releases.
     * Default value: [Locations.IDEA_PRODUCTS_RELEASES_LIST]
     */
    @get:Input
    @get:Optional
    abstract val releasesUrl: Property<String>

    init {
        group = PLUGIN_GROUP_NAME
        description = "Downloads XML files containing the IntelliJ IDEA product release information."
    }

    companion object {
        fun register(project: Project) =
            project.registerTask<DownloadIdeaProductReleasesXmlTask>(Tasks.DOWNLOAD_IDEA_PRODUCT_RELEASES_XML) {
                releasesUrl.convention(Locations.IDEA_PRODUCTS_RELEASES_LIST)

                from(releasesUrl.map {
                    project.resolveResourceFromUrl(it)
                }) {
                    rename { "idea_product_releases.xml" }
                }
                into(temporaryDir)
            }
    }
}

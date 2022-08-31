// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.kotlin.dsl.property
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.warn

@CacheableTask
open class BuildSearchableOptionsTask : RunIdeBase(false) {

    /**
     * The directory where the searchable options will be generated.
     */
    @get:OutputDirectory
    val outputDir = objectFactory.directoryProperty()

    /**
     * Emit warning if the task is executed by a paid plugin.
     * Can be disabled with [org.jetbrains.intellij.BuildFeature.PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING].
     */
    @get:Internal
    val showPaidPluginWarning = objectFactory.property<Boolean>()

    private val traverseUIArgs = listOf("traverseUI")
    private val context = logCategory()

    init {
        args = traverseUIArgs
    }

    override fun exec() {
        if (showPaidPluginWarning.get()) {
            warn(
                context,
                "Due to IDE limitations, it is impossible to run the IDE in headless mode to collect searchable options for " +
                    "a paid plugin. As paid plugins require providing a valid license and presenting a UI dialog, it is impossible " +
                    "to handle such a case, and the task will fail. Please consider disabling the task in the Gradle configuration. " +
                    "See: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-building-searchable-option"
            )
        }

        args = args + listOf(outputDir.get().asFile.canonicalPath, "true")
        super.exec()
    }

    override fun setArgs(applicationArgs: List<String>?) =
        super.setArgs(traverseUIArgs.union(applicationArgs?.toList() ?: emptyList()))

    override fun setArgs(applicationArgs: MutableIterable<*>?) =
        super.setArgs(traverseUIArgs.union(applicationArgs?.toList() ?: emptyList()))
}

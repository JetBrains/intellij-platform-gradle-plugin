// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider


/**
 * Creates a JAR file with searchable options to be distributed with the plugin.
 */
@CacheableTask
abstract class JarSearchableOptionsTask : Jar() {

    /**
     * Specifies if a warning is emitted when no searchable options are found.
     * Can be disabled with [GradleProperties.NoSearchableOptionsWarning] Gradle property.
     *
     * Default value: [GradleProperties.NoSearchableOptionsWarning]
     */
    @get:Internal
    abstract val noSearchableOptionsWarning: Property<Boolean>

    private val log = Logger(javaClass)

    @TaskAction
    override fun copy() {

        if (noSearchableOptionsWarning.get()) {
            val noSearchableOptions = source.none {
                it.name.endsWith(SEARCHABLE_OPTIONS_SUFFIX_XML) || it.name.endsWith(SEARCHABLE_OPTIONS_SUFFIX_JSON)
            }
            if (noSearchableOptions) {
                log.warn(
                    "No searchable options found. If the plugin does not provide custom settings, " +
                            "disable building searchable options to improve build performance. " +
                            "See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-buildSearchableOptions"
                )
            }
        }

        super.copy()
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Creates a Jar file with searchable options to be distributed with the plugin."

        includeEmptyDirs = false
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<JarSearchableOptionsTask>(Tasks.JAR_SEARCHABLE_OPTIONS) {
                val prepareJarSearchableOptionsTask = project.tasks.named<PrepareJarSearchableOptionsTask>(Tasks.PREPARE_JAR_SEARCHABLE_OPTIONS)
                val buildSearchableOptionsEnabledProvider = project.extensionProvider.flatMap { it.buildSearchableOptions }
                val runtimeElementsConfiguration = project.configurations[Configurations.External.RUNTIME_ELEMENTS]

                archiveClassifier.convention("searchableOptions")
                destinationDirectory.convention(project.layout.buildDirectory.dir("libs"))
                noSearchableOptionsWarning.convention(project.providers[GradleProperties.NoSearchableOptionsWarning])

                from(prepareJarSearchableOptionsTask)

                onlyIf {
                    buildSearchableOptionsEnabledProvider.get()
                }

                project.artifacts.add(runtimeElementsConfiguration.name, archiveFile)
            }
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.kotlin.dsl.assign
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.aware.ProductReleasesServiceAware
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.writeTextIfChanged
import kotlin.io.path.createDirectories

/**
 * Dumps all known IntelliJ Platform product releases to [outputFile].
 */
@UntrackedTask(because = "Product release listings are remote and time-dependent")
abstract class DumpProductsReleasesTask : DefaultTask(), ProductReleasesServiceAware {

    /**
     * The file to which product releases are written as tab-separated product code, version, and channel, one per line.
     */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun dumpProductsReleases() {
        val content = productReleasesService.get()
            .resolve {
                types = IntelliJPlatformType.entries.distinctBy { it.code }
                sinceBuild = Constraints.MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER.toString()
                channels = ProductRelease.Channel.entries
            }
            .get()
            .asSequence()
            .map { "${it.type.code}\t${it.notationVersion}\t${it.channel}" }
            .distinct()
            .sorted()
            .joinToString(System.lineSeparator(), postfix = System.lineSeparator())

        outputFile.asPath.parent.createDirectories()
        outputFile.asPath.writeTextIfChanged(content)
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Dumps all known IntelliJ Platform product releases to a file for Plugin DevKit plugin purposes."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<DumpProductsReleasesTask>(Tasks.DUMP_PRODUCTS_RELEASES) {
                outputFile.convention(
                    project.layout.buildDirectory.file("tmp/$name/product-releases.txt"),
                )
            }
    }
}

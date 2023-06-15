// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.propertyProviders

import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class PerformanceTestArgumentProvider(
    @InputDirectory @PathSensitive(RELATIVE) val scriptPath: Path,
    @InputDirectory @PathSensitive(RELATIVE) val testArtifactsDirPath: Path,
    private val profilerName: String,
) : CommandLineArgumentProvider {

    override fun asArguments() = listOf(
        "-Djdk.attach.allowAttachSelf=true",
        "-Didea.is.integration.test=true",
        "-Djb.privacy.policy.text=<!--999.999-->",
        "-Djb.consents.confirmation.enabled=false",
        "-Didea.local.statistics.without.report=true",
        "-Dlinux.native.menu.force.disable=true",
        "-Didea.fatal.error.notification=true",
        "-Dtestscript.filename=${scriptPath.absolutePathString()}",
        "-DintegrationTests.profiler=$profilerName",
        "-Dide.performance.screenshot.before.kill=${testArtifactsDirPath.absolutePathString()}",
        "-Didea.log.path=${testArtifactsDirPath.absolutePathString()}",
        "-Dsnapshots.path=${testArtifactsDirPath.absolutePathString()}",
        "-Dmemory.snapshots.path=${testArtifactsDirPath.absolutePathString()}",
    )
}

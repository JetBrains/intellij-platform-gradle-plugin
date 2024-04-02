// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Provides command line arguments for running performance tests.
 *
 * @property scriptPath The path to the performance test script.
 * @property testArtifactsDirectory The path to the directory where test artifacts will be stored.
 * @property profilerName The name of the profiler to be used.
 */
class PerformanceTestArgumentProvider(
    @InputDirectory @PathSensitive(RELATIVE) val scriptPath: Path,
    @InputDirectory @PathSensitive(RELATIVE) val testArtifactsDirectory: Path,
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
        "-Dtestscript.filename=${scriptPath.pathString}",
        "-DintegrationTests.profiler=$profilerName",
        "-Dide.performance.screenshot.before.kill=${testArtifactsDirectory.pathString}",
        "-Didea.log.path=${testArtifactsDirectory.pathString}",
        "-Dsnapshots.path=${testArtifactsDirectory.pathString}",
        "-Dmemory.snapshots.path=${testArtifactsDirectory.pathString}",
    )
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.Serializable
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.testing.Test
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.*

/** Potential execution costs, including prerequisites, rather than evidence that a task has run. */
@Serializable
internal enum class CapabilityCost { metadata, resolution, compile, ide, test, publish }

@Serializable
internal data class Capability(
    val task: String,
    val purpose: String,
    val prerequisites: List<String>,
    val executionCost: List<CapabilityCost>,
    val optionalExecutionCost: List<CapabilityCost> = emptyList(),
)

/** Discovers registered task names by type without realizing tasks or reading their resolving properties. */
internal fun Project.capabilities(): List<Capability> {
    val result = mutableMapOf<String, Capability>()
    val available = tasks.names
    val resolution = listOf(CapabilityCost.resolution)
    val compilation = resolution + CapabilityCost.compile
    val ide = compilation + CapabilityCost.ide
    val testing = compilation + CapabilityCost.test
    val ideTesting = ide + CapabilityCost.test
    val platform = listOf("Declare an IntelliJ Platform dependency; artifacts must be cached when offline.")
    val compiler = listOf("Configure a compatible compiler and compilation dependencies.")
    val sandbox = platform + "Prepare the plugin sandbox and a compatible IDE runtime."
    val nativeVariants = listOf("Enable intellijPlatform.nativeVariants and declare since-build 261 or later.")

    fun add(
        names: Set<String>,
        purpose: String,
        prerequisites: List<String>,
        cost: List<CapabilityCost>,
        optionalCost: List<CapabilityCost> = emptyList(),
    ) {
        names.forEach { name ->
            result[name] = Capability("${path.trimEnd(':')}:$name", purpose, prerequisites, cost, optionalCost)
        }
    }

    fun typed(
        type: Class<out Task>,
        purpose: String,
        prerequisites: List<String>,
        cost: List<CapabilityCost>,
        optionalCost: List<CapabilityCost> = emptyList(),
    ) = add(tasks.withType(type).names, purpose, prerequisites, cost, optionalCost)

    typed(AgentReportTask::class.java, "Inspect configured project metadata without executing the described workflows.", emptyList(), listOf(CapabilityCost.metadata))
    val reports = mapOf(
        Tasks.INTELLIJ_PLATFORM_INFO to "Inspect project identity, compiler settings, dependencies, and available capabilities.",
        Tasks.INTELLIJ_PLATFORM_PROJECT_GRAPH to "Inspect projects, source roots, and project dependency relationships.",
        Tasks.INTELLIJ_PLATFORM_DISTRIBUTION to "Inspect declared plugin packaging and distribution contents.",
        Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH to "Inspect declared dependencies; dependency resolution is opt-in.",
        Tasks.INTELLIJ_PLATFORM_TEST_MATRIX to "Inspect configured test workflows without running tests or starting an IDE.",
        Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX to "Inspect declared compatibility and configured verification targets, not verified support.",
    )
    reports.forEach { (name, purpose) ->
        if (name in available) {
            val optionalCost = if (name in setOf(Tasks.INTELLIJ_PLATFORM_INFO, Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH)) resolution else emptyList()
            add(setOf(name), purpose, emptyList(), listOf(CapabilityCost.metadata), optionalCost)
        }
    }
    typed(IntelliJPlatformDoctorTask::class.java, "Diagnose project configuration against the selected IDE without compiling.", platform, resolution)
    typed(IntelliJPlatformSdkTask::class.java, "Inspect the selected SDK, archives, classpath ownership, and local sources without compiling.", platform, resolution)
    typed(IntelliJPlatformDiscoverTask::class.java, "Discover SDK plugins/modules or product releases; release discovery does not acquire an IDE.", listOf("SDK discovery needs a declared platform; release discovery needs cached product listings when offline."), resolution)

    typed(AbstractCompile::class.java, "Compile configured sources.", compiler, compilation)
    add(available.filter { it.startsWith("compile") && it.endsWith("Kotlin") }.toSet(), "Compile configured Kotlin sources.", compiler, compilation)
    typed(Test::class.java, "Run configured JVM tests.", compiler + "Provide test classes and a test framework.", testing)
    typed(TestIdeTask::class.java, "Run tests against the configured IntelliJ Platform.", platform + compiler + "Configure an IntelliJ Platform test framework.", testing)
    typed(TestIdeUiTask::class.java, "Run Starter UI tests with the built plugin in an IDE.", sandbox + "Configure Starter UI test sources and dependencies.", ideTesting)
    typed(TestIdePerformanceTask::class.java, "Run IDE performance tests.", sandbox + "Provide performance test data and profiler configuration.", ideTesting)
    typed(RunIdeTask::class.java, "Launch the configured IDE with the developed plugin.", sandbox, ide)
    typed(BuildSearchableOptionsTask::class.java, "Build searchable options by launching a headless IDE.", sandbox, ide)

    typed(BuildPluginTask::class.java, "Build a plugin distribution archive; searchable options may launch an IDE.", platform + compiler, ide)
    typed(BuildPluginVariantsTask::class.java, "Build enabled native plugin distribution variants.", platform + compiler + nativeVariants, ide)
    typed(PreparePluginVariantTask::class.java, "Prepare an OS- and architecture-specific plugin archive.", compiler + nativeVariants, compilation)
    tasks.withType(BuildPluginTask::class.java).names.filter { it.startsWith("${Tasks.BUILD_PLUGIN_VARIANTS}_") }.forEach { name ->
        add(setOf(name), "Build this OS- and architecture-specific plugin distribution.", platform + compiler + nativeVariants, ide)
    }

    typed(VerifyPluginProjectConfigurationTask::class.java, "Verify project settings against the selected IDE and runtime without compiling.", platform, resolution)
    typed(VerifyPluginStructureTask::class.java, "Verify the built plugin structure.", platform + compiler, ideTesting)
    typed(VerifyPluginTask::class.java, "Run Plugin Verifier against configured IDE targets.", platform + compiler + "Configure Plugin Verifier targets and a compatible verifier runtime.", ideTesting)
    typed(VerifyPluginSignatureTask::class.java, "Verify a plugin archive signature.", listOf("Provide a signed plugin archive and signature verification tooling."), resolution + CapabilityCost.test)
    typed(SignPluginTask::class.java, "Sign the plugin distribution archive.", platform + compiler + "Configure signing credentials and certificate material.", ide)
    typed(PublishPluginTask::class.java, "Publish the built plugin distribution.", platform + compiler + "Configure a publishing destination and credentials.", ide + CapabilityCost.publish)

    return result.values.sortedBy { it.task }
}
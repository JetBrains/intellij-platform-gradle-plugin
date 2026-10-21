// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.Serializable
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.DefaultDomainObjectCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension
import org.jetbrains.intellij.platform.gradle.tasks.agent.IntelliJPlatformInfoTask.Companion.configuredValue
import org.jetbrains.intellij.platform.gradle.utils.dependenciesHelper
import org.jetbrains.intellij.platform.gradle.utils.isModule
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import java.io.File
import java.nio.file.Path

@Serializable
internal data class MatrixSnapshot<T>(
    val data: T,
    val issues: List<Issue> = emptyList(),
    val unavailable: List<Unavailable> = emptyList(),
)

@Serializable
internal data class MatrixValue(val state: String, val value: String? = null, val reason: String? = null)

@Serializable
internal data class MatrixIde(
    val state: String,
    val productCode: String? = null,
    val version: String? = null,
    val localPath: String? = null,
    val provenance: String,
)

@Serializable
internal data class MatrixDependency(val configuration: String, val group: String?, val name: String, val version: String?)

@Serializable
internal data class MatrixSourceSet(val name: String, val sourceDirectories: List<String>, val resourceDirectories: List<String>)

@Serializable
internal data class MatrixTestingDeclaration(
    val taskPath: String,
    val kind: String,
    val ide: MatrixIde,
    val sandbox: MatrixValue,
    val platformConfiguration: String,
)

/** Configuration-only metadata readers; none enumerate file collections or dependency providers. */
internal class MatrixMetadata(private val project: Project) {

    val module = project.pluginManager.isModule

    val sourceSets: List<MatrixSourceSet>
        get() = project.extensions.findByType<SourceSetContainer>()?.map {
            MatrixSourceSet(
                it.name,
                it.allSource.srcDirs.map { file -> file.toPath().safePathString }.sorted(),
                it.resources.srcDirs.map { file -> file.toPath().safePathString }.sorted(),
            )
        }?.sortedBy { it.name }.orEmpty()

    val baseIde: MatrixIde by lazy {
        declaredIdes(Configurations.INTELLIJ_PLATFORM_DEPENDENCY, "requested").singleOrNull()
            ?: MatrixIde("unavailable", provenance = "baseConfiguration")
    }

    fun declaredIdes(configurationName: String, provenance: String): List<MatrixIde> =
        project.dependenciesHelper.declaredIntelliJPlatforms(configurationName).map {
            val type = configuredValue(it.type) as? IntelliJPlatformType
            val version = configuredValue(it.version) as? String
            MatrixIde(if (type != null && version != null) "configured" else "notResolved", type?.code, version, provenance = provenance)
        }

    val testing: List<MatrixTestingDeclaration> by lazy {
        val extension = project.extensions.findByType<IntelliJPlatformTestingExtension>()
        buildList {
            fun appendDeclaration(parameters: IntelliJPlatformTestingExtension.CommonParameters<*>, kind: String) {
                val localPath = parameters.localPath.orNull?.asFile?.toPath()?.safePathString
                val type = parameters.type.orNull
                val version = parameters.version.orNull
                val ide = when {
                    localPath != null -> MatrixIde("configured", localPath = localPath, provenance = "testing.localPath")
                    type != null || version != null -> MatrixIde(
                        state = if ((type != null || baseIde.productCode != null) && (version != null || baseIde.version != null)) "configured" else "unavailable",
                        productCode = type?.code ?: baseIde.productCode,
                        version = version ?: baseIde.version,
                        provenance = "testing",
                    )
                    else -> baseIde.copy(provenance = "baseConfiguration")
                }
                add(
                    MatrixTestingDeclaration(
                        project.matrixTaskPath(parameters.name),
                        kind,
                        ide,
                        parameters.sandboxDirectory.orNull?.let { MatrixValue("configured", it.asFile.toPath().safePathString) }
                            ?: MatrixValue("notResolved", reason = "The sandbox convention depends on the resolved IDE."),
                        parameters.intellijPlatformConfigurationName.get(),
                    ),
                )
            }
            extension?.testIde?.forEach { appendDeclaration(it, "ide") }
            extension?.testIdeUi?.forEach { appendDeclaration(it, "ui") }
            extension?.testIdePerformance?.forEach { appendDeclaration(it, "performance") }
        }.sortedBy { it.taskPath }
    }

    fun runtime(): Map<String, MatrixValue> {
        val java = project.extensions.findByType<JavaPluginExtension>()
        return mapOf(
            "toolchainLanguageVersion" to (configuredValue(java?.toolchain?.languageVersion)?.toString()?.let { MatrixValue("configured", it) }
                ?: MatrixValue("notResolved", reason = "No constant toolchain language version is observable; lazy and IDE-derived conventions are not evaluated.")),
            "executable" to MatrixValue("notResolved", reason = "Runtime and Java launcher providers are deliberately not evaluated."),
        )
    }

    fun dependencies(names: Collection<String>): List<MatrixDependency> {
        val result = mutableListOf<MatrixDependency>()
        names.flatMap { project.configurations.findByName(it)?.hierarchy.orEmpty() }
            .distinctBy { it.name }
            .sortedBy { it.name }
            .forEach { configuration ->
                // Even configureEach can query pending collection sizes. Only visit already-materialized declarations.
                val dependencies = configuration.dependencies.withType(Dependency::class.java)
                val iterator = DefaultDomainObjectCollection::class.java.getDeclaredMethod("iteratorNoFlush")
                    .apply { isAccessible = true }
                    .invoke(dependencies) as Iterator<*>
                iterator.forEach {
                    val dependency = it as Dependency
                    result += MatrixDependency(configuration.name, dependency.group, dependency.name, dependency.version)
                }
            }
        return result.distinct().sortedWith(compareBy({ it.configuration }, { it.group }, { it.name }, { it.version }))
    }

    fun pluginConfigurations(declaration: MatrixTestingDeclaration?): List<String> = buildList {
        add(Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY)
        add(Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL)
        add(Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS)
        add(Configurations.INTELLIJ_PLATFORM_BUNDLED_MODULES)
        add(Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_DEPENDENCY)
        add(Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_LOCAL)
        add(Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_PLUGINS)
        add(Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_MODULES)
        declaration?.let { custom ->
            val suffix = custom.platformConfiguration.removePrefix(Configurations.INTELLIJ_PLATFORM_DEPENDENCY)
            add(Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_DEPENDENCY + suffix)
            add(Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_LOCAL + suffix)
            add(Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_PLUGINS + suffix)
            add(Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_MODULES + suffix)
        }
    }
}

internal fun Project.matrixTaskPath(name: String) = "${path.trimEnd(':')}:$name"

internal fun matrixCommand(taskPath: String) = "./gradlew ${matrixShellArgument(taskPath)}"

internal fun matrixShellArgument(value: String) = when {
    value.matches(Regex("[A-Za-z0-9_:./-]+")) -> value
    else -> "'${value.replace("'", "'\\''")}'"
}

/** Only literal paths are observed; a Provider, task, or file collection is never coerced to a string or resolved. */
internal fun Project.matrixLiteralPath(value: Any?): String? = when (value) {
    is String -> if (value.startsWith('~')) value else file(value).toPath().safePathString
    is File -> value.toPath().safePathString
    is Path -> value.safePathString
    is Directory -> value.asFile.toPath().safePathString
    is RegularFile -> value.asFile.toPath().safePathString
    else -> null
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.withType
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

private const val IDEA_PLUGIN_ROOT = "idea-plugin"
private const val EXTENSIONS = "extensions"
private const val DEFAULT_EXTENSION_NAMESPACE = "defaultExtensionNs"
private const val INTELLIJ_NAMESPACE = "com.intellij"

private val configurableExtensionPointNames = setOf(
    "applicationConfigurable",
    "projectConfigurable",
)

private val qualifiedConfigurableExtensionPointNames = configurableExtensionPointNames
    .mapTo(LinkedHashSet()) { "$INTELLIJ_NAMESPACE.$it" }

internal fun Project.searchableOptionsDescriptorFiles() = files(
    provider {
        buildList {
            addAll(mainProjectSearchableOptionsDescriptorFiles())

            addAll(
                relevantSearchableOptionsModuleProjects()
                    .flatMap { moduleProject: Project ->
                        val metaInfDirectory = moduleProject.projectDir.resolve("src/main/resources/META-INF")
                        metaInfDirectory
                            .listFiles { file -> file.isFile && file.extension == "xml" }
                            .orEmpty()
                            .toList()
                    }
            )
        }
    },
)

internal fun Project.buildSearchableOptionsEnabledProvider(): Provider<Boolean> {
    val buildSearchableOptionsEnabledProvider = extensionProvider.flatMap { it.buildSearchableOptions }
    val forceBuildSearchableOptionsProvider = providers[GradleProperties.ForceBuildSearchableOptions]
    val searchableOptionsDescriptorFiles = searchableOptionsDescriptorFiles()

    return provider {
        forceBuildSearchableOptionsProvider.get() || (
                buildSearchableOptionsEnabledProvider.get() &&
                        searchableOptionsDescriptorFiles.files.any { it.toPath().hasConfigurableExtensionPoint() }
                )
    }
}

internal fun Path.hasConfigurableExtensionPoint(): Boolean {
    if (!exists()) {
        return false
    }

    return runCatching {
        inputStream().use { inputStream ->
            val rootElement = JDOMUtil.loadDocument(inputStream).rootElement

            rootElement.name == IDEA_PLUGIN_ROOT && rootElement
                .getChildren(EXTENSIONS)
                .any { extensions ->
                    val defaultExtensionNamespace = extensions.getAttributeValue(DEFAULT_EXTENSION_NAMESPACE)

                    extensions.children.any { child ->
                        child.name in qualifiedConfigurableExtensionPointNames ||
                                (defaultExtensionNamespace == INTELLIJ_NAMESPACE && child.name in configurableExtensionPointNames)
                    }
                }
        }
    }.getOrDefault(false)
}

private fun Project.relevantSearchableOptionsModuleProjects(): List<Project> = listOf(
    Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE,
    Configurations.INTELLIJ_PLATFORM_PLUGIN_COMPOSED_MODULE,
).flatMap { configurationName ->
    configurations[configurationName]
        .allDependencies
        .withType<ProjectDependency>()
        .mapNotNull { dependency: ProjectDependency -> rootProject.findProject(dependency.path) }
}.distinctBy { moduleProject: Project -> moduleProject.path }

private fun Project.mainProjectSearchableOptionsDescriptorFiles(): List<File> {
    val sourceSets = extensions.findByType<SourceSetContainer>() ?: return emptyList()

    return sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME)
        ?.resources
        ?.srcDirs
        ?.map { it.resolve("META-INF/plugin.xml") }
        ?.filter(File::exists)
        .orEmpty()
}

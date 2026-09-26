// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

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
import org.jetbrains.intellij.platform.gradle.utils.xmlInputFactory
import java.io.File
import java.nio.file.Path
import javax.xml.stream.XMLStreamConstants
import kotlin.io.path.exists
import kotlin.io.path.inputStream

private const val IDEA_PLUGIN_ROOT = "idea-plugin"
private const val EXTENSION_POINTS = "extensionPoints"
private const val EXTENSION_POINT = "extensionPoint"
private const val EXTENSIONS = "extensions"
private const val DEFAULT_EXTENSION_NAMESPACE = "defaultExtensionNs"
private const val INTELLIJ_NAMESPACE = "com.intellij"
private const val CONFIGURABLE_MARKER = "configurable"

private val configurableExtensionPointNames = setOf(
    "applicationConfigurable",
    "projectConfigurable",
)

private val qualifiedConfigurableExtensionPointNames = configurableExtensionPointNames
    .mapTo(LinkedHashSet()) { "$INTELLIJ_NAMESPACE.$it" }

internal fun Project.searchableOptionsDescriptorFiles() = files(
    provider {
        buildList {
            addAll(searchableOptionsDescriptorFilesFromMainResources())

            addAll(
                relevantSearchableOptionsModuleProjects()
                    .flatMap(Project::searchableOptionsDescriptorFilesFromMainResources)
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
                        searchableOptionsDescriptorFiles.files.any { it.toPath().hasSearchableOptionsContent() }
                )
    }
}

internal fun Path.hasSearchableOptionsContent(): Boolean {
    if (!exists()) {
        return false
    }

    return runCatching {
        inputStream().use { inputStream ->
            val reader = xmlInputFactory.get().createXMLStreamReader(inputStream)
            try {
                val elements = ArrayDeque<PluginXmlElement>()

                while (reader.hasNext()) {
                    when (reader.next()) {
                        XMLStreamConstants.START_ELEMENT -> {
                            val localName = reader.localName
                            val element = when (val parent = elements.lastOrNull()) {
                                null -> {
                                    if (localName != IDEA_PLUGIN_ROOT) {
                                        return@use false
                                    }
                                    PluginXmlElement.Root
                                }

                                PluginXmlElement.Root -> when (localName) {
                                    EXTENSION_POINTS -> PluginXmlElement.ExtensionPoints
                                    EXTENSIONS -> PluginXmlElement.Extensions(
                                        reader.getAttributeValue(null, DEFAULT_EXTENSION_NAMESPACE)
                                    )

                                    else -> PluginXmlElement.Other
                                }

                                PluginXmlElement.ExtensionPoints -> {
                                    if (localName == EXTENSION_POINT) {
                                        for (i in 0 until reader.attributeCount) {
                                            if (reader.getAttributeValue(i).contains(CONFIGURABLE_MARKER, ignoreCase = true)) {
                                                return@use true
                                            }
                                        }
                                    }
                                    PluginXmlElement.Other
                                }

                                is PluginXmlElement.Extensions -> {
                                    val qualifiedName = localName.toQualifiedExtensionPointName(parent.defaultNamespace)
                                    if (qualifiedName in qualifiedConfigurableExtensionPointNames ||
                                        qualifiedName.contains(CONFIGURABLE_MARKER, ignoreCase = true)
                                    ) {
                                        return@use true
                                    }
                                    PluginXmlElement.Other
                                }

                                PluginXmlElement.Other -> PluginXmlElement.Other
                            }
                            elements.addLast(element)
                        }

                        XMLStreamConstants.END_ELEMENT -> elements.removeLast()
                    }
                }
                false
            } finally {
                reader.close()
            }
        }
    }.getOrDefault(false)
}

private sealed interface PluginXmlElement {
    data object Root : PluginXmlElement
    data object ExtensionPoints : PluginXmlElement
    data class Extensions(val defaultNamespace: String?) : PluginXmlElement
    data object Other : PluginXmlElement
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

private fun Project.searchableOptionsDescriptorFilesFromMainResources(): List<File> {
    val sourceSets = extensions.findByType<SourceSetContainer>() ?: return emptyList()

    return sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME)
        ?.resources
        ?.srcDirs
        ?.flatMap { resourceDirectory ->
            buildList {
                addAll(resourceDirectory.xmlFiles())
                addAll(resourceDirectory.resolve("META-INF").xmlFiles())
            }
        }
        ?.distinct()
        .orEmpty()
}

private fun File.xmlFiles() = listFiles { file -> file.isFile && file.extension == "xml" }
    .orEmpty()
    .toList()

private fun String.toQualifiedExtensionPointName(defaultExtensionNamespace: String?) = when {
    contains('.') -> this
    defaultExtensionNamespace != null -> "$defaultExtensionNamespace.$this"
    else -> this
}

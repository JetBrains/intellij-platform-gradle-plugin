// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import nl.adaptivity.xmlutil.util.CompactFragment

/**
 * The plugin descriptor fields observed by distribution reports. Unmodeled extension declarations are ignored.
 */
@Serializable
@XmlSerialName("idea-plugin")
internal data class PluginDescriptor(
    @XmlSerialName("package") val packageName: String? = null,
    @XmlElement val id: String? = null,
    @XmlElement val name: String? = null,
    @XmlElement val version: String? = null,
    @XmlElement @XmlSerialName("description") val description: Text? = null,
    @XmlElement @XmlSerialName("change-notes") val changeNotes: Text? = null,
    @XmlElement @XmlSerialName("idea-version") val ideaVersion: IdeaVersion? = null,
    @XmlElement val vendor: Vendor? = null,
    @XmlElement @XmlSerialName("product-descriptor") val productDescriptor: ProductDescriptor? = null,
    @XmlElement @XmlSerialName("depends") val depends: List<LegacyDependency> = emptyList(),
    @XmlElement val dependencies: Dependencies? = null,
    @XmlElement val content: Content? = null,
    @XmlElement @XmlSerialName("module") val modules: List<ModuleAlias> = emptyList(),
) {

    @Serializable
    data class Text(
        @XmlValue val content: CompactFragment = CompactFragment(""),
    ) {
        val value: String
            get() = content.getXmlReader().use { reader ->
                buildString {
                    while (reader.hasNext()) {
                        if (reader.next().isTextElement) append(reader.text)
                    }
                }
            }
    }

    @Serializable
    data class IdeaVersion(
        @XmlSerialName("since-build") val sinceBuild: String? = null,
        @XmlSerialName("until-build") val untilBuild: String? = null,
    )

    @Serializable
    @XmlSerialName("vendor")
    data class Vendor(
        val email: String? = null,
        val url: String? = null,
        @XmlValue val value: String = "",
    )

    @Serializable
    data class ProductDescriptor(
        val code: String? = null,
        @XmlSerialName("release-date") val releaseDate: String? = null,
        @XmlSerialName("release-version") val releaseVersion: String? = null,
        val optional: String? = null,
        val eap: String? = null,
    )

    @Serializable
    data class LegacyDependency(
        val optional: String? = null,
        @XmlSerialName("config-file") val configFile: String? = null,
        @XmlValue val id: String = "",
    )

    @Serializable
    @XmlSerialName("dependencies")
    data class Dependencies(
        @XmlElement @XmlSerialName("plugin") val plugins: List<Dependency> = emptyList(),
        @XmlElement @XmlSerialName("module") val modules: List<Dependency> = emptyList(),
    )

    @Serializable
    data class Dependency(
        val id: String? = null,
        val name: String? = null,
        val optional: String? = null,
        @XmlSerialName("config-file") val configFile: String? = null,
    )

    @Serializable
    @XmlSerialName("content")
    data class Content(
        @XmlElement @XmlSerialName("module") val modules: List<ContentModule> = emptyList(),
    )

    @Serializable
    data class ContentModule(
        val name: String? = null,
        val loading: String? = null,
        @XmlElement @XmlSerialName("depends") val depends: List<LegacyDependency> = emptyList(),
        @XmlElement val dependencies: Dependencies? = null,
        @XmlValue val content: List<CompactFragment> = emptyList(),
    ) {
        val inline get() = depends.isNotEmpty() || dependencies != null || content.isNotEmpty()
    }

    @Serializable
    data class ModuleAlias(
        val value: String? = null,
    )
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.jdom2.Document
import org.jdom2.Element
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.asPath
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.logCategory
import org.jetbrains.intellij.platform.gradle.tasks.base.PlatformVersionAware
import org.jetbrains.intellij.platform.gradle.transformXml
import org.jetbrains.intellij.platform.gradle.warn
import kotlin.io.path.inputStream

/**
 * Patches `plugin.xml` files with values provided to the task.
 *
 * To maintain and generate an up-to-date changelog, try using [Gradle Changelog Plugin](https://github.com/JetBrains/gradle-changelog-plugin).
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html">Plugin Configuration File</a>
 */
@Deprecated(message = "CHECK")
@CacheableTask
abstract class PatchPluginXmlTask : DefaultTask(), PlatformVersionAware {

    @get:SkipWhenEmpty
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /**
     * @see [IntelliJPlatformExtension.PluginConfiguration.id]
     */
    @get:Input
    @get:Optional
    abstract val pluginId: Property<String>

    /**
     * @see [IntelliJPlatformExtension.PluginConfiguration.name]
     */
    @get:Input
    @get:Optional
    abstract val pluginName: Property<String>

    /**
     * @see [IntelliJPlatformExtension.PluginConfiguration.version]
     */
    @get:Input
    @get:Optional
    abstract val pluginVersion: Property<String>

    /**
     * @see [IntelliJPlatformExtension.PluginConfiguration.description]
     */
    @get:Input
    @get:Optional
    abstract val pluginDescription: Property<String>

    /**
     * @see [IntelliJPlatformExtension.PluginConfiguration.changeNotes]
     */
    @get:Input
    @get:Optional
    abstract val changeNotes: Property<String>

    /**
     * @see [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.code]
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorCode: Property<String>

    /**
     * @see [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.releaseDate]
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorReleaseDate: Property<String>

    /**
     * @see [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.releaseVersion]
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorReleaseVersion: Property<String>

    /**
     * @see [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.optional]
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorOptional: Property<Boolean>

    /**
     * @see [IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.sinceBuild]
     */
    @get:Input
    @get:Optional
    abstract val sinceBuild: Property<String>

    /**
     * @see [IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.untilBuild]
     */
    @get:Input
    @get:Optional
    abstract val untilBuild: Property<String>

    /**
     * @see [IntelliJPlatformExtension.PluginConfiguration.Vendor.name]
     */
    @get:Input
    @get:Optional
    abstract val vendorName: Property<String>

    /**
     * @see [IntelliJPlatformExtension.PluginConfiguration.Vendor.email]
     */
    @get:Input
    @get:Optional
    abstract val vendorEmail: Property<String>

    /**
     * @see [IntelliJPlatformExtension.PluginConfiguration.Vendor.url]
     */
    @get:Input
    @get:Optional
    abstract val vendorUrl: Property<String>

    private val context = logCategory()

    init {
        group = PLUGIN_GROUP_NAME
        description = "Patches `plugin.xml` files with values provided to the task."
    }

    @TaskAction
    fun patchPluginXml() {

        val inputPath = inputFile.asPath
        val outputPath = outputFile.asPath

        val sinceBuildValue = sinceBuild.orNull ?: with(platformVersion) {
            "$major.$minor"
        }
        val untilBuildValue = untilBuild.orNull ?: with(platformVersion) {
            "$major.*"
        }

        inputPath.inputStream().use { inputStream ->
            val document = JDOMUtil.loadDocument(inputStream)

            with(document) {
                patch(pluginId, "id")
                patch(pluginName, "name")
                patch(pluginVersion.takeIf { it.get() != Project.DEFAULT_VERSION }, "version")
                patch(pluginDescription, "description")
                patch(changeNotes, "change-notes")

                patch(productDescriptorCode, "product-descriptor", "code")
                patch(productDescriptorReleaseDate, "product-descriptor", "release-date")
                patch(productDescriptorReleaseVersion, "product-descriptor", "release-version")
                patch(productDescriptorOptional.map { it.toString() }, "product-descriptor", "optional")

                patch(sinceBuildValue, "idea-version", "since-build")
                patch(untilBuildValue, "idea-version", "until-build")

                patch(vendorName, "vendor")
                patch(vendorEmail, "vendor", "email")
                patch(vendorUrl, "vendor", "url")
            }

            transformXml(document, outputPath)
        }
    }

    /**
     * Sets the [provider] value for the given [tagName] or [attributeName] of [tagName].
     */
    private fun Document.patch(provider: Provider<String>?, tagName: String, attributeName: String? = null) {
        val value = provider?.orNull ?: return
        patch(value, tagName, attributeName)
    }

    /**
     * Sets the [value] for the given [tagName] or [attributeName] of [tagName].
     */
    private fun Document.patch(value: String, tagName: String, attributeName: String? = null) {
        val pluginXml = rootElement.takeIf { it.name == "idea-plugin" } ?: return
        val element = pluginXml.getChild(tagName) ?: Element(tagName).apply {
            pluginXml.addContent(0, this)
        }

        when {
            attributeName == null -> {
                val existingValue = element.text
                if (existingValue.isNotEmpty()) {
                    warn(context, "Patching plugin.xml: value of '$tagName[$existingValue]' tag will be set to '$value'")
                }
                element.text = value
            }

            else -> {
                val existingValue = element.getAttribute(attributeName)?.value
                if (!existingValue.isNullOrEmpty()) {
                    warn(context, "Patching plugin.xml: attribute '$attributeName=[$existingValue]' of '$tagName' tag will be set to '$value'")
                }
                element.setAttribute(attributeName, value)
            }
        }
    }
}

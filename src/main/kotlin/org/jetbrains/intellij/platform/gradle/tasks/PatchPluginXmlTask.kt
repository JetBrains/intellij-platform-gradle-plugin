// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.the
import org.jdom2.CDATA
import org.jdom2.Document
import org.jdom2.Element
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.model.transformXml
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import kotlin.io.path.inputStream
import kotlin.io.path.name

/**
 * Patches `plugin.xml` files with values provided to the task.
 *
 * To maintain and generate an up-to-date changelog, try using [Gradle Changelog Plugin](https://github.com/JetBrains/gradle-changelog-plugin).
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html">Plugin Configuration File</a>
 */
@CacheableTask
abstract class PatchPluginXmlTask : DefaultTask(), IntelliJPlatformVersionAware {

    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /**
     * @see IntelliJPlatformExtension.PluginConfiguration.id
     */
    @get:Input
    @get:Optional
    abstract val pluginId: Property<String>

    /**
     * @see IntelliJPlatformExtension.PluginConfiguration.name
     */
    @get:Input
    @get:Optional
    abstract val pluginName: Property<String>

    /**
     * @see IntelliJPlatformExtension.PluginConfiguration.version
     */
    @get:Input
    @get:Optional
    abstract val pluginVersion: Property<String>

    /**
     * @see IntelliJPlatformExtension.PluginConfiguration.description
     */
    @get:Input
    @get:Optional
    abstract val pluginDescription: Property<String>

    /**
     * @see IntelliJPlatformExtension.PluginConfiguration.changeNotes
     */
    @get:Input
    @get:Optional
    abstract val changeNotes: Property<String>

    /**
     * @see IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.code
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorCode: Property<String>

    /**
     * @see IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.releaseDate
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorReleaseDate: Property<String>

    /**
     * @see IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.releaseVersion
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorReleaseVersion: Property<String>

    /**
     * @see IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.optional
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorOptional: Property<Boolean>

    /**
     * @see IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.sinceBuild
     */
    @get:Input
    @get:Optional
    abstract val sinceBuild: Property<String>

    /**
     * @see IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.untilBuild
     */
    @get:Input
    @get:Optional
    abstract val untilBuild: Property<String>

    /**
     * @see IntelliJPlatformExtension.PluginConfiguration.Vendor.name
     */
    @get:Input
    @get:Optional
    abstract val vendorName: Property<String>

    /**
     * @see IntelliJPlatformExtension.PluginConfiguration.Vendor.email
     */
    @get:Input
    @get:Optional
    abstract val vendorEmail: Property<String>

    /**
     * @see IntelliJPlatformExtension.PluginConfiguration.Vendor.url
     */
    @get:Input
    @get:Optional
    abstract val vendorUrl: Property<String>

    private val log = Logger(javaClass)

    init {
        group = PLUGIN_GROUP_NAME
        description = "Patches `plugin.xml` files with values provided to the task."
    }

    @TaskAction
    fun patchPluginXml() {
        inputFile.asPath.inputStream().use { inputStream ->
            val document = JDOMUtil.loadDocument(inputStream)

            with(document) {
                patch(pluginId, "id")
                patch(pluginName, "name")
                patch(pluginVersion.takeIf { it.get() != Project.DEFAULT_VERSION }, "version")
                patch(pluginDescription, "description", isCDATA = true)
                patch(changeNotes, "change-notes", isCDATA = true)

                patch(productDescriptorCode, "product-descriptor", "code")
                patch(productDescriptorReleaseDate, "product-descriptor", "release-date")
                patch(productDescriptorReleaseVersion, "product-descriptor", "release-version")
                patch(productDescriptorOptional.map { it.toString() }, "product-descriptor", "optional")

                patch(sinceBuild, "idea-version", "since-build")
                patch(untilBuild, "idea-version", "until-build")

                patch(vendorName, "vendor")
                patch(vendorEmail, "vendor", "email")
                patch(vendorUrl, "vendor", "url")
            }

            transformXml(document, outputFile.asPath)
        }
    }

    /**
     * Sets the [provider] value for the given [tagName] or [attributeName] of [tagName].
     */
    private fun Document.patch(provider: Provider<String>?, tagName: String, attributeName: String? = null, isCDATA: Boolean = false) {
        val value = provider?.orNull ?: return
        patch(value, tagName, attributeName, isCDATA)
    }

    /**
     * Sets the [value] for the given [tagName] or [attributeName] of [tagName].
     */
    private fun Document.patch(value: String, tagName: String, attributeName: String? = null, isCDATA: Boolean = false) {
        val pluginXml = rootElement.takeIf { it.name == "idea-plugin" } ?: return
        val element = pluginXml.getChild(tagName) ?: Element(tagName).apply {
            pluginXml.addContent(0, this)
        }

        when {
            attributeName == null -> {
                val existingValue = element.text
                if (existingValue.isNotEmpty() && existingValue != value) {
                    log.warn("Patching plugin.xml: value of '$tagName[$existingValue]' tag will be set to '$value'")
                }
                when {
                    isCDATA -> element.addContent(CDATA(value))
                    else -> element.text = value
                }
            }

            else -> {
                val existingValue = element.getAttribute(attributeName)?.value
                if (!existingValue.isNullOrEmpty()) {
                    log.warn("Patching plugin.xml: attribute '$attributeName=[$existingValue]' of '$tagName' tag will be set to '$value'")
                }
                element.setAttribute(attributeName, value)
            }
        }
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<PatchPluginXmlTask>(Tasks.PATCH_PLUGIN_XML) {
                val extension = project.the<IntelliJPlatformExtension>()

                inputFile.convention(project.layout.file(project.provider {
                    val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer

                    sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                        .resources
                        .srcDirs
                        .map { it.resolve("META-INF/plugin.xml") }
                        .firstOrNull { it.exists() }
                }))
                outputFile.convention(project.layout.file(
                    inputFile.map { temporaryDir.resolve(it.asPath.name) }
                ))

                extension.pluginConfiguration.let { pluginConfiguration ->
                    pluginId.convention(pluginConfiguration.id)
                    pluginName.convention(pluginConfiguration.name)
                    pluginVersion.convention(pluginConfiguration.version)
                    pluginDescription.convention(pluginConfiguration.description)
                    changeNotes.convention(pluginConfiguration.changeNotes)

                    pluginConfiguration.productDescriptor.let { productDescriptor ->
                        productDescriptorCode.convention(productDescriptor.code)
                        productDescriptorReleaseDate.convention(productDescriptor.releaseDate)
                        productDescriptorReleaseVersion.convention(productDescriptor.releaseVersion)
                        productDescriptorOptional.convention(productDescriptor.optional)
                    }

                    pluginConfiguration.ideaVersion.let { ideaVersion ->
                        sinceBuild.convention(ideaVersion.sinceBuild)
                        untilBuild.convention(ideaVersion.untilBuild)
                    }

                    pluginConfiguration.vendor.let { vendor ->
                        vendorName.convention(vendor.name)
                        vendorEmail.convention(vendor.email)
                        vendorUrl.convention(vendor.url)
                    }
                }
            }
    }
}

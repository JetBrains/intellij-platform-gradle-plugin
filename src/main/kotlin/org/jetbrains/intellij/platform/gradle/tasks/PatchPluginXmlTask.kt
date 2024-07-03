// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.jdom2.CDATA
import org.jdom2.Document
import org.jdom2.Element
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.models.transformXml
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
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

    /**
     * Represents an input `plugin.xml` file.
     *
     * By default, a `plugin.xml` file is picked from the main resources location, like: `src/main/kotlin/resources/META-INF/plugin.xml`.
     */
    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFile: RegularFileProperty

    /**
     * Represents the output `plugin.xml` file property for a task.
     *
     * By default, the file is written to a temporary task directory, like: [ProjectLayout.getBuildDirectory]/tmp/patchPluginXml/plugin.xml
     */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /**
     * A unique identifier of the plugin.
     * It should be a fully qualified name similar to Java packages and must not collide with the ID of existing plugins.
     * The ID is a technical value used to identify the plugin in the IDE and [JetBrains Marketplace](https://plugins.jetbrains.com/).
     * Please use characters, numbers, and `.`/`-`/`_` symbols only and keep it reasonably short.
     *
     * The provided value will be set as a value of the `<id>` element.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.id]
     *
     * @see IntelliJPlatformExtension.PluginConfiguration.id
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__id">Plugin Configuration File: `id`</a>
     */
    @get:Input
    @get:Optional
    abstract val pluginId: Property<String>

    /**
     * The user-visible plugin display name (Title Case).
     *
     * The provided value will be set as a value of the `<name>` element.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.name]
     *
     * @see IntelliJPlatformExtension.PluginConfiguration.name
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__name">Plugin Configuration File: `name`</a>
     */
    @get:Input
    @get:Optional
    abstract val pluginName: Property<String>

    /**
     * The plugin version is displayed in the Plugins settings dialog and on the JetBrains Marketplace plugin page.
     * Plugins uploaded to the JetBrains Marketplace must follow semantic versioning.
     *
     * The provided value will be set as a value of the `<version>` element.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.version]
     *
     * @see IntelliJPlatformExtension.PluginConfiguration.version
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__version">Plugin Configuration File: `version`</a>
     */
    @get:Input
    @get:Optional
    abstract val pluginVersion: Property<String>

    /**
     * The plugin description is displayed on the JetBrains Marketplace plugin page and in the Plugins settings dialog.
     * Simple HTML elements, like text formatting, paragraphs, lists, etc., are allowed.
     *
     * The description content is automatically wrapped with `<![CDATA[... ]]>`.
     *
     * The provided value will be set as a value of the `<description>` element.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.description]
     *
     * @see IntelliJPlatformExtension.PluginConfiguration.description
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__description">Plugin Configuration File: `description`</a>
     */
    @get:Input
    @get:Optional
    abstract val pluginDescription: Property<String>

    /**
     * A short summary of new features, bugfixes, and changes provided with the latest plugin version.
     * Change notes are displayed on the JetBrains Marketplace plugin page and in the Plugins settings dialog.
     * Simple HTML elements, like text formatting, paragraphs, lists, etc., are allowed.
     *
     * The change notes content is automatically wrapped with `<![CDATA[... ]]>`.
     *
     * The provided value will be set as a value of the `<change-notes>` element.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.changeNotes]
     *
     * @see IntelliJPlatformExtension.PluginConfiguration.changeNotes
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__change-notes">Plugin Configuration File: `change-notes`</a>
     */
    @get:Input
    @get:Optional
    abstract val changeNotes: Property<String>

    /**
     * The plugin product code used in the JetBrains Sales System.
     * The code must be agreed with JetBrains in advance and follow [the requirements](https://plugins.jetbrains.com/docs/marketplace/obtain-a-product-code-from-jetbrains.html).
     *
     * The provided value will be set as a value of the `<product-descriptor code="">` element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.code]
     *
     * @see IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.code
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor">Plugin Configuration File: `product-descriptor`</a>
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorCode: Property<String>

    /**
     * Date of the major version release in the `YYYYMMDD` format.
     *
     * The provided value will be set as a value of the `<product-descriptor release-date="">` element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.releaseDate]
     *
     * @see IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.releaseDate
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor">Plugin Configuration File: `product-descriptor`</a>
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorReleaseDate: Property<String>

    /**
     * A major version in a special number format.
     *
     * The provided value will be set as a value of the `<product-descriptor release-version="">` element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.releaseVersion]
     *
     * @see IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.releaseVersion
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor">Plugin Configuration File: `product-descriptor`</a>
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorReleaseVersion: Property<String>

    /**
     * The boolean value determining whether the plugin is a [Freemium](https://plugins.jetbrains.com/docs/marketplace/freemium.html) plugin.
     * Default value: `false`.
     *
     * The provided value will be set as a value of the `<product-descriptor optional="">` element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.optional]
     *
     * @see IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.optional
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor">Plugin Configuration File: `product-descriptor`</a>
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorOptional: Property<Boolean>

    /**
     * The lowest IDE version compatible with the plugin.
     *
     * The provided value will be set as a value of the `<idea-version since-build="..."/>` element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.sinceBuild]
     *
     * @see IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.sinceBuild
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__idea-version">Plugin Configuration File: `idea-version`</a>
     */
    @get:Input
    @get:Optional
    abstract val sinceBuild: Property<String>

    /**
     * The highest IDE version compatible with the plugin.
     * Undefined value declares compatibility with all the IDEs since the version specified by the `since-build` (also with the future builds that may cause incompatibility errors).
     *
     * The provided value will be set as a value of the `<idea-version until-build="..."/>` element attribute.
     *
     * The `until-build` attribute can be unset by setting `provider { null }` as a value.
     * Note that passing only `null` will make Gradle use a default value instead.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.untilBuild]
     *
     * @see IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.untilBuild
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__idea-version">Plugin Configuration File: `idea-version`</a>
     */
    @get:Input
    @get:Optional
    abstract val untilBuild: Property<String>

    /**
     * The vendor name or organization ID (if created) in the Plugins settings dialog and on the JetBrains Marketplace plugin page.
     *
     * The provided value will be set as a value of the `<vendor>` element.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.Vendor.name]
     *
     * @see IntelliJPlatformExtension.PluginConfiguration.Vendor.name
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__vendor">Plugin Configuration File: `vendor`</a>
     */
    @get:Input
    @get:Optional
    abstract val vendorName: Property<String>

    /**
     * The vendor's email address.
     *
     * The provided value will be set as a value of the `<vendor email="">` element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.Vendor.email]
     *
     * @see IntelliJPlatformExtension.PluginConfiguration.Vendor.email
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__vendor">Plugin Configuration File</a>
     */
    @get:Input
    @get:Optional
    abstract val vendorEmail: Property<String>

    /**
     * The link to the vendor's homepage.
     *
     * The provided value will be set as a value of the `<vendor url="">` element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.Vendor.url]
     *
     * @see IntelliJPlatformExtension.PluginConfiguration.Vendor.url
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__vendor">Plugin Configuration File</a>
     */
    @get:Input
    @get:Optional
    abstract val vendorUrl: Property<String>

    private val log = Logger(javaClass)

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
                patch(untilBuild, "idea-version", "until-build", acceptNull = true)

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
    private fun Document.patch(
        provider: Provider<String?>?,
        tagName: String,
        attributeName: String? = null,
        isCDATA: Boolean = false,
        acceptNull: Boolean = false,
    ) {
        val value = provider?.orNull

        when {
            value != null -> patch(value, tagName, attributeName, isCDATA)
            acceptNull -> remove(tagName, attributeName)
        }
    }

    /**
     * Sets the [value] for the given [tagName] or [attributeName] of [tagName].
     * If [value] is `null` but the relevant attribute or element exists, unset it.
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
                if (!existingValue.isNullOrEmpty() && existingValue != value) {
                    log.warn("Patching plugin.xml: attribute '$attributeName=[$existingValue]' of '$tagName' tag will be set to '$value'")
                }
                element.setAttribute(attributeName, value)
            }
        }
    }

    private fun Document.remove(tagName: String, attributeName: String? = null) {
        val pluginXml = rootElement.takeIf { it.name == "idea-plugin" } ?: return

        when {
            attributeName == null -> rootElement.removeChild(tagName)
            else -> {
                val element = pluginXml.getChild(tagName) ?: return
                element.removeAttribute(attributeName)
            }
        }
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Patches `plugin.xml` file with values provided to the task."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<PatchPluginXmlTask>(Tasks.PATCH_PLUGIN_XML) {
                val pluginConfigurationProvider = project.extensionProvider.map { it.pluginConfiguration }
                val productDescriptorProvider = pluginConfigurationProvider.map { it.productDescriptor }
                val ideaVersionProvider = pluginConfigurationProvider.map { it.ideaVersion }
                val vendorProvider = pluginConfigurationProvider.map { it.vendor }

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

                pluginId.convention(pluginConfigurationProvider.flatMap { it.id })
                pluginName.convention(pluginConfigurationProvider.flatMap { it.name })
                pluginVersion.convention(pluginConfigurationProvider.flatMap { it.version })
                pluginDescription.convention(pluginConfigurationProvider.flatMap { it.description })
                changeNotes.convention(pluginConfigurationProvider.flatMap { it.changeNotes })

                productDescriptorCode.convention(productDescriptorProvider.flatMap { it.code })
                productDescriptorReleaseDate.convention(productDescriptorProvider.flatMap { it.releaseDate })
                productDescriptorReleaseVersion.convention(productDescriptorProvider.flatMap { it.releaseVersion })
                productDescriptorOptional.convention(productDescriptorProvider.flatMap { it.optional })

                sinceBuild.convention(ideaVersionProvider.flatMap { it.sinceBuild })
                untilBuild.convention(ideaVersionProvider.flatMap { it.untilBuild })

                vendorName.convention(vendorProvider.flatMap { it.name })
                vendorEmail.convention(vendorProvider.flatMap { it.email })
                vendorUrl.convention(vendorProvider.flatMap { it.url })
            }
    }
}

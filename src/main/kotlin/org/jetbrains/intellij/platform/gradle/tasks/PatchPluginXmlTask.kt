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
 * Patches `plugin.xml` files with values provided with the [IntelliJPlatformExtension.PluginConfiguration] extension.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html">Plugin Configuration File</a>
 */
@CacheableTask
abstract class PatchPluginXmlTask : DefaultTask(), IntelliJPlatformVersionAware {

    /**
     * Specifies the input `plugin.xml`</path>` file, which by default is picked from the main resource location.
     *
     * Default value: `src/main/resources/META-INF/plugin.xml`
     */
    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFile: RegularFileProperty

    /**
     * Specifies the patched output `plugin.xml` file, which by default is written to a temporary task-specific directory within the [ProjectLayout.getBuildDirectory] directory.
     *
     * Default value: [ProjectLayout.getBuildDirectory]/tmp/patchPluginXml/plugin.xml
     */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /**
     * Specifies a unique plugin identifier, which should be a fully qualified name similar to Java packages and must not collide with the ID of existing plugins.
     * The ID is a technical value used to identify the plugin in the IDE and [JetBrains Marketplace](https://plugins.jetbrains.com/).
     *
     * The provided value will be assigned to the [`<id>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__id) element.
     *
     * Please use characters, numbers, and `.`/`-`/`_` symbols only and keep it reasonably short.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.id]
     */
    @get:Input
    @get:Optional
    abstract val pluginId: Property<String>

    /**
     * Specifies the user-visible plugin name.
     * It should use Title Case.
     * The provided value will be assigned to the [`<name>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__name) element.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.name]
     */
    @get:Input
    @get:Optional
    abstract val pluginName: Property<String>

    /**
     * Specifies the plugin version displayed in the <control>Plugins</control> settings dialog and on the [JetBrains Marketplace](https://plugins.jetbrains.com) plugin page.
     * Plugins uploaded to [JetBrains Marketplace](https://plugins.jetbrains.com) must follow [semantic versioning](https://plugins.jetbrains.com/docs/marketplace/semver.htm).
     * The provided value will be assigned to the [`<version>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__version) element.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.version]
     */
    @get:Input
    @get:Optional
    abstract val pluginVersion: Property<String>

    /**
     * Specifies the plugin description displayed in the <control>Plugins</control> settings dialog and on the [JetBrains Marketplace](https://plugins.jetbrains.com) plugin page.
     * Simple HTML elements, like text formatting, paragraphs, lists, etc., are allowed.
     *
     * The description content is automatically wrapped in `<![CDATA[... ]]>`.
     * The provided value will be assigned to the [`<description>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__description) element.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.description]
     */
    @get:Input
    @get:Optional
    abstract val pluginDescription: Property<String>

    /**
     * A short summary of new features, bugfixes, and changes provided in this plugin version.
     * Change notes are displayed on the [JetBrains Marketplace](https://plugins.jetbrains.com) plugin page and in the <control>Plugins</control> settings dialog.
     * Simple HTML elements, like text formatting, paragraphs, lists, etc., are allowed.
     *
     * The change notes content is automatically wrapped in `<![CDATA[... ]]>`.
     * The provided value will be assigned to the [`<change-notes>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__change-notes) element.
     *
     * To maintain and generate an up-to-date changelog, try using [Gradle Changelog Plugin](https://github.com/JetBrains/gradle-changelog-plugin).
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.changeNotes]
     */
    @get:Input
    @get:Optional
    abstract val changeNotes: Property<String>

    /**
     * The plugin product code used in the JetBrains Sales System.
     * The code must be agreed with JetBrains in advance and follow [the requirements](https://plugins.jetbrains.com/docs/marketplace/obtain-a-product-code-from-jetbrains.html).
     * The provided value will be assigned to the [`<product-descriptor code="">`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor) element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.code]
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorCode: Property<String>

    /**
     * Date of the major version release in the `YYYYMMDD` format.
     * The provided value will be assigned to the [`<product-descriptor release-date="">`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor) element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.releaseDate]
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorReleaseDate: Property<String>

    /**
     * Specifies the major version of the plugin in a special number format used for paid plugins on [JetBrains Marketplace](https://plugins.jetbrains.com/docs/marketplace/add-required-parameters.html).
     * The provided value will be assigned to the [`<product-descriptor release-version="">`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor) element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.releaseVersion]
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorReleaseVersion: Property<String>

    /**
     * Specifies the boolean value determining whether the plugin is a [Freemium](https://plugins.jetbrains.com/docs/marketplace/freemium.html) plugin.
     * The provided value will be assigned to the [`<product-descriptor optional="">`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor) element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.optional]
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorOptional: Property<Boolean>

    /**
     * Specifies the boolean value determining whether the plugin is an EAP release.
     * The provided value will be assigned to the [`<product-descriptor eap="">`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor) element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor.eap]
     */
    @get:Input
    @get:Optional
    abstract val productDescriptorEap: Property<Boolean>

    /**
     * Specifies the lowest IDE version compatible with the plugin.
     * The provided value will be assigned to the [`<idea-version since-build="..."/>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__idea-version) element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.sinceBuild]
     */
    @get:Input
    @get:Optional
    abstract val sinceBuild: Property<String>

    /**
     * The highest IDE version compatible with the plugin.
     * The `until-build` attribute can be unset by setting `provider { null }` as a value, and note that only passing `null` will make Gradle use the default value instead.
     * However, if `until-build` is undefined, compatibility with all the IDEs since the version specified by the `since-build` is assumed, which can cause incompatibility errors in future builds.
     *
     * The provided value will be assigned to the [`<idea-version until-build="..."/>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__idea-version) element attribute.
     *
     * The `until-build` attribute can be unset by setting `provider { null }` as a value.
     * Passing `null` will make Gradle use the default value instead.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.untilBuild]
     */
    @get:Input
    @get:Optional
    abstract val untilBuild: Property<String>

    /**
     * Specifies the vendor name or organization ID (if created) in the <control>Plugins</control> settings dialog and on the [JetBrains Marketplace](https://plugins.jetbrains.com) plugin page.
     * The provided value will be assigned to the [`<vendor>`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__vendor) element.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.Vendor.name]
     */
    @get:Input
    @get:Optional
    abstract val vendorName: Property<String>

    /**
     * Specifies the vendor's email address.
     * The provided value will be assigned to the [`<vendor email="">`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__vendor) element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.Vendor.email]
     */
    @get:Input
    @get:Optional
    abstract val vendorEmail: Property<String>

    /**
     * Specifies the link to the vendor's homepage.
     * The provided value will be assigned to the [`<vendor url="">`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__vendor) element attribute.
     *
     * Default value: [IntelliJPlatformExtension.PluginConfiguration.Vendor.url]
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
                patch(productDescriptorEap.map { it.toString() }, "product-descriptor", "eap")

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
                    isCDATA -> element.setContent(CDATA(value))
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
        description = "Patches plugin.xml file with provided values."
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
                productDescriptorEap.convention(productDescriptorProvider.flatMap { it.eap })

                sinceBuild.convention(ideaVersionProvider.flatMap { it.sinceBuild })
                untilBuild.convention(ideaVersionProvider.flatMap { it.untilBuild })

                vendorName.convention(vendorProvider.flatMap { it.name })
                vendorEmail.convention(vendorProvider.flatMap { it.email })
                vendorUrl.convention(vendorProvider.flatMap { it.url })
            }
    }
}

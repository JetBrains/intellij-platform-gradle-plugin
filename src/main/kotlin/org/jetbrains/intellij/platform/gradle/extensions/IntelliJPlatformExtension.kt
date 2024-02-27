// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.resources.ResourceHandler
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByName
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Extensions
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Locations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Sandbox
import org.jetbrains.intellij.platform.gradle.model.ProductInfo
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.model.toPublication
import org.jetbrains.intellij.platform.gradle.model.validateSupportedVersion
import org.jetbrains.intellij.platform.gradle.provider.ProductReleasesValueSource
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.*
import org.jetbrains.intellij.platform.gradle.tasks.aware.PluginVerifierAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SigningAware
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.io.File
import javax.inject.Inject
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.math.absoluteValue

/**
 * The IntelliJ Platform Gradle Plugin extension.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@IntelliJPlatform
interface IntelliJPlatformExtension : ExtensionAware {

    /**
     * Builds an index of UI components (searchable options) for the plugin.
     * Controls the execution of the [BuildSearchableOptionsTask] task.
     *
     * Default value: `true`
     */
    val buildSearchableOptions: Property<Boolean>

    /**
     * Enables the compiled classes instrumentation.
     * The compiled code will be enhanced with:
     * - nullability assertions
     * - post-processing of forms created by IntelliJ GUI Designer
     *
     * Default value: `true`
     */
    val instrumentCode: Property<Boolean>

    /**
     * Provides read-only access to the IntelliJ Platform dependency artifact path.
     */
    val platformPath: DirectoryProperty

    /**
     * Provides read-only access to the [ProductInfo] object associated with the IntelliJ Platform dependency configured for the current project.
     */
    val productInfo: Property<ProductInfo>

    /**
     * Defines the project name, which is used for creating file structure and the build archive.
     *
     * Default value: [Project.getName]
     */
    val projectName: Property<String>

    /**
     * The path to the sandbox container where tests and IDE instances read and write data.
     *
     * Default value: [ProjectLayout.getBuildDirectory]/[Sandbox.CONTAINER]/
     */
    val sandboxContainer: DirectoryProperty

    val pluginConfiguration
        get() = extensions.getByName<PluginConfiguration>(Extensions.PLUGIN_CONFIGURATION)

    val publishing
        get() = extensions.getByName<Publishing>(Extensions.PUBLISHING)

    val signing
        get() = extensions.getByName<Signing>(Extensions.SIGNING)

    val verifyPlugin
        get() = extensions.getByName<VerifyPlugin>(Extensions.VERIFY_PLUGIN)

    /**
     * Configures the plugin definition and stores in the `plugin.xml` file.
     */
    @IntelliJPlatform
    interface PluginConfiguration : ExtensionAware {

        val productDescriptor
            get() = extensions.getByName<ProductDescriptor>(Extensions.PRODUCT_DESCRIPTOR)

        val ideaVersion
            get() = extensions.getByName<IdeaVersion>(Extensions.IDEA_VERSION)

        val vendor
            get() = extensions.getByName<Vendor>(Extensions.VENDOR)

        /**
         * The plugin's unique identifier.
         * This should mirror the structure of fully qualified Java packages and must remain distinct from the IDs of existing plugins.
         * This ID is a technical descriptor used not only within the IDE, but also on [JetBrains Marketplace](https://plugins.jetbrains.com/).
         *
         * Please restrict input to characters, numbers, and `.`/`-`/`_` symbols , and aim for a concise length.
         *
         * The entered value will populate the `<id>` element.
         *
         * @see PatchPluginXmlTask.pluginId
         * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__id">Plugin Configuration File: `id`</a>
         */
        val id: Property<String>

        /**
         * The plugin display name, visible to users (Title Case).
         *
         * The inputted value will be used to populate the `<name>` element.
         *
         * @see PatchPluginXmlTask.pluginName
         * @see PrepareSandboxTask.pluginName
         * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__name">Plugin Configuration File: `name`</a>
         */
        val name: Property<String>

        /**
         * The plugin version, presented in the Plugins settings dialog and on its JetBrains Marketplace page.
         *
         * For plugins uploaded to the JetBrains Marketplace, semantic versioning must be adhered to.
         *
         * The specified value will be used as an `<version>` element.
         *
         * @see PatchPluginXmlTask.pluginVersion
         * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__version">Plugin Configuration File: `version`</a>
         */
        val version: Property<String>

        /**
         * The plugin description, which is presented on the JetBrains Marketplace plugin page and in the Plugins settings dialog.
         * Basic HTML elements such as text formatting, paragraphs, and lists are permitted.
         *
         * The description content is automatically enclosed by `<![CDATA[... ]]>`.
         *
         * The supplied value will populate the `<description>` element.
         *
         * @see PatchPluginXmlTask.pluginDescription
         * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__description">Plugin Configuration File: `description`</a>
         */
        val description: Property<String>

        /**
         * A concise summary of new features, bug fixes, and alterations provided in the latest plugin version.
         * These change notes will be displayed on the JetBrains Marketplace plugin page and in the Plugins settings dialog.
         * Basic HTML elements such as text formatting, paragraphs, and lists are permitted.
         *
         * The change notes content is automatically encapsulated in `<![CDATA[... ]]>`.
         *
         * The inputted value will populate the `<change-notes>` element.
         *
         * @see PatchPluginXmlTask.changeNotes
         * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__change-notes">Plugin Configuration File: `change-notes`</a>
         */
        val changeNotes: Property<String>

        /**
         * Configures the `product-descriptor` section of the plugin.
         *
         * @see <a href="https://plugins.jetbrains.com/docs/marketplace/add-required-parameters.html">How to add required parameters for paid plugins</a>
         */
        @IntelliJPlatform
        interface ProductDescriptor {

            /**
             * The product code for the plugin, used in the JetBrains Sales System, needs to be pre-approved by JetBrains and must adhere to [specified requirements](https://plugins.jetbrains.com/docs/marketplace/obtain-a-product-code-from-jetbrains.html).
             *
             * The given value will be utilized as a `<product-descriptor code="">` element attribute.
             *
             * @see PatchPluginXmlTask.productDescriptorCode
             * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor">Plugin Configuration File: `product-descriptor`</a>
             */
            val code: Property<String>

            /**
             * The release date of the major version, formatted as `YYYYMMDD`.
             *
             * The supplied value will be used to populate the `<product-descriptor release-date="">` element attribute.
             *
             * @see PatchPluginXmlTask.productDescriptorReleaseDate
             * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor">Plugin Configuration File: `product-descriptor`</a>
             */
            val releaseDate: Property<String>

            /**
             * The major version, represented in a specific numerical format.
             *
             * The given value will be used as the `<product-descriptor release-version="">` element attribute.
             *
             * @see PatchPluginXmlTask.productDescriptorReleaseVersion
             * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor">Plugin Configuration File: `product-descriptor`</a>
             */
            val releaseVersion: Property<String>

            /**
             * The boolean value that indicates if the plugin is a [Freemium](https://plugins.jetbrains.com/docs/marketplace/freemium.html) plugin.
             *
             * The inputted value will be used for the `<product-descriptor optional="">` element attribute.
             *
             * Default value: `false`.
             *
             * @see PatchPluginXmlTask.productDescriptorOptional
             * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor">Plugin Configuration File: `product-descriptor`</a>
             */
            val optional: Property<Boolean>
        }

        /**
         * Configures the `idea-version` section of the plugin.
         */
        @IntelliJPlatform
        interface IdeaVersion {

            /**
             * The earliest IDE version that is compatible with the plugin.
             *
             * The supplied value will be utilized as the `<idea-version since-build=""/>` element attribute.
             *
             * The default value is set to the `MAJOR.MINOR` version based on the currently selected IntelliJ Platform, like `233.12345`.
             *
             * @see PatchPluginXmlTask.sinceBuild
             * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__idea-version">Plugin Configuration File: `idea-version`</a>
             */
            val sinceBuild: Property<String>

            /**
             * The latest IDE version that is compatible with the plugin. An undefined value signifies compatibility with all IDEs starting from the version mentioned in `since-build`, including potential future builds that may cause compatibility issues.
             *
             * The given value will be assigned to the `<idea-version until-build=""/>` element attribute.
             *
             * The default value is set to the `MAJOR.*` version based on the currently selected IntelliJ Platform, such as `233.*`.
             *
             * The `until-build` attribute can be unset by setting `provider { null }` as a value.
             * Note that passing only `null` will make Gradle use a default value instead.
             *
             * @see PatchPluginXmlTask.untilBuild
             * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__idea-version">Plugin Configuration File: `idea-version`</a>
             */
            val untilBuild: Property<String>
        }

        /**
         * Configures the `vendor` section of the plugin.
         */
        @IntelliJPlatform
        interface Vendor {

            /**
             * The name of the vendor or the organization ID (if created), as displayed in the Plugins settings dialog and on the JetBrains Marketplace plugin page.
             *
             * The supplied value will be used as the value for the `<vendor>` element.
             *
             * @see PatchPluginXmlTask.vendorName
             * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__vendor">Plugin Configuration File: `vendor`</a>
             */
            val name: Property<String>

            /**
             * The email address of the vendor.
             *
             * The given value will be utilized as the `<vendor email="">` element attribute.
             *
             * @see PatchPluginXmlTask.vendorEmail
             * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__vendor">Plugin Configuration File</a>
             */
            val email: Property<String>

            /**
             * The URL to the vendor's homepage.
             *
             * The supplied value will be assigned to the `<vendor url="">` element attribute.
             *
             * @see PatchPluginXmlTask.vendorUrl
             * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__vendor">Plugin Configuration File</a>
             */
            val url: Property<String>
        }
    }

    /**
     * Plugin publishing configuration.
     */
    @IntelliJPlatform
    interface Publishing {

        /**
         * The hostname used for publishing the plugin.
         *
         * Default value: [Locations.MARKETPLACE]
         *
         * @see PublishPluginTask.host
         */
        val host: Property<String>

        /**
         * Authorization token.
         *
         * @see PublishPluginTask.token
         */
        val token: Property<String>

        /**
         * A list of channel names to upload plugin to.
         *
         * Default value: `listOf("default")`
         *
         * @see PublishPluginTask.channels
         */
        val channels: ListProperty<String>

        /**
         * Specifies if the Toolbox Enterprise plugin repository service should be used.
         *
         * Default value: `false`
         *
         * @see PublishPluginTask.toolboxEnterprise
         */
        val toolboxEnterprise: Property<Boolean>

        /**
         * Publish the plugin update and mark it as hidden to prevent public release after approval.
         *
         * Default value: `false`
         *
         * @see PublishPluginTask.hidden
         * @see <a href="https://plugins.jetbrains.com/docs/marketplace/hidden-plugin.html">Hidden release</a>
         */
        val hidden: Property<Boolean>
    }

    /**
     * Plugin signing configuration.
     *
     * @see SignPluginTask
     * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-signing.html">Plugin Signing</a>
     */
    @IntelliJPlatform
    interface Signing {

        /**
         * A path to the local Marketplace ZIP Signer CLI tool to be used.
         *
         * @see SigningAware
         */
        val cliPath: RegularFileProperty

        /**
         * KeyStore file.
         * Refers to `ks` CLI option.
         *
         * @see SignPluginTask.keyStore
         */
        val keyStore: RegularFileProperty

        /**
         * KeyStore password.
         * Refers to `ks-pass` CLI option.
         *
         * @see SignPluginTask.keyStorePassword
         */
        val keyStorePassword: Property<String>

        /**
         * KeyStore key alias.
         * Refers to `ks-key-alias` CLI option.
         *
         * @see SignPluginTask.keyStoreKeyAlias
         */
        val keyStoreKeyAlias: Property<String>

        /**
         * KeyStore type.
         * Refers to `ks-type` CLI option.
         *
         * @see SignPluginTask.keyStoreType
         */
        val keyStoreType: Property<String>

        /**
         * JCA KeyStore Provider name.
         * Refers to `ks-provider-name` CLI option.
         *
         * @see SignPluginTask.keyStoreProviderName
         */
        val keyStoreProviderName: Property<String>

        /**
         * Encoded private key in the PEM format.
         * Refers to `key` CLI option.
         *
         * Takes precedence over the [privateKeyFile] property.
         *
         * @see SignPluginTask.privateKey
         */
        val privateKey: Property<String>

        /**
         * A file with an encoded private key in the PEM format.
         * Refers to `key-file` CLI option.
         *
         * @see SignPluginTask.privateKeyFile
         */
        val privateKeyFile: RegularFileProperty

        /**
         * Password required to decrypt the private key.
         * Refers to `key-pass` CLI option.
         *
         * @see SignPluginTask.password
         */
        val password: Property<String>

        /**
         * A string containing X509 certificates.
         * The first certificate from the chain will be used as a certificate authority (CA).
         * Refers to `cert` CLI option.
         *
         * Takes precedence over the [certificateChainFile] property.
         *
         * @see SignPluginTask.certificateChain
         */
        val certificateChain: Property<String>

        /**
         * Path to the file containing X509 certificates.
         * The first certificate from the chain will be used as a certificate authority (CA).
         * Refers to `cert-file` CLI option.
         *
         * @see SignPluginTask.certificateChainFile
         */
        val certificateChainFile: RegularFileProperty
    }

    /**
     * IntelliJ Plugin Verifier CLI tool configuration.
     *
     * @see PluginVerifierAware
     */
    @IntelliJPlatform
    interface VerifyPlugin : ExtensionAware {

        /**
         * The extension to define the IDEs to be used along with the IntelliJ Plugin Verifier CLI tool for the binary plugin verification.
         *
         * @see Ides
         */
        val ides
            get() = extensions.getByName<Ides>(Extensions.IDES)

        /**
         * A path to the local IntelliJ Plugin Verifier CLI tool to be used.
         *
         * @see PluginVerifierAware
         */
        val cliPath: RegularFileProperty

        /**
         * The path to the directory where IDEs used for the verification will be downloaded.
         *
         * Default value: [homeDirectory]/ides
         *
         * @see VerifyPlugin.downloadDirectory
         */
        val downloadDirectory: DirectoryProperty

        /**
         * The list of class prefixes from the external libraries.
         * The Plugin Verifier will not report `No such class` for classes of these packages.
         *
         * @see VerifyPlugin.externalPrefixes
         */
        val externalPrefixes: ListProperty<String>

        /**
         * Defines the verification level at which the task should fail if any reported issue matches.
         *
         * Default value: [FailureLevel.COMPATIBILITY_PROBLEMS]
         *
         * @see FailureLevel
         * @see VerifyPlugin.failureLevel
         */
        val failureLevel: ListProperty<FailureLevel>

        /**
         * The list of free arguments is passed directly to the IntelliJ Plugin Verifier CLI tool.
         *
         * They can be used in addition to the arguments that are provided by dedicated options.
         *
         * @see VerifyPlugin.freeArgs
         */
        val freeArgs: ListProperty<String>

        /**
         * Retrieve the Plugin Verifier home directory used for storing downloaded IDEs.
         * Following home directory resolving method is taken directly from the Plugin Verifier to keep the compatibility.
         *
         * Default value:
         * - Directory specified with `plugin.verifier.home.dir` system property
         * - Directory specified with `XDG_CACHE_HOME` environment variable
         * - ~/.cache/pluginVerifier
         * - [ProjectLayout.getBuildDirectory]/tmp/pluginVerifier
         *
         * @see VerifyPlugin.homeDirectory
         */
        val homeDirectory: DirectoryProperty

        /**
         * A file that contains a list of problems that will be ignored in a report.
         *
         * @see VerifyPlugin.ignoredProblemsFile
         */
        val ignoredProblemsFile: RegularFileProperty

        /**
         * Specifies which subsystems of IDE should be checked.
         *
         * Default value: [Subsystems.ALL]
         *
         * @see Subsystems
         * @see VerifyPlugin.subsystemsToCheck
         */
        val subsystemsToCheck: Property<Subsystems>

        /**
         * A flag that controls the output format - if set to `true`, the TeamCity compatible output will be returned to stdout.
         *
         * Default value: `false`
         *
         * @see VerifyPlugin.teamCityOutputFormat
         */
        val teamCityOutputFormat: Property<Boolean>

        /**
         * The path to the directory where verification reports will be saved.
         *
         * Default value: [ProjectLayout.getBuildDirectory]/reports/pluginVerifier
         *
         * @see VerifyPlugin.verificationReportsDirectory
         */
        val verificationReportsDirectory: DirectoryProperty

        /**
         * The output formats of the verification reports.
         *
         * Default value: ([VerificationReportsFormats.PLAIN], [VerificationReportsFormats.HTML])
         *
         * @see VerificationReportsFormats
         * @see VerifyPlugin.verificationReportsFormats
         */
        val verificationReportsFormats: ListProperty<VerificationReportsFormats>

        /**
         * The extension to define the IDEs to be used along with the IntelliJ Plugin Verifier CLI tool for the binary plugin verification.
         *
         * It provides a set of helpers which add relevant entries to the configuration, which later is used to resolve IntelliJ-based IDE binary releases.
         */
        @IntelliJPlatform
        abstract class Ides @Inject constructor(
            internal val dependencies: DependencyHandler,
            internal val downloadDirectory: DirectoryProperty,
            internal val extensionProvider: Provider<IntelliJPlatformExtension>,
            internal val gradle: Gradle,
            internal val providers: ProviderFactory,
            internal val resources: ResourceHandler,
        ) {

            /**
             * Adds a dependency to a binary IDE release to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param type The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
             * @param version The provider for the version of the IntelliJ Platform dependency.
             */
            fun ide(type: Provider<*>, version: Provider<String>) = dependencies.addProvider(
                Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY,
                type
                    .map {
                        when (it) {
                            is IntelliJPlatformType -> it
                            is String -> it.toIntelliJPlatformType()
                            else -> throw IllegalArgumentException("Invalid argument type: '${it.javaClass}'. Supported types: String or ${IntelliJPlatformType::class.java}")
                        }
                    }
                    .zip(version) { typeValue, versionValue ->
                        typeValue.binary ?: return@zip null

                        downloadDirectory
                            .dir("${typeValue}-${versionValue}")
                            .asPath
                            .takeIf { it.exists() }
                            ?.let {
                                // IDE is already present in the [downloadDirectory], use as [localIde]
                                localIde(it.pathString)
                                return@zip null
                            }

                        dependencies.create(
                            group = typeValue.binary.group,
                            name = typeValue.binary.name,
                            version = versionValue,
                            ext = "tar.gz",
                        )
                    }
            )

            /**
             * Adds a dependency to a binary IDE release to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param type The IntelliJ Platform dependency.
             * @param version The version of the IntelliJ Platform dependency.
             */
            fun ide(type: IntelliJPlatformType, version: String) = ide(providers.provider { type }, providers.provider { version })

            /**
             * Adds a dependency to a binary IDE release to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param type The IntelliJ Platform dependency.
             * @param version The version of the IntelliJ Platform dependency.
             */
            fun ide(type: String, version: String) = ide(type.toIntelliJPlatformType(), version)

            /**
             * Adds a dependency to a binary IDE release to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param definition The IntelliJ Platform dependency. Accepts [String] in `TYPE-VERSION` or `VERSION` format.
             */
            fun ide(definition: String) = definition.split('-').let {
                when {
                    it.size == 2 -> ide(it.first(), it.last())
                    else -> ide(IntelliJPlatformType.IntellijIdeaCommunity, it.first())
                }
            }

            /**
             * Adds the local IDE to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param localPath The provider for the type of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
             */
            fun localIde(localPath: Provider<*>) = dependencies.addProvider(
                Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_LOCAL_INSTANCE,
                localPath.map {
                    val artifactPath = resolveArtifactPath(it)
                    val productInfo = artifactPath.productInfo()

                    productInfo.validateSupportedVersion()

                    val type = productInfo.productCode.toIntelliJPlatformType()
                    val hash = artifactPath.pathString.hashCode().absoluteValue % 1000

                    dependencies.create(
                        group = Configurations.Dependencies.LOCAL_IDE_GROUP,
                        name = type.dependency.name,
                        version = "${productInfo.version}+$hash",
                    ).apply {
                        createIvyDependency(gradle, listOf(artifactPath.toPublication()))
                    }
                }
            )

            /**
             * Adds the local IDE to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param localPath The IntelliJ Platform dependency.
             */
            fun localIde(localPath: String) = localIde(providers.provider { localPath })

            /**
             * Adds the local IDE to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param localPath The IntelliJ Platform dependency.
             */
            fun localIde(localPath: File) = localIde(providers.provider { localPath })

            /**
             * Adds the local IDE to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param localPath The IntelliJ Platform dependency.
             */
            fun localIde(localPath: Directory) = localIde(providers.provider { localPath })

            /**
             * Retrieves matching IDEs using the default configuration based on the currently used IntelliJ Platform and applies them
             * for IntelliJ Platform Verifier using the [ide] helper method.
             *
             * @see ide
             * @see listProductReleases
             * @see ProductReleasesValueSource
             */
            fun recommended() = listProductReleases().get().map { ide(it) }

            /**
             * Retrieves matching IDEs using custom filter parameters.
             *
             * @see ide
             * @see listProductReleases
             * @see ProductReleasesValueSource
             */
            fun select(configure: ProductReleasesValueSource.FilterParameters.() -> Unit = {}) = listProductReleases(configure).get().map { ide(it) }

            /**
             * Prepares a [ProductReleasesValueSource] instance for the further usage, so it's possible to retrieve and filter IDEs based on the currently
             * used platform or given criteria.
             *
             * @param configure The lambda function to configure the parameters for obtaining the product releases. Defaults to an empty action.
             * @see ProductReleasesValueSource
             */
            private fun listProductReleases(configure: ProductReleasesValueSource.FilterParameters.() -> Unit = {}) = ProductReleasesValueSource(configure)
        }
    }
}

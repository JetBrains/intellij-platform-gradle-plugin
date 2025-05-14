// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Extensions
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.IntelliJPlatform
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.plugins.configureExtension
import org.jetbrains.intellij.platform.gradle.providers.ProductReleasesValueSource.FilterParameters
import org.jetbrains.intellij.platform.gradle.tasks.BuildSearchableOptionsTask
import org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.platform.gradle.tasks.PublishPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.SignPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.*
import org.jetbrains.intellij.platform.gradle.tasks.aware.PluginVerifierAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SigningAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.*
import java.io.File
import java.nio.file.Path
import javax.inject.Inject

/**
 * The IntelliJ Platform Gradle Plugin extension.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@IntelliJPlatform
abstract class IntelliJPlatformExtension @Inject constructor(
    configurations: ConfigurationContainer,
    providers: ProviderFactory,
    rootProjectDirectory: Path,
) : ExtensionAware {

    private val intelliJPlatformConfiguration = configurations[Configurations.INTELLIJ_PLATFORM_DEPENDENCY].asLenient

    /**
     * Provides read-only access to the IntelliJ Platform project cache location.
     */
    val cachePath by lazy {
        providers.intellijPlatformCachePath(rootProjectDirectory)
    }

    /**
     * Provides read-only access to the IntelliJ Platform dependency artifact path.
     */
    val platformPath: Path
        get() = intelliJPlatformConfiguration.platformPath()

    /**
     * Provides read-only access to the [ProductInfo] object associated with the IntelliJ Platform dependency configured for the current project.
     */
    val productInfo: ProductInfo
        get() = platformPath.productInfo()

    /**
     * Enables auto-reload of dynamic plugins.
     * Dynamic plugin will be reloaded automatically when its content is modified.
     * This allows a much faster development cycle by avoiding a full restart of the development instance after code changes.
     *
     * Default value: `true`
     */
    abstract val autoReload: Property<Boolean>

    /**
     * Builds an index of UI components (searchable options) for the plugin.
     * Controls the execution of the [BuildSearchableOptionsTask] task.
     *
     * Default value: `true`
     */
    abstract val buildSearchableOptions: Property<Boolean>

    /**
     * Enables the compiled classes instrumentation.
     * The compiled code will be enhanced with:
     * - nullability assertions
     * - post-processing of forms created by IntelliJ GUI Designer
     *
     * Default value: `true`
     */
    abstract val instrumentCode: Property<Boolean>

    /**
     * Defines the project name, which is used for creating file structure and the build archive.
     *
     * Default value: [Project.getName]
     */
    abstract val projectName: Property<String>

    /**
     * The path to the sandbox container where tests and IDE instances read and write data.
     *
     * Default value: [ProjectLayout.getBuildDirectory]/[Sandbox.CONTAINER]/
     */
    abstract val sandboxContainer: DirectoryProperty

    /**
     * Enables Split Mode when running the IDE.
     *
     * When you develop a plugin, you may want to check how it works in remote development mode, when one machine is running the backend part and another
     * is running a frontend part (JetBrains Client) which connects to the backend.
     *
     * This property allows running the IDE with backend and frontend parts running in separate processes.
     * The developed plugin is installed in the backend part by default, this can be changed via [splitModeTarget].
     *
     * Default value: `false`
     */
    abstract val splitMode: Property<Boolean>

    /**
     * Taken into account only if [splitMode] is set to `true` and specifies in which part of the IDE the plugin
     * should be installed when `runIde` task is executed: the backend process, the frontend process, or both.
     *
     * Default value: [SplitModeAware.SplitModeTarget.BACKEND]
     */
    abstract val splitModeTarget: Property<SplitModeAware.SplitModeTarget>


    val pluginConfiguration
        get() = extensions.getByName<PluginConfiguration>(Extensions.PLUGIN_CONFIGURATION)

    fun pluginConfiguration(action: Action<in PluginConfiguration>) {
        action.execute(pluginConfiguration)
    }

    fun pluginConfiguration(
        @DelegatesTo(
            value = PluginConfiguration::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        action.delegate = pluginConfiguration
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action.call()
    }


    val publishing
        get() = extensions.getByName<Publishing>(Extensions.PUBLISHING)

    fun publishing(action: Action<in Publishing>) {
        action.execute(publishing)
    }

    fun publishing(@DelegatesTo(value = Publishing::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>) {
        action.delegate = publishing
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action.call()
    }


    val signing
        get() = extensions.getByName<Signing>(Extensions.SIGNING)

    fun signing(action: Action<in Signing>) {
        action.execute(signing)
    }

    fun signing(@DelegatesTo(value = Signing::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>) {
        action.delegate = signing
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action.call()
    }


    @Deprecated("Use pluginVerification instead", ReplaceWith("pluginVerification(action)"))
    val verifyPlugin
        get() = extensions.getByName<PluginVerification>(Extensions.PLUGIN_VERIFICATION)

    @Deprecated("Use pluginVerification instead", ReplaceWith("pluginVerification(action)"))
    fun verifyPlugin(action: Action<in PluginVerification>) {
        action.execute(pluginVerification)
    }

    @Deprecated("Use pluginVerification instead", ReplaceWith("pluginVerification(action)"))
    fun verifyPlugin(
        @DelegatesTo(
            value = PluginVerification::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        action.delegate = pluginVerification
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action.call()
    }

    @Deprecated("Use PluginVerification instead", ReplaceWith("PluginVerification"))
    interface VerifyPlugin : PluginConfiguration


    val pluginVerification
        get() = extensions.getByName<PluginVerification>(Extensions.PLUGIN_VERIFICATION)

    fun pluginVerification(action: Action<in PluginVerification>) {
        action.execute(pluginVerification)
    }

    fun pluginVerification(
        @DelegatesTo(
            value = PluginVerification::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        action.delegate = pluginVerification
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action.call()
    }

    /**
     * Configures the plugin definition and stores in the `plugin.xml` file.
     */
    @IntelliJPlatform
    interface PluginConfiguration : ExtensionAware {

        val productDescriptor
            get() = extensions.getByName<ProductDescriptor>(Extensions.PRODUCT_DESCRIPTOR)

        fun productDescriptor(action: Action<in ProductDescriptor>) {
            action.execute(productDescriptor)
        }

        fun productDescriptor(
            @DelegatesTo(
                value = ProductDescriptor::class,
                strategy = Closure.DELEGATE_FIRST
            ) action: Closure<*>
        ) {
            action.delegate = productDescriptor
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }


        val ideaVersion
            get() = extensions.getByName<IdeaVersion>(Extensions.IDEA_VERSION)

        fun ideaVersion(action: Action<in IdeaVersion>) {
            action.execute(ideaVersion)
        }

        fun ideaVersion(
            @DelegatesTo(
                value = IdeaVersion::class,
                strategy = Closure.DELEGATE_FIRST
            ) action: Closure<*>
        ) {
            action.delegate = ideaVersion
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }


        val vendor
            get() = extensions.getByName<Vendor>(Extensions.VENDOR)

        fun vendor(action: Action<in Vendor>) {
            action.execute(vendor)
        }

        fun vendor(@DelegatesTo(value = Vendor::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>) {
            action.delegate = vendor
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }


        /**
         * The plugin's unique identifier.
         * This should mirror the structure of fully qualified Java packages and must remain distinct from the IDs of existing plugins.
         * This ID is a technical descriptor used not only within the IDE but also on [JetBrains Marketplace](https://plugins.jetbrains.com/).
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

            /**
             * Specifies the boolean value determining whether the plugin is an EAP release.
             *
             * The provided value will be assigned to the [`<product-descriptor eap="">`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor) element attribute.
             *
             * Default value: `false`.
             *
             * @see PatchPluginXmlTask.productDescriptorEap
             * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor">Plugin Configuration File: `product-descriptor`</a>
             */
            val eap: Property<Boolean>

            companion object : Registrable<ProductDescriptor> {
                override fun register(project: Project, target: Any) =
                    target.configureExtension<ProductDescriptor>(Extensions.PRODUCT_DESCRIPTOR)
            }
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

            companion object : Registrable<IdeaVersion> {
                override fun register(project: Project, target: Any) =
                    target.configureExtension<IdeaVersion>(Extensions.IDEA_VERSION) {
                        val buildVersion = project.extensionProvider.map {
                            it.runCatching { productInfo.buildNumber.toVersion() }.getOrDefault(Version())
                        }
                        sinceBuild.convention(buildVersion.map { "${it.major}.${it.minor}" })
                    }
            }
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

            companion object : Registrable<Vendor> {
                override fun register(project: Project, target: Any) =
                    target.configureExtension<Vendor>(Extensions.VENDOR)
            }
        }

        companion object : Registrable<PluginConfiguration> {
            override fun register(project: Project, target: Any) =
                target.configureExtension<PluginConfiguration>(Extensions.PLUGIN_CONFIGURATION) {
                    version.convention(project.provider { project.version.toString() })
                }
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
         * Default value: [Locations.JETBRAINS_MARKETPLACE]
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
         * A list of channel names to upload the plugin to.
         *
         * Default value: `listOf("default")`
         *
         * @see PublishPluginTask.channels
         */
        val channels: ListProperty<String>

        /**
         * Specifies if the IDE Services plugin repository service should be used.
         *
         * Default value: `false`
         *
         * @see PublishPluginTask.ideServices
         */
        val ideServices: Property<Boolean>

        /**
         * Publish the plugin update and mark it as hidden to prevent public release after approval.
         *
         * Default value: `false`
         *
         * @see PublishPluginTask.hidden
         * @see <a href="https://plugins.jetbrains.com/docs/marketplace/hidden-plugin.html">Hidden release</a>
         */
        val hidden: Property<Boolean>

        companion object : Registrable<Publishing> {
            override fun register(project: Project, target: Any) =
                target.configureExtension<Publishing>(Extensions.PUBLISHING) {
                    host.convention(Locations.JETBRAINS_MARKETPLACE)
                    ideServices.convention(false)
                    channels.convention(listOf("default"))
                    hidden.convention(false)
                }
        }
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

        companion object : Registrable<Signing> {
            override fun register(project: Project, target: Any) =
                target.configureExtension<Signing>(Extensions.SIGNING)
        }
    }

    /**
     * IntelliJ Plugin Verifier CLI tool configuration.
     *
     * @see PluginVerifierAware
     */
    @IntelliJPlatform
    interface PluginVerification : ExtensionAware {

        /**
         * The extension to define the IDEs to be used along with the IntelliJ Plugin Verifier CLI tool for the binary plugin verification.
         *
         * @see Ides
         */
        val ides
            get() = extensions.getByName<Ides>(Extensions.IDES)

        fun ides(action: Action<in Ides>) {
            action.execute(ides)
        }

        fun ides(@DelegatesTo(value = Ides::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>) {
            action.delegate = ides
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }


        /**
         * A path to the local IntelliJ Plugin Verifier CLI tool to be used.
         *
         * @see PluginVerifierAware
         */
        val cliPath: RegularFileProperty

        /**
         * The list of class prefixes from the external libraries.
         * The Plugin Verifier will not report `No such class` for classes of these packages.
         *
         * @see PluginVerification.externalPrefixes
         */
        val externalPrefixes: ListProperty<String>

        /**
         * Defines the verification level at which the task should fail if any reported issue matches.
         *
         * Default value: [FailureLevel.COMPATIBILITY_PROBLEMS]
         *
         * @see FailureLevel
         * @see PluginVerification.failureLevel
         */
        val failureLevel: ListProperty<FailureLevel>

        /**
         * The list of free arguments is passed directly to the IntelliJ Plugin Verifier CLI tool.
         *
         * They can be used in addition to the arguments that are provided by dedicated options.
         *
         * @see PluginVerification.freeArgs
         */
        val freeArgs: ListProperty<String>

        /**
         * A file that contains a list of problems that will be ignored in a report.
         *
         * @see PluginVerification.ignoredProblemsFile
         */
        val ignoredProblemsFile: RegularFileProperty

        /**
         * Specifies which subsystems of the IDE should be checked.
         *
         * Default value: [Subsystems.ALL]
         *
         * @see Subsystems
         * @see PluginVerification.subsystemsToCheck
         */
        val subsystemsToCheck: Property<Subsystems>

        /**
         * A flag that controls the output format - if set to `true`, the TeamCity compatible output will be returned to stdout.
         *
         * Default value: `false`
         *
         * @see PluginVerification.teamCityOutputFormat
         */
        val teamCityOutputFormat: Property<Boolean>

        /**
         * The path to the directory where verification reports will be saved.
         *
         * Default value: [ProjectLayout.getBuildDirectory]/reports/pluginVerifier
         *
         * @see PluginVerification.verificationReportsDirectory
         */
        val verificationReportsDirectory: DirectoryProperty

        /**
         * The output formats of the verification reports.
         *
         * Default value: ([VerificationReportsFormats.PLAIN], [VerificationReportsFormats.HTML])
         *
         * @see VerificationReportsFormats
         * @see PluginVerification.verificationReportsFormats
         */
        val verificationReportsFormats: ListProperty<VerificationReportsFormats>

        /**
         * The extension to define the IDEs to be used along with the IntelliJ Plugin Verifier CLI tool for the binary plugin verification.
         *
         * It provides a set of helpers which add relevant entries to the configuration, which later is used to resolve IntelliJ-based IDE binary releases.
         *
         * @param dependenciesHelper IntelliJ Platform dependencies helper instance
         * @param extensionProvider IntelliJ Platform extension instance provider
         */
        @IntelliJPlatform
        abstract class Ides @Inject constructor(
            private val dependenciesHelper: IntelliJPlatformDependenciesHelper,
            private val extensionProvider: Provider<IntelliJPlatformExtension>,
        ) {

            /**
             * Adds a dependency to a binary IDE release to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param type The IntelliJ Platform dependency.
             * @param version The version of the IntelliJ Platform dependency.
             * @param useInstaller Switches between the IDE installer and archive from the IntelliJ Maven repository.
             */
            @JvmOverloads
            fun ide(type: IntelliJPlatformType, version: String, useInstaller: Boolean = true) =
                dependenciesHelper.addIntelliJPlatformDependency(
                    typeProvider = dependenciesHelper.provider { type },
                    versionProvider = dependenciesHelper.provider { version },
                    useInstallerProvider = dependenciesHelper.provider { useInstaller },
                    configurationName = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY,
                    intellijPlatformConfigurationName = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY,
                )

            /**
             * Adds a dependency to a binary IDE release to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param type The IntelliJ Platform dependency.
             * @param version The version of the IntelliJ Platform dependency.
             * @param useInstaller Switches between the IDE installer and archive from the IntelliJ Maven repository.
             */
            @JvmOverloads
            fun ide(type: String, version: String, useInstaller: Boolean = true) =
                dependenciesHelper.addIntelliJPlatformDependency(
                    typeProvider = dependenciesHelper.provider { type },
                    versionProvider = dependenciesHelper.provider { version },
                    useInstallerProvider = dependenciesHelper.provider { useInstaller },
                    configurationName = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY,
                    intellijPlatformConfigurationName = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY,
                )

            /**
             * Adds a dependency to a binary IDE release to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param type The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
             * @param version The provider for the version of the IntelliJ Platform dependency.
             * @param useInstaller Switches between the IDE installer and archive from the IntelliJ Maven repository.
             */
            @JvmOverloads
            fun ide(type: Provider<*>, version: Provider<String>, useInstaller: Boolean = true) =
                dependenciesHelper.addIntelliJPlatformDependency(
                    typeProvider = type,
                    versionProvider = version,
                    useInstallerProvider = dependenciesHelper.provider { useInstaller },
                    configurationName = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY,
                    intellijPlatformConfigurationName = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY,
                )

            /**
             * Adds a dependency to a binary IDE release to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param type The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
             * @param version The provider for the version of the IntelliJ Platform dependency.
             * @param useInstaller Switches between the IDE installer and archive from the IntelliJ Maven repository.
             */
            fun ide(type: Provider<*>, version: Provider<String>, useInstaller: Provider<Boolean>) = dependenciesHelper.addIntelliJPlatformDependency(
                typeProvider = type,
                versionProvider = version,
                useInstallerProvider = useInstaller,
                configurationName = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY,
                intellijPlatformConfigurationName = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY,
            )

            /**
             * Adds a dependency to a binary IDE release to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param notation The IntelliJ Platform dependency. Accepts [String] in `TYPE-VERSION` or `VERSION` format.
             */
            fun ide(notation: String) = dependenciesHelper.addIntelliJPluginVerifierIdes(
                notationsProvider = dependenciesHelper.provider { listOf(notation) },
            )

            /**
             * Adds a dependency to a binary IDE release to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param notation The IntelliJ Platform dependency. Accepts [String] in `TYPE-VERSION` or `VERSION` format.
             */
            fun ide(notation: Provider<String>) = dependenciesHelper.addIntelliJPluginVerifierIdes(
                notationsProvider = notation.map { listOf(it) },
            )

            /**
             * Adds dependencies to binary IDE releases to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param notations The IntelliJ Platform dependencies. Accepts [String] in `TYPE-VERSION` or `VERSION` format.
             */
            fun ides(notations: List<String>) = dependenciesHelper.addIntelliJPluginVerifierIdes(
                notationsProvider = dependenciesHelper.provider { notations },
            )

            /**
             * Adds dependencies to binary IDE releases to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param notations The IntelliJ Platform dependencies. Accepts [String] in `TYPE-VERSION` or `VERSION` format.
             */
            fun ides(notations: Provider<List<String>>) = dependenciesHelper.addIntelliJPluginVerifierIdes(
                notationsProvider = notations,
            )

            /**
             * Adds the local IDE to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param localPath The provider for the type of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
             */
            fun local(localPath: Provider<*>) = dependenciesHelper.addIntelliJPlatformLocalDependency(
                localPathProvider = localPath,
                configurationName = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_LOCAL_INSTANCE,
            )

            /**
             * Adds the local IDE to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param localPath The IntelliJ Platform dependency.
             */
            fun local(localPath: String) = dependenciesHelper.addIntelliJPlatformLocalDependency(
                localPathProvider = dependenciesHelper.provider { localPath },
                configurationName = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_LOCAL_INSTANCE,
            )

            /**
             * Adds the local IDE to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param localPath The IntelliJ Platform dependency.
             */
            fun local(localPath: File) = dependenciesHelper.addIntelliJPlatformLocalDependency(
                localPathProvider = dependenciesHelper.provider { localPath },
                configurationName = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_LOCAL_INSTANCE,
            )

            /**
             * Adds the local IDE to be used for testing with the IntelliJ Plugin Verifier.
             *
             * @param localPath The IntelliJ Platform dependency.
             */
            fun local(localPath: Directory) = dependenciesHelper.addIntelliJPlatformLocalDependency(
                localPathProvider = dependenciesHelper.provider { localPath },
                configurationName = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_LOCAL_INSTANCE,
            )

            /**
             * Retrieves matching IDEs using the default configuration based on the currently used IntelliJ Platform and applies them
             * for IntelliJ Platform Verifier using the [ide] helper method.
             *
             * @see ide
             * @see ProductReleasesValueSource
             */
            fun recommended() = dependenciesHelper.addIntelliJPluginVerifierIdes(
                notationsProvider = ProductReleasesValueSource(),
            )

            /**
             * Retrieves matching IDEs using custom filter parameters.
             *
             * @see ide
             * @see ProductReleasesValueSource
             */
            fun select(configure: FilterParameters.() -> Unit = {}) = dependenciesHelper.addIntelliJPluginVerifierIdes(
                notationsProvider = ProductReleasesValueSource(configure),
            )

            /**
             * Extension function for the [IntelliJPlatformExtension.PluginVerification.Ides] extension to let filter IDE binary releases just using [FilterParameters].
             */
            @Suppress("FunctionName")
            fun ProductReleasesValueSource(configure: FilterParameters.() -> Unit = {}) =
                dependenciesHelper.createProductReleasesValueSource {
                    val ideaVersionProvider = extensionProvider.map { it.pluginConfiguration.ideaVersion }

                    channels.convention(listOf(Channel.RELEASE, Channel.EAP, Channel.RC))
                    types.convention(extensionProvider.map {
                        listOf(it.productInfo.productCode.toIntelliJPlatformType())
                    })
                    sinceBuild.convention(ideaVersionProvider.flatMap { it.sinceBuild })
                    untilBuild.convention(ideaVersionProvider.flatMap { it.untilBuild })

                    configure()
                }

            companion object {
                fun register(
                    dependenciesHelper: IntelliJPlatformDependenciesHelper,
                    extensionProvider: Provider<IntelliJPlatformExtension>,
                    target: Any
                ) =
                    target.configureExtension<Ides>(
                        Extensions.IDES,
                        dependenciesHelper,
                        extensionProvider,
                    )
            }
        }

        companion object : Registrable<PluginVerification> {
            override fun register(project: Project, target: Any) =
                target.configureExtension<PluginVerification>(Extensions.PLUGIN_VERIFICATION) {
                    failureLevel.convention(listOf(FailureLevel.COMPATIBILITY_PROBLEMS))
                    verificationReportsDirectory.convention(project.layout.buildDirectory.dir("reports/pluginVerifier"))
                    verificationReportsFormats.convention(
                        listOf(
                            VerificationReportsFormats.PLAIN,
                            VerificationReportsFormats.HTML,
                        )
                    )
                    teamCityOutputFormat.convention(false)
                    subsystemsToCheck.convention(Subsystems.ALL)
                }
        }
    }

    companion object : Registrable<IntelliJPlatformExtension> {
        override fun register(project: Project, target: Any) =
            target.configureExtension<IntelliJPlatformExtension>(
                Extensions.INTELLIJ_PLATFORM,
                project.configurations,
                project.providers,
                project.rootProjectPath,
            ) {
                autoReload.convention(true)
                buildSearchableOptions.convention(true)
                instrumentCode.convention(true)
                projectName.convention(project.name)
                sandboxContainer.convention(project.layout.buildDirectory.dir(Sandbox.CONTAINER))
                splitMode.convention(false)
                splitModeTarget.convention(SplitModeAware.SplitModeTarget.BACKEND)
            }
    }
}

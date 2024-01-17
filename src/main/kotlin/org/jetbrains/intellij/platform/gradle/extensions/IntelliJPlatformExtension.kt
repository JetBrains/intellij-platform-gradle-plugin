// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.GradleException
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.DirectoryProperty
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
import org.jetbrains.intellij.platform.gradle.utils.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Extensions
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.MINIMAL_SUPPORTED_INTELLIJ_PLATFORM_VERSION
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.model.ProductInfo
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.model.toPublication
import org.jetbrains.intellij.platform.gradle.provider.ProductReleasesValueSource
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.VerificationReportsFormats
import org.jetbrains.intellij.platform.gradle.utils.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import java.io.File
import javax.inject.Inject
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.math.absoluteValue

@Suppress("unused", "MemberVisibilityCanBePrivate")
@IntelliJPlatform
interface IntelliJPlatformExtension : ExtensionAware {

    /**
     * Instrument Java classes with nullability assertions and compile forms created by IntelliJ GUI Designer.
     *
     * Default value: `true`
     */
    val instrumentCode: Property<Boolean>

    val buildSearchableOptions: Property<Boolean>

    val sandboxContainer: DirectoryProperty

    val pluginConfiguration
        get() = extensions.getByName<PluginConfiguration>(Extensions.PLUGIN_CONFIGURATION)

    val pluginVerifier
        get() = extensions.getByName<PluginVerifier>(Extensions.PLUGIN_VERIFIER)

    val publishing
        get() = extensions.getByName<Publishing>(Extensions.PUBLISHING)

    val signing
        get() = extensions.getByName<Signing>(Extensions.SIGNING)

    @IntelliJPlatform
    interface PluginConfiguration : ExtensionAware {

        val productDescriptor
            get() = extensions.getByName<ProductDescriptor>(Extensions.PRODUCT_DESCRIPTOR)

        val ideaVersion
            get() = extensions.getByName<IdeaVersion>(Extensions.IDEA_VERSION)

        val vendor
            get() = extensions.getByName<Vendor>(Extensions.VENDOR)

        /**
         * A unique identifier of the plugin.
         * It should be a fully qualified name similar to Java packages and must not collide with the ID of existing plugins.
         * The ID is a technical value used to identify the plugin in the IDE and [JetBrains Marketplace](https://plugins.jetbrains.com/).
         * Please use characters, numbers, and `.`/`-`/`_` symbols only and keep it reasonably short.
         *
         * The provided value will be set as a value of the `<id>` element.
         *
         * See [Plugin Configuration File: `id`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__id) documentation for more details.
         */
        val id: Property<String>

        /**
         * The user-visible plugin display name (Title Case).
         *
         * The provided value will be set as a value of the `<name>` element.
         *
         * See [Plugin Configuration File: `name`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__name) documentation for more details.
         */
        val name: Property<String>

        /**
         * The plugin version displayed in the Plugins settings dialog and in the JetBrains Marketplace plugin page.
         * Plugins uploaded to the JetBrains Marketplace must follow semantic versioning.
         *
         * The provided value will be set as a value of the `<version>` element.
         *
         * See [Plugin Configuration File: `version`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__version) documentation for more details.
         */
        val version: Property<String>

        /**
         * The plugin description displayed on the JetBrains Marketplace plugin page and in the Plugins settings dialog.
         * Simple HTML elements, like text formatting, paragraphs, lists, etc., are allowed and must be wrapped into `<![CDATA[... ]]>` section.
         *
         * The provided value will be set as a value of the `<description>` element.
         *
         * See [Plugin Configuration File: `description`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__description) documentation for more details.
         */
        val description: Property<String>

        /**
         * A short summary of new features, bugfixes, and changes provided with the latest plugin version. Change notes are displayed on the JetBrains Marketplace plugin page and in the Plugins settings dialog.
         * Simple HTML elements, like text formatting, paragraphs, lists, etc., are allowed and must be wrapped into `<![CDATA[... ]]>` section.
         *
         * The provided value will be set as a value of the `<change-notes>` element.
         *
         * See [Plugin Configuration File: `description`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__change-notes) documentation for more details.
         */
        val changeNotes: Property<String>

        @IntelliJPlatform
        interface ProductDescriptor {

            /**
             * The plugin product code used in the JetBrains Sales System.
             * The code must be agreed with JetBrains in advance and follow [the requirements](https://plugins.jetbrains.com/docs/marketplace/obtain-a-product-code-from-jetbrains.html).
             *
             * The provided value will be set as a value of the `<product-descriptor code="">` element attribute.
             *
             * See [Plugin Configuration File: `product-descriptor`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor) documentation for more details.
             */
            val code: Property<String>

            /**
             * Date of the major version release in the `YYYYMMDD` format.
             *
             * The provided value will be set as a value of the `<product-descriptor release-date="">` element attribute.
             *
             * See [Plugin Configuration File: `product-descriptor`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor) documentation for more details.
             */
            val releaseDate: Property<String>

            /**
             * A major version in a special number format.
             *
             * The provided value will be set as a value of the `<product-descriptor release-version="">` element attribute.
             *
             * See [Plugin Configuration File: `product-descriptor`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor) documentation for more details.
             */
            val releaseVersion: Property<String>

            /**
             * The boolean value determining whether the plugin is a [Freemium](https://plugins.jetbrains.com/docs/marketplace/freemium.html) plugin.
             * Default value: `false`.
             *
             * The provided value will be set as a value of the `<product-descriptor optional="">` element attribute.
             *
             * See [Plugin Configuration File: `product-descriptor`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__product-descriptor) documentation for more details.
             */
            val optional: Property<Boolean>
        }

        @IntelliJPlatform
        interface IdeaVersion {

            /**
             * The lowest IDE version compatible with the plugin.
             *
             * The provided value will be set as a value of the `<idea-version since-build=""/>` element attribute.
             *
             * See [Plugin Configuration File: `idea-version`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__idea-version) documentation for more details.
             */
            val sinceBuild: Property<String>

            /**
             * The highest IDE version compatible with the plugin.
             * Undefined value declares compatibility with all the IDEs since the version specified by the since-build (also with the future builds what may cause incompatibility errors).
             *
             * The provided value will be set as a value of the `<idea-version since-build=""/>` element attribute.
             *
             * See [Plugin Configuration File: `idea-version`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__idea-version) documentation for more details.
             */
            val untilBuild: Property<String>
        }

        @IntelliJPlatform
        interface Vendor {

            /**
             * The vendor name or organization ID (if created) in the Plugins settings dialog and in the JetBrains Marketplace plugin page.
             *
             * The provided value will be set as a value of the `<vendor>` element.
             *
             * See [Plugin Configuration File: `vendor`](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__vendor) documentation for more details.
             */
            val name: Property<String>

            /**
             * The vendor's email address.
             *
             * The provided value will be set as a value of the `<vendor email="">` element attribute.
             *
             * See [Plugin Configuration File](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__vendor) documentation for more details.
             */
            val email: Property<String>

            /**
             * The link to the vendor's homepage.
             *
             * The provided value will be set as a value of the `<vendor url="">` element attribute.
             *
             * See [Plugin Configuration File](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__vendor) documentation for more details.
             */
            val url: Property<String>
        }
    }

    @IntelliJPlatform
    interface PluginVerifier : ExtensionAware {

        val ides
            get() = extensions.getByName<Ides>(Extensions.IDES)

        /**
         * A path to the local IntelliJ Plugin Verifier CLI tool to be used.
         */
        val cliPath: RegularFileProperty

        val freeArgs: ListProperty<String>

//        /**
//         * IDEs to check, in `intellij.version` format, i.e.: `["IC-2019.3.5", "PS-2019.3.2"]`.
//         * Check the available build versions on [IntelliJ Platform Builds list](https://jb.gg/intellij-platform-builds-list).
//         *
//         * Default value: output of the [ListProductsReleasesTask] task
//         */
//        val ideVersions: ListProperty<String>
//
//        /**
//         * A list of the paths to locally installed IDE distributions that should be used for verification in addition to those specified in [ideVersions].
//         */
//        val idePaths: ConfigurableFileCollection

        /**
         * Retrieve the Plugin Verifier home directory used for storing downloaded IDEs.
         * Following home directory resolving method is taken directly from the Plugin Verifier to keep the compatibility.
         */
        val homeDirectory: DirectoryProperty

        /**
         * The path to the directory where IDEs used for the verification will be downloaded.
         *
         * Default value: `System.getProperty("plugin.verifier.home.dir")/ides`, `System.getenv("XDG_CACHE_HOME")/pluginVerifier/ides`,
         * `System.getProperty("user.home")/.cache/pluginVerifier/ides` or system temporary directory.
         */
        val downloadDirectory: DirectoryProperty

        /**
         * Defines the verification level at which the task should fail if any reported issue matches.
         * Can be set as [FailureLevel] enum or [EnumSet<FailureLevel>].
         *
         * Default value: [FailureLevel.COMPATIBILITY_PROBLEMS]
         */
        val failureLevel: ListProperty<FailureLevel>

        /**
         * The path to the directory where verification reports will be saved.
         *
         * Default value: `${project.buildDir}/reports/pluginVerifier`
         */
        val verificationReportsDirectory: DirectoryProperty

        /**
         * The output formats of the verification reports.
         *
         * Accepted values:
         * - `plain` for console output
         * - `html`
         * ` `markdown`
         *
         * Default value: [VerificationReportsFormats.PLAIN], [VerificationReportsFormats.HTML]
         */
        val verificationReportsFormats: ListProperty<VerificationReportsFormats>

        /**
         * The list of classes prefixes from the external libraries.
         * The Plugin Verifier will not report `No such class` for classes of these packages.
         */
        val externalPrefixes: ListProperty<String>

        /**
         * A flag that controls the output format - if set to `true`, the TeamCity compatible output will be returned to stdout.
         *
         * Default value: `false`
         */
        val teamCityOutputFormat: Property<Boolean>

        /**
         * Specifies which subsystems of IDE should be checked.
         *
         * Default value: `all`
         *
         * Acceptable values:**
         * - `all`
         * - `android-only`
         * - `without-android`
         */
        val subsystemsToCheck: Property<String>

        /**
         * A file that contains a list of problems that will be ignored in a report.
         */
        val ignoredProblemsFile: RegularFileProperty

        @IntelliJPlatform
        abstract class Ides @Inject constructor(
            internal val dependencies: DependencyHandler,
            internal val downloadDirectory: DirectoryProperty,
            internal val extension: Provider<IntelliJPlatformExtension>,
            internal val gradle: Gradle,
            internal val productInfo: Provider<ProductInfo>,
            internal val providers: ProviderFactory,
            internal val resources: ResourceHandler,
        ) {

            fun ide(type: Provider<*>, version: Provider<String>) = dependencies.addProvider(
                Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY,
                type
                    .map {
                        when (it) {
                            is IntelliJPlatformType -> it
                            is String -> it.toIntelliJPlatformType()
                            else -> throw IllegalArgumentException("Invalid argument type: ${it.javaClass}. Supported types: String or ${IntelliJPlatformType::class.java}")
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

            fun ide(type: IntelliJPlatformType, version: String) = ide(providers.provider { type }, providers.provider { version })

            fun ide(type: String, version: String) = ide(type.toIntelliJPlatformType(), version)

            fun ide(definition: String) = definition.split('-').let {
                when {
                    it.size == 2 -> ide(it.first(), it.last())
                    else -> ide(IntelliJPlatformType.IntellijIdeaCommunity, it.first())
                }
            }

            fun localIde(localPath: Provider<String>) = dependencies.addProvider(
                Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_LOCAL_INSTANCE,
                localPath.map {
                    val artifactPath = resolveArtifactPath(it)
                    val productInfo = artifactPath.productInfo()

                    val type = productInfo.productCode.toIntelliJPlatformType()
                    if (productInfo.buildNumber.toVersion() < MINIMAL_SUPPORTED_INTELLIJ_PLATFORM_VERSION.toVersion()) {
                        throw GradleException("The minimal supported IDE version is $MINIMAL_SUPPORTED_INTELLIJ_PLATFORM_VERSION+, the provided version is too low: ${productInfo.version} (${productInfo.buildNumber})")
                    }
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

            fun localIde(localPath: String) = localIde(providers.provider { localPath })

            fun localIde(localPath: File) = localIde(providers.provider { localPath.absolutePath })

            fun listProductReleases(configure: ProductReleasesValueSource.Parameters.() -> Unit = {}) = ProductReleasesValueSource(configure)

            fun recommended() = listProductReleases().get().map { ide(it) }
        }
    }

    @IntelliJPlatform
    interface Publishing {

        val host: Property<String>

        val token: Property<String>

        val channel: Property<String>

        val toolboxEnterprise: Property<Boolean>

        val hidden: Property<Boolean>
    }

    @IntelliJPlatform
    interface Signing {

        /**
         * A path to the local Marketplace ZIP Signer CLI tool to be used.
         */
        val cliPath: RegularFileProperty

        /**
         * KeyStore file path.
         * Refers to `ks` CLI option.
         */
        val keyStore: Property<String>

        /**
         * KeyStore password.
         * Refers to `ks-pass` CLI option.
         */
        val keyStorePassword: Property<String>

        /**
         * KeyStore key alias.
         * Refers to `ks-key-alias` CLI option.
         */
        val keyStoreKeyAlias: Property<String>

        /**
         * KeyStore type.
         * Refers to `ks-type` CLI option.
         */
        val keyStoreType: Property<String>

        /**
         * JCA KeyStore Provider name.
         * Refers to `ks-provider-name` CLI option.
         */
        val keyStoreProviderName: Property<String>

        /**
         * Encoded private key in the PEM format.
         * Refers to `key` CLI option.
         */
        val privateKey: Property<String>

        /**
         * A file with an encoded private key in the PEM format.
         * Refers to `key-file` CLI option.
         */
        val privateKeyFile: RegularFileProperty

        /**
         * Password required to decrypt the private key.
         * Refers to `key-pass` CLI option.
         */
        val password: Property<String>

        /**
         * A string containing X509 certificates.
         * The first certificate from the chain will be used as a certificate authority (CA).
         * Refers to `cert` CLI option.
         */
        val certificateChain: Property<String>

        /**
         * Path to the file containing X509 certificates.
         * The first certificate from the chain will be used as a certificate authority (CA).
         * Refers to `cert-file` CLI option.
         */
        val certificateChainFile: RegularFileProperty
    }
}

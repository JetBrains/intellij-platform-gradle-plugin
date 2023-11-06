// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.extensions

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.getByName
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Extensions

@IntelliJPlatform
interface IntelliJPlatformExtension : ExtensionAware {

    /**
     * Instrument Java classes with nullability assertions and compile forms created by IntelliJ GUI Designer.
     *
     * Default value: `true`
     */
    val instrumentCode: Property<Boolean>

    val sandboxContainer: DirectoryProperty

    val pluginConfiguration
        get() = extensions.getByName<PluginConfiguration>(Extensions.PLUGIN_CONFIGURATION)

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
}

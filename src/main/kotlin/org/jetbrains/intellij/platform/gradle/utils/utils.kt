// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.GradleInternal
import org.gradle.api.plugins.PluginManager
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

val FileSystemLocation.asPath
    get() = asFile.toPath().absolute()

internal val <T : FileSystemLocation> Provider<T>.asPath
    get() = get().asFile.toPath().absolute()

internal fun ConfigurationContainer.create(name: String, description: String, configuration: Configuration.() -> Unit = {}) =
    maybeCreate(name).apply {
        isVisible = false
        isCanBeConsumed = false
        isCanBeResolved = true

        this.description = description
        configuration()
    }

internal val Configuration.asLenient
    get() = incoming.artifactView { lenient(true) }.files

internal val ALL_TASKS
    get() = Constants.Tasks::class.java.declaredFields
        .filter { it.name != "INSTANCE" }
        .map { it.get(null).toString() }
        .minus("INSTANCE")

fun <T> Property<T>.isSpecified() = isPresent && when (val value = orNull) {
    null -> false
    is String -> value.isNotEmpty()
    is RegularFile -> value.asFile.exists()
    else -> true
}

/**
 * Retrieves the [Path] of the IntelliJ Platform with [Configurations.INTELLIJ_PLATFORM_DEPENDENCY] configuration.
 *
 * @receiver The [Configuration] to retrieve the product information from.
 * @return The [Path] of the IntelliJ Platform
 * @throws GradleException
 */
@Throws(GradleException::class)
fun FileCollection.platformPath() = with(toList()) {
    val message = when (size) {
        0 -> "No IntelliJ Platform dependency found."
        1 -> null
        else -> "More than one IntelliJ Platform dependencies found."
    } ?: return@with single().toPath().absolute().resolvePlatformPath()

    throw GradleException(
        """
        $message
        Please ensure there is a single IntelliJ Platform dependency defined in your project and that the necessary repositories, where it can be located, are added.
        See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
        """.trimIndent()
    )
}

internal fun Path.resolvePlatformPath() = generateSequence(this) { parent ->
    val entry = parent
        .listDirectoryEntries()
        .singleOrNull() // pick an entry if it is a singleton in a directory
        ?.takeIf { it.isDirectory() } // and this entry is a directory
        ?: return@generateSequence null

    when {
        // eliminate `/Application Name.app/Contents/...`
        entry.name.endsWith(".app")
            -> entry.listDirectoryEntries("Contents").firstOrNull()

        // set the root to the directory containing `product-info.json`
        entry.listDirectoryEntries("product-info.json").isNotEmpty()
            -> entry

        // set the root to the directory containing `Resources/product-info.json`
        entry.listDirectoryEntries("Resources").firstOrNull()?.listDirectoryEntries("product-info.json").orEmpty().isNotEmpty()
            -> entry

        // stop when `lib/` is inside, even if it's a singleton
        entry.listDirectoryEntries(Constants.Sandbox.Plugin.LIB).isNotEmpty()
            -> null

        else
            -> null
    }
}.last()

internal fun IdePluginManager.safelyCreatePlugin(path: Path, validateDescriptor: Boolean = false) =
    createPlugin(path, validateDescriptor).runCatching {
        if (this is PluginCreationFail) {
            val details = errorsAndWarnings.joinToString(separator = "\n") { it.message }
            throw GradleException("Could not resolve plugin: '$path':\n$details")
        }

        require(this is PluginCreationSuccess)
        plugin
    }

val Project.settings
    get() = (gradle as GradleInternal).settings

val Project.rootProjectPath
    get() = rootProject.rootDir.toPath().absolute()

val Project.extensionProvider
    get() = provider { the<IntelliJPlatformExtension>() }

internal val PluginManager.isModule
    get() = hasPlugin(Plugins.MODULE) && !hasPlugin(Plugin.ID)

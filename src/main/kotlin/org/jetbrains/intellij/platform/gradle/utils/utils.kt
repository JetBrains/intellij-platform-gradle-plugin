// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import com.jetbrains.plugin.structure.intellij.problems.IntelliJPluginCreationResultResolver
import com.jetbrains.plugin.structure.intellij.problems.JetBrainsPluginCreationResultResolver
import com.jetbrains.plugin.structure.intellij.problems.PluginCreationResultResolver
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
import org.gradle.api.resources.ResourceHandler
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
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

private fun Path.deepResolve(block: Path.() -> Path?) = generateSequence(this) { parent ->
    val entry = parent
        .listDirectoryEntries()
        .singleOrNull() // pick an entry if it is a singleton in a directory
        ?.takeIf { it.isDirectory() } // and this entry is a directory
        ?: return@generateSequence null
    block(entry)
}.last()

internal fun Path.resolvePlatformPath() = deepResolve {
    when {
        // eliminate `/Application Name.app/Contents/...`
        name.endsWith(".app")
            -> this.listDirectoryEntries("Contents").firstOrNull()

        // set the root to the directory containing `product-info.json`
        listDirectoryEntries("product-info.json").isNotEmpty()
            -> this

        // set the root to the directory containing `Resources/product-info.json`
        listDirectoryEntries("Resources").firstOrNull()?.listDirectoryEntries("product-info.json").orEmpty()
            .isNotEmpty()
            -> this

        // stop when `lib/` is inside, even if it's a singleton
        listDirectoryEntries(Sandbox.Plugin.LIB).isNotEmpty()
            -> null

        else
            -> null
    }
}

internal fun Path.resolvePluginPath() = deepResolve {
    when {
        // set the root to the directory containing `lib/`
        listDirectoryEntries(Sandbox.Plugin.LIB).isNotEmpty()
            -> this

        else
            -> null
    }
}

internal fun IdePluginManager.safelyCreatePlugin(path: Path, validateDescriptor: Boolean = false, suppressPluginProblems: Boolean = true) =
    createPlugin(path, validateDescriptor, problemResolver = getProblemResolver(suppressPluginProblems)).runCatching {
        if (this is PluginCreationFail) {
            val details = errorsAndWarnings.joinToString(separator = "\n") { it.message }
            throw GradleException("Could not resolve plugin: '$path':\n$details")
        }

        require(this is PluginCreationSuccess)
        plugin
    }

private fun getProblemResolver(suppressPluginProblems: Boolean): PluginCreationResultResolver {
    return if (suppressPluginProblems) {
        JetBrainsPluginCreationResultResolver.fromClassPathJson(IntelliJPluginCreationResultResolver())
    } else {
        IntelliJPluginCreationResultResolver()
    }
}

val Project.settings
    get() = (gradle as GradleInternal).settings

val Project.rootProjectPath
    get() = rootProject.rootDir.toPath().absolute()

val Project.extensionProvider
    get() = provider { the<IntelliJPlatformExtension>() }

internal val PluginManager.isModule
    get() = hasPlugin(Plugins.MODULE) && !hasPlugin(Plugin.ID)

// TODO: migrate to `project.resources.binary` whenever it's available. Ref: https://github.com/gradle/gradle/issues/25237
internal fun ResourceHandler.resolveUrl(url: Property<String>) = text.fromUri(url).asFile("UTF-8")

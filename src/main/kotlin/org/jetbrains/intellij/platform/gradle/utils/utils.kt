// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import com.jetbrains.plugin.structure.intellij.problems.AnyProblemToWarningPluginCreationResultResolver
import com.jetbrains.plugin.structure.intellij.problems.IntelliJPluginCreationResultResolver
import com.jetbrains.plugin.structure.intellij.problems.JetBrainsPluginCreationResultResolver
import com.jetbrains.plugin.structure.intellij.problems.PluginCreationResultResolver
import com.jetbrains.plugin.structure.intellij.problems.remapping.JsonUrlProblemLevelRemappingManager
import com.jetbrains.plugin.structure.intellij.problems.remapping.RemappingSet.JETBRAINS_PLUGIN_REMAPPING_SET
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.GradleInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.PluginManager
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesHelper
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.services.RequestedIntelliJPlatform
import java.nio.file.Path
import kotlin.io.path.*

val FileSystemLocation.asPath
    get() = asFile.toPath().absolute()

internal val <T : FileSystemLocation> Provider<T>.asPath
    get() = get().asFile.toPath().absolute()

internal fun ConfigurationContainer.create(
    name: String,
    description: String,
    configuration: Configuration.() -> Unit = {},
) =
    maybeCreate(name).apply {
        isVisible = false
        isCanBeConsumed = false
        isCanBeResolved = true

        this.description = description
        configuration()
    }

internal val Configuration.asLenient
    get() = incoming.artifactView { lenient(true) }.files

fun <T : Any> Property<T>.isSpecified() = isPresent && when (val value = orNull) {
    null -> false
    is String -> value.isNotEmpty()
    is RegularFile -> value.asFile.exists()
    else -> true
}

/**
 * Retrieves the [Path] of the IntelliJ Platform with [Configurations.INTELLIJ_PLATFORM_DEPENDENCY] configuration.
 *
 * @param requestedPlatform The requested platform name.
 * @receiver The [Configuration] to retrieve the product information from.
 * @return The [Path] of the IntelliJ Platform
 * @throws GradleException
 */
@Throws(GradleException::class)
fun FileCollection.platformPath(requestedPlatform: RequestedIntelliJPlatform? = null) = with(toList()) {
    val message = when (size) {
        0 -> "No IntelliJ Platform dependency found" + requestedPlatform?.let { " with '$it'" }.orEmpty() + "."
        1 -> null
        else -> "More than one IntelliJ Platform dependencies found."
    } ?: return@with single().toPath().absolute().resolvePlatformPath()

    throw GradleException(
        """
        $message
        Please ensure there is a single IntelliJ Platform dependency defined in your project and that the necessary repositories, where it can be located, are added.
        See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
        """.trimIndent(),
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

/**
 * Shorthand for an absolute and normalized path with invariant separators.
 */
internal val Path.safePathString
    get() = absolute().normalize().invariantSeparatorsPathString

/**
 * A property extension for `String` that expands the tilde (`~`) representing the user's home directory
 * into its absolute path.
 *
 * If the string is exactly "~", it is replaced with the value of the "user.home" property.
 * If the string starts with "~/", it replaces the "~" with the "user.home" property and appends the remaining path.
 * For all other cases, the original string is returned unchanged.
 */
internal val String.expandUserHome: String
    get() = when {
        equals("~") -> System.getProperty("user.home")
        startsWith("~/") || startsWith("~\\") -> System.getProperty("user.home") + substring(1)
        else -> this
    }

/**
 * Safely creates a plugin from the given path using the `IdePluginManager`. Handles plugin creation failures
 * by throwing a `GradleException` with detailed error messages.
 *
 * @param path The path to the plugin file or directory.
 * @param validateDescriptor Indicates whether the descriptor of the plugin should be validated during the creation process.
 * @param suppressPluginProblems Indicates whether plugin problems should be suppressed, treating any issues as warnings.
 */
internal fun IdePluginManager.safelyCreatePlugin(
    path: Path,
    validateDescriptor: Boolean = false,
    suppressPluginProblems: Boolean = true,
) =
    createPlugin(path, validateDescriptor, problemResolver = getProblemResolver(suppressPluginProblems)).runCatching {
        if (this is PluginCreationFail) {
            val details = errorsAndWarnings.joinToString(separator = "\n") { it.message }
            throw GradleException("Could not resolve plugin: '$path':\n$details")
        }

        require(this is PluginCreationSuccess)
        plugin
    }

/**
 * Determines the appropriate problem resolver for plugin creation based on the suppression flag.
 *
 * @param suppressPluginProblems A boolean flag indicating whether plugin problems should be treated as warnings.
 *                               If true, all plugin problems are treated as warnings. Otherwise, problems are resolved
 *                               using specific remapping logic and the JetBrains plugin resolution strategy.
 * @return An instance of [PluginCreationResultResolver], which handles plugin creation problems resolution based on the input flag.
 */
private fun getProblemResolver(suppressPluginProblems: Boolean): PluginCreationResultResolver {
    return if (suppressPluginProblems) {
        AnyProblemToWarningPluginCreationResultResolver
    } else {
        val levelRemapping = JsonUrlProblemLevelRemappingManager
            .fromClassPathJson()
            .getLevelRemapping(JETBRAINS_PLUGIN_REMAPPING_SET)
        JetBrainsPluginCreationResultResolver(IntelliJPluginCreationResultResolver(), levelRemapping)
    }
}

val Project.settings
    get() = (gradle as GradleInternal).settings

val Project.rootProjectPath
    get() = rootProject.rootDir.toPath().absolute()

val Project.extensionProvider
    get() = provider { the<IntelliJPlatformExtension>() }

internal val Project.dependenciesHelper
    get() = IntelliJPlatformDependenciesHelper(
        configurations,
        dependencyFactory,
        layout,
        objects,
        providers,
        project.path,
        gradle,
        rootProjectPath,
        extensionProvider,
        project.settings.dependencyResolutionManagement.rulesMode,
    )

internal val PluginManager.isModule
    get() = hasPlugin(Plugins.MODULE) && !hasPlugin(Plugin.ID)

/**
 * Helper function for creating cached [ProviderFactory.provider].
 */
internal inline fun <reified T : Any> cachedProvider(
    objects: ObjectFactory,
    providers: ProviderFactory,
    crossinline value: () -> T,
) = objects
    .property<T>()
    .value(providers.provider { value() })
    .apply {
        disallowChanges()
        finalizeValueOnRead()
    }

/**
 * Helper function for creating cached list [ProviderFactory.provider].
 */
internal inline fun <reified T : Any> cachedListProvider(
    objects: ObjectFactory,
    providers: ProviderFactory,
    crossinline value: () -> List<T>,
) = objects
    .listProperty<T>()
    .value(providers.provider { value() })
    .apply {
        disallowChanges()
        finalizeValueOnRead()
    }

internal data class Quadruple<T1, T2, T3, T4>(val first: T1, val second: T2, val third: T3, val fourth: T4)

internal fun <A : Any, B : Any, C : Any, R : Any> zip(
    a: Provider<A>,
    b: Provider<B>,
    c: Provider<C>,
    combiner: (A, B, C) -> R,
): Provider<R> = a
    .zip(b) { av, bv -> av to bv }
    .zip(c) { (av, bv), cv -> combiner(av, bv, cv) }

internal fun <A : Any, B : Any, C : Any, D : Any, R : Any> zip(
    a: Provider<A>,
    b: Provider<B>,
    c: Provider<C>,
    d: Provider<D>,
    combiner: (A, B, C, D) -> R,
): Provider<R> = a
    .zip(b) { av, bv -> av to bv }
    .zip(c) { (av, bv), cv -> Triple(av, bv, cv) }
    .zip(d) { (av, bv, cv), dv -> combiner(av, bv, cv, dv) }

internal fun <A : Any, B : Any, C : Any, D : Any, E : Any, R : Any> zip(
    a: Provider<A>,
    b: Provider<B>,
    c: Provider<C>,
    d: Provider<D>,
    e: Provider<E>,
    combiner: (A, B, C, D, E) -> R,
): Provider<R> = a
    .zip(b) { av, bv -> av to bv }
    .zip(c) { (av, bv), cv -> Triple(av, bv, cv) }
    .zip(d) { (av, bv, cv), dv -> Quadruple(av, bv, cv, dv) }
    .zip(e) { (av, bv, cv, dv), ev -> combiner(av, bv, cv, dv, ev) }

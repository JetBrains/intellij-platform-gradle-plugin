// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.jetbrains.intellij.platform.gradle.artifacts.transform.collectBundledPluginsJars
import org.jetbrains.intellij.platform.gradle.artifacts.transform.collectIntelliJPlatformJars
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.models.*
import org.jetbrains.intellij.platform.gradle.resolvers.path.ModuleDescriptorsPathResolver
import org.jetbrains.intellij.platform.gradle.utils.CollectedModuleDescriptor
import org.jetbrains.intellij.platform.gradle.utils.ModuleDescriptorsParser
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Obtains the list of coordinates from module descriptors provided by the IntelliJ Platform.
 *
 * Such a list is used, i.e., to exclude transitive dependencies of the [IntelliJPlatformDependenciesExtension.testFramework] dependencies.
 */
abstract class ModuleDescriptorsValueSource : ValueSource<Set<Coordinates>, ModuleDescriptorsValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        /**
         * IntelliJ Platform location
         */
        val intellijPlatformPath: DirectoryProperty
    }

    override fun obtain(): Set<Coordinates> {
        val platformPath = parameters.intellijPlatformPath.asPath

        val moduleDescriptorsFile = ModuleDescriptorsPathResolver(platformPath)
            .runCatching { resolve() }
            .getOrNull()

        // Fallback to the hardcoded transitive dependencies list to be excluded if there's no `modules/module-descriptor.jar` file present to read from.
        if (moduleDescriptorsFile == null) {
            return fallbackExclusions + explicitExclusions
        }

        return loadModuleDescriptorCoordinates(
            moduleDescriptorsFile = moduleDescriptorsFile,
            collectedJars = collectModuleDescriptorResourcePaths(platformPath),
        ) + explicitExclusions
    }
}

private val camelCaseBoundaryRegex = Regex("([a-z])([A-Z])")
private val supportedModuleDescriptorNamespaces = setOf("jetbrains", "jps", "\$legacy_jps_module")

private fun collectModuleDescriptorResourcePaths(platformPath: Path): Set<String> {
    val productInfo = platformPath.productInfo()
    val platformJars = collectIntelliJPlatformJars(productInfo, platformPath)
    val bundledPluginJars = collectBundledPluginsJars(platformPath)
    val collectedJars = HashSet<String>(platformJars.size + bundledPluginJars.size)

    for (jar in platformJars) {
        collectedJars += platformPath.relativize(jar).invariantSeparatorsPathString
    }
    for (jar in bundledPluginJars) {
        collectedJars += platformPath.relativize(jar).invariantSeparatorsPathString
    }

    return collectedJars
}

internal fun loadModuleDescriptorCoordinates(moduleDescriptorsFile: Path, collectedJars: Set<String>): Set<Coordinates> {
    val descriptors = ModuleDescriptorsParser.load(moduleDescriptorsFile).values
    val coordinates = HashSet<Coordinates>(descriptors.size)

    for (descriptor in descriptors) {
        if (descriptor.namespace != null && descriptor.namespace !in supportedModuleDescriptorNamespaces) {
            continue
        }

        val path = descriptor.path ?: continue
        if (path !in collectedJars) {
            continue
        }

        descriptor.toCoordinatesOrNull()?.let(coordinates::add)
    }

    return coordinates
}

internal fun CollectedModuleDescriptor.toCoordinatesOrNull() = name.toCoordinatesOrNull()

internal fun ModuleDescriptor.toCoordinatesOrNull() = name.toCoordinatesOrNull()

private fun String.toCoordinatesOrNull(): Coordinates? {
    val nameParts = split('.')
    if (nameParts.size < 2) {
        return null
    }

    val artifactIdParts = nameParts
        .drop(1)
        .let {
            when (it.firstOrNull()) {
                in setOf("platform", "vcs", "cloud") -> it.drop(1)
                else -> it
            }
        }

    if (artifactIdParts.isEmpty()) {
        return null
    }

    return Coordinates(
        groupId = nameParts.take(2).joinToString(".", prefix = "com.jetbrains."),
        artifactId = artifactIdParts
            .joinToString("-")
            .replace(camelCaseBoundaryRegex, "$1-$2")
            .lowercase(),
    )
}

private val explicitExclusions = setOf(
    Coordinates("junit", "junit"),
    Coordinates("org.jetbrains", "jetCheck"),
    Coordinates("org.hamcrest", "hamcrest-core"),
    Coordinates("org.jetbrains.teamcity", "serviceMessages"),
) + kotlinStdlib + coroutines

private val fallbackExclusions = setOf(
    // TestFrameworkType.Platform
    Coordinates("com.jetbrains.intellij.java", "java-resources-en"),
    Coordinates("com.jetbrains.intellij.java", "java-rt"),
    Coordinates("com.jetbrains.intellij.platform", "boot"),
    Coordinates("com.jetbrains.intellij.platform", "code-style-impl"),
    Coordinates("com.jetbrains.intellij.platform", "core-ui"),
    Coordinates("com.jetbrains.intellij.platform", "execution-impl"),
    Coordinates("com.jetbrains.intellij.platform", "ide-impl"),
    Coordinates("com.jetbrains.intellij.platform", "ide-util-io-impl"),
    Coordinates("com.jetbrains.intellij.platform", "ide-util-io"),
    Coordinates("com.jetbrains.intellij.platform", "ide-util-netty"),
    Coordinates("com.jetbrains.intellij.platform", "images"),
    Coordinates("com.jetbrains.intellij.platform", "lang-impl"),
    Coordinates("com.jetbrains.intellij.platform", "lang"),
    Coordinates("com.jetbrains.intellij.platform", "resources"),
    Coordinates("com.jetbrains.intellij.platform", "service-container"),
    Coordinates("com.jetbrains.intellij.platform", "util-class-loader"),
    Coordinates("com.jetbrains.intellij.platform", "util-jdom"),
    Coordinates("com.jetbrains.intellij.platform", "workspace-model-jps"),
    Coordinates("com.jetbrains.intellij.platform", "workspace-model-storage"),
    Coordinates("com.jetbrains.intellij.regexp", "regexp"),
    Coordinates("com.jetbrains.intellij.xml", "xml-dom-impl"),

    // TestFrameworkType.Plugin.Java
    Coordinates("com.jetbrains.intellij.java", "java-compiler-impl"),
    Coordinates("com.jetbrains.intellij.java", "java-debugger-impl"),
    Coordinates("com.jetbrains.intellij.java", "java-execution-impl"),
    Coordinates("com.jetbrains.intellij.java", "java-execution"),
    Coordinates("com.jetbrains.intellij.java", "java-impl-refactorings"),
    Coordinates("com.jetbrains.intellij.java", "java-impl"),
    Coordinates("com.jetbrains.intellij.java", "java-plugin"),
    Coordinates("com.jetbrains.intellij.java", "java-ui"),
    Coordinates("com.jetbrains.intellij.java", "java"),
    Coordinates("com.jetbrains.intellij.platform", "external-system-impl"),
    Coordinates("com.jetbrains.intellij.platform", "jps-build"),
    Coordinates("com.jetbrains.intellij.platform", "util"),
)

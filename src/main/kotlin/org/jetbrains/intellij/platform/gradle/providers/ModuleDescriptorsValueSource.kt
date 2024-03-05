// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.jetbrains.intellij.platform.gradle.artifacts.transform.collectIntelliJPlatformJars
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.model.Coordinates
import org.jetbrains.intellij.platform.gradle.model.ModuleDescriptor
import org.jetbrains.intellij.platform.gradle.model.XmlExtractor
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.resolvers.path.ModuleDescriptorsPathResolver
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.util.jar.JarFile
import kotlin.io.path.pathString

/**
 * Obtains the list of coordinates from module descriptors provided with the IntelliJ Platform.
 *
 * Such a list is used, i.e., to exclude transitive dependencies of the [IntelliJPlatformDependenciesExtension.testFramework] dependencies.
 */
abstract class ModuleDescriptorsValueSource : ValueSource<Set<Coordinates>, ModuleDescriptorsValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        /**
         * IntelliJ Platform location
         */
        val intellijPlatformPath: DirectoryProperty

        /**
         * IntelliJ Platform cache directory
         */
        val intellijPlatformCache: DirectoryProperty
    }

    override fun obtain(): Set<Coordinates> {
        val productInfo = parameters.intellijPlatformPath.asPath.productInfo()
        val platformPath = parameters.intellijPlatformPath.asPath

        val collectedJars = collectIntelliJPlatformJars(productInfo, platformPath).map {
            platformPath.relativize(it).pathString
        }

        val resolver = ModuleDescriptorsPathResolver(platformPath, parameters.intellijPlatformCache.asPath)
        val moduleDescriptorsFile = resolver.resolve()
        val jarFile = JarFile(moduleDescriptorsFile.toFile())

        return jarFile
            .entries()
            .asSequence()
            .filter { it.name.endsWith(".xml") }
            .map { XmlExtractor<ModuleDescriptor>().unmarshal(jarFile.getInputStream(it)) }
            .map { it.key to Coordinates(it.groupId, it.artifactId) }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .filterKeys { collectedJars.contains(it) }
            .values
            .flatten()
            .toSet()
    }

    private inline val ModuleDescriptor.key
        get() = resources.resourceRoot.path.removePrefix("../")

    private inline val ModuleDescriptor.groupId
        get() = name.split('.').take(2).joinToString(".", prefix = "com.jetbrains.")

    private inline val ModuleDescriptor.artifactId: String
        get() = name
            .split('.')
            .drop(1)
            .let {
                when (it.first()) {
                    in setOf("platform", "vcs", "cloud") -> it.drop(1)
                    else -> it
                }
            }
            .joinToString("-")
            .replace(Regex("([a-z])([A-Z])"), "$1-$2")
            .lowercase()
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.jetbrains.intellij.platform.gradle.artifacts.transform.collectBundledPluginsJars
import org.jetbrains.intellij.platform.gradle.artifacts.transform.collectIntelliJPlatformJars
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.ModuleDescriptor
import org.jetbrains.intellij.platform.gradle.models.decode
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.resolvers.path.ModuleDescriptorsPathResolver
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.util.jar.JarFile
import kotlin.io.path.pathString

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
        val productInfo = parameters.intellijPlatformPath.asPath.productInfo()
        val platformPath = parameters.intellijPlatformPath.asPath

        val moduleDescriptorsFile = ModuleDescriptorsPathResolver(platformPath)
            .runCatching { resolve() }
            .getOrNull()

        // Fallback to the hardcoded transitive dependencies list to be excluded if there's no `modules/module-descriptor.jar` file present to read from.
        if (moduleDescriptorsFile == null) {
            return fallbackExclusions + explicitExclusions
        }

        val collectedJars =
            collectIntelliJPlatformJars(productInfo, platformPath)
                .plus(collectBundledPluginsJars(platformPath))
                .map { platformPath.relativize(it).pathString }

        val jarFile = JarFile(moduleDescriptorsFile.toFile())

        return jarFile
            .entries()
            .asSequence()
            .filter { it.name.endsWith(".xml") }
            .map { jarFile.getInputStream(it) }
            .mapNotNull { decode<ModuleDescriptor>(it) }
            .map { it.key to Coordinates(it.groupId, it.artifactId) }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .filterKeys { collectedJars.contains(it) }
            .values
            .flatten()
            .toSet()
            .plus(explicitExclusions)
    }

    private inline val ModuleDescriptor.key
        get() = resources?.resourceRoot?.path?.removePrefix("../")

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

private val explicitExclusions = setOf(
    Coordinates("junit", "junit"),
    Coordinates("org.jetbrains", "jetCheck"),
    Coordinates("org.hamcrest", "hamcrest-core"),
    Coordinates("org.jetbrains.teamcity", "serviceMessages"),
    Coordinates("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm"),
)

private val fallbackExclusions = setOf(
    // TestFrameworkType.Platform
    Coordinates("org.jetbrains.kotlin", "kotlin-stdlib"),
    Coordinates("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm"),
    Coordinates("org.jetbrains.kotlin", "kotlin-stdlib-jdk8"),
    Coordinates("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8"),
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

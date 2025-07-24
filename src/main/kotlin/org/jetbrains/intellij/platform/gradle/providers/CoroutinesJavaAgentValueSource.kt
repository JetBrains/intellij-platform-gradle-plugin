// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.jetbrains.intellij.platform.gradle.artifacts.transform.collectIntelliJPlatformJars
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.utils.platformPath
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * A Gradle [ValueSource] that creates a Kotlin Coroutines Java Agent JAR file for debugging purposes.
 *
 * This value source dynamically creates a minimal JAR file containing only the manifest needed
 * to enable the Kotlin Coroutines debugging agent. The agent allows for enhanced debugging
 * capabilities when working with coroutines in IntelliJ Platform-based applications.
 *
 * The implementation:
 * 1. Scans the IntelliJ Platform JARs to find the appropriate coroutines agent class
 * 2. Creates a JAR file with a manifest that specifies the found agent as the Premain-Class
 * 3. Returns the created JAR file for use as a `-javaagent` parameter
 *
 * @see ValueSource
 */
abstract class CoroutinesJavaAgentValueSource : ValueSource<File, CoroutinesJavaAgentValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {

        /**
         * The IntelliJ Platform configuration containing the platform JARs to scan for the agent class.
         */
        val intelliJPlatformConfiguration: ConfigurableFileCollection

        /**
         * The target directory where the generated agent JAR file will be placed.
         */
        val targetDirectory: DirectoryProperty
    }

    /**
     * Mapping of JAR filenames to their corresponding fully qualified class names.
     * The implementation will attempt to find one of these classes in the platform JARs
     * and use it as the Java agent premain class.
     */
    private val fqns = mapOf(
        "coroutines-javaagent-legacy.jar" to "kotlinx.coroutines.debug.AgentPremain",
        "coroutines-javaagent.jar" to "kotlinx.coroutines.debug.internal.AgentPremain",
    )

    /**
     * Creates and returns a Kotlin Coroutines Java Agent JAR file.
     *
     * This method performs the following steps:
     * 1. Extracts the platform path from the IntelliJ Platform configuration
     * 2. Loads the product information to determine available JARs
     * 3. Scans the platform JARs to find the coroutines debug agent class
     * 4. Creates a JAR file with a manifest specifying the found agent as the Premain-Class
     * 5. Returns the path to the created JAR file
     *
     * @return A [File] pointing to the created coroutines Java agent JAR
     * @throws RuntimeException if no suitable agent class is found in the platform JARs
     */
    override fun obtain(): File {
        val platformPath = parameters.intelliJPlatformConfiguration.platformPath()
        val productInfo = platformPath.productInfo()
        val urls = collectIntelliJPlatformJars(productInfo, platformPath).map { it.toUri().toURL() }.toTypedArray()
        val fqn = URLClassLoader(urls).use {
            fqns.values.firstNotNullOfOrNull { fqn -> it.runCatching { loadClass(fqn) }.getOrNull() }
        }?.name

        val manifest = Manifest(
            """
            Manifest-Version: 1.0
            Premain-Class: $fqn
            Can-Retransform-Classes: true
            Multi-Release: true
            
            """.trimIndent().byteInputStream(),
        )

        val targetFile = parameters.targetDirectory.map {
            val filename = fqns.entries.first { entry -> entry.value == fqn }.key
            it.file(filename).asFile
        }.get()

        JarOutputStream(targetFile.outputStream(), manifest).close()

        return targetFile
    }
}

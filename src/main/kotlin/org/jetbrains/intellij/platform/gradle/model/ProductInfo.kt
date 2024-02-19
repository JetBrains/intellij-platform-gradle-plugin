// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Constraints
import org.jetbrains.intellij.platform.gradle.pathResolver.ProductInfoResolver
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Represents information about the IntelliJ Platform product.
 * The information is retrieved from the `product-info.json` file in the IntelliJ Platform directory.
 *
 * @property name The product's name, like "IntelliJ IDEA".
 * @property version The marketing version of the product, like "2023.2".
 * @property versionSuffix The suffix of the version, like "EAP".
 * @property buildNumber The build number of the product, like "232.8660.185".
 * @property productCode The product code, like "IU".
 * @property dataDirectoryName The directory name of the product data.
 * @property svgIconPath The path to the SVG icon of the product.
 * @property productVendor The vendor of the product.
 * @property launch The list of OS- and arch-specific launch configurations for the product.
 * @property customProperties The list of custom properties of the product.
 * @property bundledPlugins The list of bundled plugins provided with the current release.
 * @property fileExtensions The list of file extensions associated with the product.
 * @property modules The list of modules of the product.
 */
@Serializable
data class ProductInfo(
    val name: String? = null,
    val version: String = "",
    val versionSuffix: String? = null,
    val buildNumber: String = "",
    val productCode: String = "",
    val dataDirectoryName: String? = null,
    val svgIconPath: String? = null,
    val productVendor: String? = null,
    val launch: List<Launch> = mutableListOf(),
    val customProperties: List<CustomProperty> = mutableListOf(),
    val bundledPlugins: List<String> = mutableListOf(),
    val fileExtensions: List<String> = mutableListOf(),
    val modules: List<String> = mutableListOf(),
) {

    /**
     * Represents a launch configuration for a product.
     *
     * @property os The target operating system for the launch.
     * @property arch The architecture of the target system.
     * @property launcherPath The path to the OS-specific launcher executable.
     * @property javaExecutablePath The path to the Java executable.
     * @property vmOptionsFilePath The path to the file containing VM options.
     * @property startupWmClass The startup window class (WM_CLASS) for the application.
     * @property bootClassPathJarNames The names of the JAR files to be included in the boot classpath.
     * @property additionalJvmArguments Additional JVM arguments.
     */
    @Serializable
    data class Launch(
        val os: OS? = null,
        val arch: String? = null,
        val launcherPath: String? = null,
        val javaExecutablePath: String? = null,
        val vmOptionsFilePath: String? = null,
        val startupWmClass: String? = null,
        val bootClassPathJarNames: List<String> = mutableListOf(),
        val additionalJvmArguments: List<String> = mutableListOf(),
    ) {

        /**
         * Represents the different operating systems.
         */
        @Suppress("EnumEntryName")
        enum class OS {
            Linux, Windows, macOS
        }
    }

    /**
     * Represents a custom property with a key-value pair.
     *
     * @property key The key of the custom property.
     * @property value The value of the custom property.
     */
    @Serializable
    data class CustomProperty(
        val key: String? = null,
        val value: String? = null,
    )
}

/**
 * Validates that the resolved IntelliJ Platform is supported by checking against the minimal supported IntelliJ Platform version.
 *
 * If the provided version is lower, a [IllegalArgumentException] is thrown with an appropriate message.
 *
 * @throws IllegalArgumentException if the provided version is lower than the minimum supported version.
 * @see Constraints.MINIMAL_INTELLIJ_PLATFORM_VERSION
 * @see Constraints.MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER
 */
@Throws(IllegalArgumentException::class)
fun ProductInfo.validateSupportedVersion() {
    if (buildNumber.toVersion() < Constraints.MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER) {
        throw IllegalArgumentException("The minimal supported IDE version is ${Constraints.MINIMAL_INTELLIJ_PLATFORM_VERSION} (${Constraints.MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER}), the provided version is too low: $version ($buildNumber)")
    }
}

/**
 * Finds the [ProductInfo.Launch] object for the given architecture and OS.
 *
 * @param architecture The architecture of the target system.
 * @param os The operating system of the target system. By default, it refers to [OperatingSystem.current].
 * @return The launch configuration for the specified architecture.
 * @throws GradleException If the specified architecture is not supported.
 * @throws GradleException If the launch information for the current OS and architecture is not found.
 */
internal fun ProductInfo.launchFor(architecture: String, os: OperatingSystem = OperatingSystem.current()): ProductInfo.Launch = with(os) {
    val availableArchitectures = launch.mapNotNull { it.arch }.toSet()
    val arch = with(availableArchitectures) {
        when {
            isEmpty() -> null // older SDKs or Maven releases don't provide architecture information, null is used in such a case
            contains(architecture) -> architecture
            contains("amd64") && architecture == "x86_64" -> "amd64"
            else -> throw GradleException("Unsupported JVM architecture for running Gradle tasks: $architecture. Supported architectures: ${joinToString()}")
        }
    }

    launch.find {
        when {
            isLinux -> ProductInfo.Launch.OS.Linux
            isWindows -> ProductInfo.Launch.OS.Windows
            isMacOsX -> ProductInfo.Launch.OS.macOS
            else -> ProductInfo.Launch.OS.Linux
        } == it.os && arch == it.arch
    } ?: throw GradleException("Could not find launch information for the current OS: $name ($arch)")
}.run {
    copy(additionalJvmArguments = additionalJvmArguments.map { it.trim('"') })
}

/**
 * JSON object used for parsing and serializing JSON data.
 * The `ignoreUnknownKeys` property is set to `true` to ignore unknown keys during deserialization.
 */
private val json = Json { ignoreUnknownKeys = true }

/**
 * Retrieves the [ProductInfo] for the IntelliJ Platform from the root directory.
 *
 * @receiver The [Path] representing the IntelliJ Platform root directory.
 * @return The [ProductInfo] object containing the product information.
 */
fun Path.productInfo() = json.decodeFromString<ProductInfo>(
    ProductInfoResolver(this).resolve().readText()
)

/**
 * Retrieves the [ProductInfo] for the IntelliJ Platform with [Configurations.INTELLIJ_PLATFORM] configuration.
 *
 * @receiver The [Configuration] to retrieve the product information from.
 * @return The [ProductInfo] object containing the product information.
 */
fun FileCollection.productInfo() = platformPath().productInfo()

/**
 * Retrieves the [Path] of the IntelliJ Platform with [Configurations.INTELLIJ_PLATFORM] configuration.
 *
 * @receiver The [Configuration] to retrieve the product information from.
 * @return The [Path] of the IntelliJ Platform
 */
fun FileCollection.platformPath() = runCatching {
    single().toPath()
}.onFailure {
    throw GradleException("""
        The dependency on the IntelliJ Platform couldn't be resolved. 
        Please ensure that this dependency is defined in your project and that the necessary repositories, where it can be located, are added.
        See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    """.trimIndent())
}.getOrThrow()

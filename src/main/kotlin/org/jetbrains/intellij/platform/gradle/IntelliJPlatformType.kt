// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.intellij.platform.gradle.models.Coordinates

// TODO any changes must be synchronized with
// IntelliJPlatformDependenciesExtension
// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-types.html#IntelliJPlatformType
// IJ:
// - org.jetbrains.idea.devkit.projectRoots.IntelliJPlatformProduct
// - community/plugins/devkit/intellij.devkit/resources/META-INF/plugin.xml

/**
 * Describes all IntelliJ Platform types available to be used for plugin development, dependency resolution, and plugin verification.
 *
 * Each entry is composed of a product code name and coordinates used for dependency and binary release resolution.
 */
enum class IntelliJPlatformType(
    val code: String,
    val maven: Coordinates?,
    val installer: Coordinates?,
) {
    AndroidStudio(
        code = "AI",
        maven = null,
        installer = Coordinates("com.google.android.studio", "android-studio"),
    ),
    @Deprecated("Aqua (QA) is no longer available as a target IntelliJ Platform")
    Aqua(
        code = "QA",
        maven = null,
        installer = Coordinates("aqua", "aqua"),
    ),
    CLion(
        code = "CL",
        maven = Coordinates("com.jetbrains.intellij.clion", "clion"),
        installer = Coordinates("cpp", "CLion"),
    ),
    DataGrip(
        code = "DB",
        maven = null,
        installer = Coordinates("datagrip", "datagrip"),
    ),
    DataSpell(
        code = "DS",
        maven = null,
        installer = Coordinates("python", "dataspell"),
    ),
    FleetBackend(
        code = "FLIJ",
        maven = Coordinates("com.jetbrains.intellij.fleetBackend", "fleetBackend"),
        installer = null,
    ),
    Gateway(
        code = "GW",
        maven = Coordinates("com.jetbrains.intellij.gateway", "gateway"),
        installer = Coordinates("idea/gateway", "JetBrainsGateway"),
    ),
    GoLand(
        code = "GO",
        maven = Coordinates("com.jetbrains.intellij.goland", "goland"),
        installer = Coordinates("go", "goland"),
    ),
    IntellijIdeaCommunity(
        code = "IC",
        maven = Coordinates("com.jetbrains.intellij.idea", "ideaIC"),
        installer = Coordinates("idea", "ideaIC"),
    ),
    IntellijIdeaUltimate(
        code = "IU",
        maven = Coordinates("com.jetbrains.intellij.idea", "ideaIU"),
        installer = Coordinates("idea", "ideaIU"),
    ),
    JetBrainsClient(
        code = "JBC",
        maven = null,
        installer = Coordinates("idea/code-with-me", "JetBrainsClient"),

        // https://download.jetbrains.com/idea/ideaIC-2025.1.1.1.tar.gz
        // https://download.jetbrains.com/idea/code-with-me/JetBrainsClient-251.25410.129.tar.gz

        // https://download.jetbrains.com/idea/code-with-me/JetBrainsClient-251.25410.129.aarch64.dmg
    ),
    MPS(
        code = "MPS",
        maven = null,
        installer = Coordinates("mps", "MPS"),
    ),
    PhpStorm(
        code = "PS",
        maven = Coordinates("com.jetbrains.intellij.phpstorm", "phpstorm"),
        installer = Coordinates("webide", "PhpStorm"),
    ),
    PyCharmProfessional(
        code = "PY",
        maven = Coordinates("com.jetbrains.intellij.pycharm", "pycharmPY"),
        installer = Coordinates("python", "pycharm-professional"),
    ),
    PyCharmCommunity(
        code = "PC",
        maven = Coordinates("com.jetbrains.intellij.pycharm", "pycharmPC"),
        installer = Coordinates("python", "pycharm-community"),
    ),
    Rider(
        code = "RD",
        maven = Coordinates("com.jetbrains.intellij.rider", "riderRD"),
        installer = Coordinates("rider", "JetBrains.Rider"),
    ),
    RubyMine(
        code = "RM",
        maven = null,
        installer = Coordinates("ruby", "RubyMine"),
    ),
    RustRover(
        code = "RR",
        maven = Coordinates("com.jetbrains.intellij.rustrover", "RustRover"),
        installer = Coordinates("rustrover", "RustRover"),
    ),
    WebStorm(
        code = "WS",
        maven = Coordinates("com.jetbrains.intellij.webstorm", "webstorm"),
        installer = Coordinates("webstorm", "WebStorm"),
    ),
    @Deprecated("Writerside (WRS) is no longer available as a target IntelliJ Platform")
    Writerside(
        code = "WRS",
        maven = Coordinates("com.jetbrains.intellij.idea", "writerside"),
        installer = Coordinates("writerside", "writerside"),
    ),
    ;

    companion object {
        private val map = values().associateBy(IntelliJPlatformType::code)

        /**
         * @throws IllegalArgumentException
         */
        @Throws(IllegalArgumentException::class)
        fun fromCode(code: String) = requireNotNull(map[code]) {
            "Specified type '$code' is unknown. Supported values: ${values().joinToString()}"
        }
    }

    override fun toString() = code
}

/**
 * Maps the value held by this [Provider] to an [IntelliJPlatformType].
 *
 * This extension function transforms a [Provider] containing a value that can be converted
 * to an [IntelliJPlatformType] into a [Provider] of [IntelliJPlatformType].

 * @receiver A [Provider] containing a value that can be converted to [IntelliJPlatformType]
 * @return A [Provider] of [IntelliJPlatformType] for the given provider value
 * @throws IllegalArgumentException if the provider value cannot be converted to [IntelliJPlatformType]
 */
@Throws(IllegalArgumentException::class)
fun Provider<*>.toIntelliJPlatformType() = map { it.toIntelliJPlatformType() }

/**
 * Maps the value to an [IntelliJPlatformType].
 *
 * This extension function transforms [Any] value that can be converted into [IntelliJPlatformType].
 *
 * @receiver [Any] value that can be converted to [IntelliJPlatformType]
 * @return [IntelliJPlatformType] instance
 * @throws IllegalArgumentException if the value cannot be converted to [IntelliJPlatformType]
 */
@Throws(IllegalArgumentException::class)
fun Any.toIntelliJPlatformType(): IntelliJPlatformType = when (this) {
    is IntelliJPlatformType -> this
    is String -> IntelliJPlatformType.fromCode(this)
    else -> throw IllegalArgumentException("Invalid argument type: '$javaClass'. Supported types: String or ${IntelliJPlatformType::class.java}")
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

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
    val binary: Coordinates?,
) {
    AndroidStudio(
        code = "AI",
        maven = null,
        binary = Coordinates("com.google.android.studio", "android-studio"),
    ),
    Aqua(
        code = "QA",
        maven = null,
        binary = Coordinates("aqua", "aqua"),
    ),
    CLion(
        code = "CL",
        maven = Coordinates("com.jetbrains.intellij.clion", "clion"),
        binary = Coordinates("cpp", "CLion"),
    ),
    DataGrip(
        code = "DB",
        maven = null,
        binary = Coordinates("datagrip", "datagrip"),
    ),
    DataSpell(
        code = "DS",
        maven = null,
        binary = Coordinates("python", "dataspell"),
    ),
    FleetBackend(
        code = "FLIJ",
        maven = Coordinates("com.jetbrains.intellij.fleetBackend", "fleetBackend"),
        binary = null,
    ),
    Gateway(
        code = "GW",
        maven = Coordinates("com.jetbrains.intellij.gateway", "gateway"),
        binary = Coordinates("idea/gateway", "JetBrainsGateway"),
    ),
    GoLand(
        code = "GO",
        maven = Coordinates("com.jetbrains.intellij.goland", "goland"),
        binary = Coordinates("go", "goland"),
    ),
    IntellijIdeaCommunity(
        code = "IC",
        maven = Coordinates("com.jetbrains.intellij.idea", "ideaIC"),
        binary = Coordinates("idea", "ideaIC"),
    ),
    IntellijIdeaUltimate(
        code = "IU",
        maven = Coordinates("com.jetbrains.intellij.idea", "ideaIU"),
        binary = Coordinates("idea", "ideaIU"),
    ),
    MPS(
        code = "MPS",
        maven = null,
        binary = Coordinates("mps", "MPS"),
    ),
    PhpStorm(
        code = "PS",
        maven = Coordinates("com.jetbrains.intellij.phpstorm", "phpstorm"),
        binary = Coordinates("webide", "PhpStorm"),
    ),
    PyCharmProfessional(
        code = "PY",
        maven = Coordinates("com.jetbrains.intellij.pycharm", "pycharmPY"),
        binary = Coordinates("python", "pycharm-professional"),
    ),
    PyCharmCommunity(
        code = "PC",
        maven = Coordinates("com.jetbrains.intellij.pycharm", "pycharmPC"),
        binary = Coordinates("python", "pycharm-community"),
    ),
    Rider(
        code = "RD",
        maven = Coordinates("com.jetbrains.intellij.rider", "riderRD"),
        binary = Coordinates("rider", "JetBrains.Rider"),
    ),
    RubyMine(
        code = "RM",
        maven = null,
        binary = Coordinates("ruby", "RubyMine"),
    ),
    RustRover(
        code = "RR",
        maven = Coordinates("com.jetbrains.intellij.rustrover", "RustRover"),
        binary = Coordinates("rustrover", "RustRover"),
    ),
    WebStorm(
        code = "WS",
        maven = Coordinates("com.jetbrains.intellij.webstorm", "webstorm"),
        binary = Coordinates("webstorm", "WebStorm"),
    ),
    Writerside(
        code = "WRS",
        maven = Coordinates("com.jetbrains.intellij.idea", "writerside"),
        binary = Coordinates("writerside", "writerside"),
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
 * @throws IllegalArgumentException
 */
@Throws(IllegalArgumentException::class)
internal fun Any.toIntelliJPlatformType() = when (this) {
    is IntelliJPlatformType -> this
    is String -> IntelliJPlatformType.fromCode(this)
    else -> throw IllegalArgumentException("Invalid argument type: '$javaClass'. Supported types: String or ${IntelliJPlatformType::class.java}")
}

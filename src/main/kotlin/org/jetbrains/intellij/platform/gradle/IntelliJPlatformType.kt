// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.models.Coordinates

/**
 * Describes all IntelliJ Platform types available to be used for plugin development, dependency resolution, and plugin verification.
 *
 * Each entry is composed of a product code name and coordinates used for dependency and binary release resolution.
 */
enum class IntelliJPlatformType(
    val code: String,
    val dependency: Coordinates?,
    val binary: Coordinates?,
) {
    AndroidStudio(
        code = "AI",
        dependency = Coordinates("com.google.android.studio", "android-studio"),
        binary = Coordinates("com.google.android.studio", "android-studio"),
    ),
    Aqua(
        code = "QA",
        dependency = null,
        binary = Coordinates("aqua", "aqua"),
    ),
    CLion(
        code = "CL",
        dependency = Coordinates("com.jetbrains.intellij.clion", "clion"),
        binary = Coordinates("cpp", "CLion"),
    ),
    DataGrip(
        code = "DB",
        dependency = null,
        binary = Coordinates("datagrip", "datagrip"),
    ),
    DataSpell(
        code = "DS",
        dependency = null,
        binary = Coordinates("python", "dataspell"),
    ),
    Fleet(
        code = "FLIJ",
        dependency = Coordinates("com.jetbrains.intellij.fleetBackend", "fleetBackend"),
        binary = null,
    ),
    Gateway(
        code = "GW",
        dependency = Coordinates("com.jetbrains.intellij.gateway", "gateway"),
        binary = Coordinates("idea/gateway", "JetBrainsGateway"),
    ),
    GoLand(
        code = "GO",
        dependency = Coordinates("com.jetbrains.intellij.goland", "goland"),
        binary = Coordinates("go", "goland"),
    ),
    IntellijIdeaCommunity(
        code = "IC",
        dependency = Coordinates("com.jetbrains.intellij.idea", "ideaIC"),
        binary = Coordinates("idea", "ideaIC"),
    ),
    IntellijIdeaUltimate(
        code = "IU",
        dependency = Coordinates("com.jetbrains.intellij.idea", "ideaIU"),
        binary = Coordinates("idea", "ideaIU"),
    ),

    //    JPS(
    //        code = "JPS",
    //        groupId = "",
    //        artifactId = "",
    //    ),
    PhpStorm(
        code = "PS",
        dependency = Coordinates("com.jetbrains.intellij.phpstorm", "phpstorm"),
        binary = Coordinates("webide", "PhpStorm"),
    ),
    PyCharmProfessional(
        code = "PY",
        dependency = Coordinates("com.jetbrains.intellij.pycharm", "pycharmPY"),
        binary = Coordinates("python", "pycharm-professional"),
    ),
    PyCharmCommunity(
        code = "PC",
        dependency = Coordinates("com.jetbrains.intellij.pycharm", "pycharmPC"),
        binary = Coordinates("python", "pycharm-community"),
    ),
    Rider(
        code = "RD",
        dependency = Coordinates("com.jetbrains.intellij.rider", "riderRD"),
        binary = Coordinates("rider", "JetBrains.Rider"),
    ),
    RubyMine(
        code = "RM",
        dependency = null,
        binary = Coordinates("ruby", "RubyMine"),
    ),
    RustRover(
        code = "RR",
        dependency = Coordinates("com.jetbrains.intellij.rustrover", "RustRover"),
        binary = null,
    ),
    WebStorm(
        code = "WS",
        dependency = Coordinates("com.jetbrains.intellij.webstorm", "webstorm"),
        binary = Coordinates("webstorm", "WebStorm"),
    ),
    Writerside(
        code = "WRS",
        dependency = Coordinates("com.jetbrains.intellij.idea", "writerside"),
        binary = null,
    ),
    ;

    companion object {
        private val map = values().associateBy(IntelliJPlatformType::code)

        @Throws(GradleException::class)
        fun fromCode(code: String) = map[code]
            ?: throw GradleException("Specified type '$code' is unknown. Supported values: ${values().joinToString()}")
    }

    override fun toString() = code
}

internal fun Any.toIntelliJPlatformType() = when (this) {
    is IntelliJPlatformType -> this
    is String -> IntelliJPlatformType.fromCode(this)
    else -> throw IllegalArgumentException("Invalid argument type: '$javaClass'. Supported types: String or ${IntelliJPlatformType::class.java}")
}

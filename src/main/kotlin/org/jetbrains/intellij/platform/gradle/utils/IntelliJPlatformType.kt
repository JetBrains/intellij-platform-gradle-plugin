// Copyright 2023-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import org.gradle.api.GradleException

enum class IntelliJPlatformType(
    val code: String,
    val dependency: Coordinates,
    val binary: Coordinates?,
    val unavailable: Boolean = false,
) {
    AndroidStudio(
        code = "AI",
        dependency = Coordinates("com.google.android.studio", "android-studio"),
        binary = Coordinates("com.google.android.studio", "android-studio"),
    ),
    CLion(
        code = "CL",
        dependency = Coordinates("com.jetbrains.intellij.clion", "clion"),
        binary = Coordinates("cpp", "CLion"),
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
    RustRover(
        code = "RR",
        dependency = Coordinates("com.jetbrains.intellij.rustrover", "RustRover"),
        binary = null,
    ),
    Writerside(
        code = "WRS",
        dependency = Coordinates("com.jetbrains.intellij.idea", "writerside"),
        binary = null,
        unavailable = true,
    ),
    ;

    companion object {
        private val map = values().associateBy(IntelliJPlatformType::code)

        @Throws(GradleException::class)
        fun fromCode(code: String) = map[code]
            ?: throw GradleException("Specified type '$code' is unknown. Supported values: ${values().joinToString()}")
    }

    override fun toString() = code

    data class Coordinates(val group: String, val name: String)
}

internal fun String.toIntelliJPlatformType() = IntelliJPlatformType.fromCode(this)

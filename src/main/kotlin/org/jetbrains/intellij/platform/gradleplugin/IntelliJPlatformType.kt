// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin

import org.gradle.api.GradleException

enum class IntelliJPlatformType(val code: String, val groupId: String, val artifactId: String) {
    AndroidStudio(
        code = "AI",
        groupId = "com.google.android.studio",
        artifactId = "android-studio"
    ),
    CLion(
        code = "CL",
        groupId = "com.jetbrains.intellij.clion",
        artifactId = "clion"
    ),
    Fleet(
        code = "FL",
        groupId = "com.jetbrains.intellij.fleetBackend",
        artifactId = "fleetBackend"
    ),
    Gateway(
        code = "GW",
        groupId = "com.jetbrains.gateway",
        artifactId = "JetBrainsGateway"
    ),
    GoLand(
        code = "GO",
        groupId = "com.jetbrains.intellij.goland",
        artifactId = "goland"
    ),
    IntellijIdeaCommunity(
        code = "IC",
        groupId = "com.jetbrains.intellij.idea",
        artifactId = "ideaIC"
    ),
    IntellijIdeaUltimate(
        code = "IU",
        groupId = "com.jetbrains.intellij.idea",
        artifactId = "ideaIU"
    ),
    JPS(
        code = "JPS",
        groupId = "",
        artifactId = ""
    ),
    PhpStorm(
        code = "PS",
        groupId = "com.jetbrains.intellij.phpstorm",
        artifactId = "phpstorm"
    ),
    PyCharmProfessional(
        code = "PY",
        groupId = "com.jetbrains.intellij.pycharm",
        artifactId = "pycharmPY"
    ),
    PyCharmCommunity(
        code = "PC",
        groupId = "com.jetbrains.intellij.pycharm",
        artifactId = "pycharmPC"
    ),
    Rider(
        code = "RD",
        groupId = "com.jetbrains.intellij.rider",
        artifactId = "riderRD"
    );

    companion object {
        private val map = values().associateBy(IntelliJPlatformType::code)

        fun fromCode(code: String) = map[code]
            ?: throw GradleException("Specified type '$code' is unknown. Supported values: ${values().joinToString()}")
    }

    override fun toString() = code
}

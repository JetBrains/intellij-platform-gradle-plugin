// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin

import org.gradle.api.GradleException

enum class IntelliJPlatformType(val code: String) {
    AndroidStudio("AI"),
    CLion("CL"),
    Gateway("GW"),
    GoLand("GO"),
    IntellijIdeaCommunity("IC"),
    IntellijIdeaUltimate("IU"),
    JPS("JPS"),
    PhpStorm("PS"),
    PyCharmProfessional("PY"),
    PyCharmCommunity("PC"),
    Rider("RD");

    companion object {
        private val map = values().associateBy(IntelliJPlatformType::code)

        fun fromCode(code: String) = map[code]
            ?: throw GradleException("Specified type '$code' is unknown. Supported values: ${values().joinToString()}")
    }

    override fun toString() = code
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.Version

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
    IntellijIdea(
        code = "IU",
        maven = Coordinates("com.jetbrains.intellij.idea", "idea"),
        installer = Coordinates("idea", "idea"),
    ),
    JetBrainsClient(
        code = "JBC",
        maven = null,
        installer = Coordinates("idea/code-with-me", "JetBrainsClient"),
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
    PyCharm(
        code = "PY",
        maven = Coordinates("com.jetbrains.intellij.pycharm", "pycharm"),
        installer = Coordinates("python", "pycharm"),
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
        maven = Coordinates("com.jetbrains.intellij.rubymine", "rubymine"),
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

    internal val log = Logger(javaClass)


    companion object {
        private val map = values().associateBy(IntelliJPlatformType::code)

        /**
         * Note: this method returns [IntellijIdea] for `IU` value, ignoring the [IntellijIdeaUltimate] type.
         *
         * @param code the product code name
         * @return the IntelliJ Platform type for the specified code name
         * @throws IllegalArgumentException
         */
        @Throws(IllegalArgumentException::class)
        fun fromCode(code: String) = requireNotNull(map[code]) {
            "Specified type '$code' is unknown. Supported values: ${values().joinToString()}"
        }

        /**
         * Note: this method returns [IntellijIdea] for `IU` value, ignoring the [IntellijIdeaUltimate] type.
         *
         * @param code the product code name
         * @param version the version string
         * @return the IntelliJ Platform type for the specified code name.
         * @throws IllegalArgumentException
         */
        @Throws(IllegalArgumentException::class)
        fun fromCode(code: String, version: String) = fromCode(code).run {
            when {
                /**
                 * By default, IU is parsed to [IntellijIdea].
                 * Based on the [version], we set it back to [IntellijIdeaUltimate] if it's below
                 * [Constraints.UNIFIED_INTELLIJ_IDEA_BUILD_NUMBER] or [Constraints.UNIFIED_INTELLIJ_IDEA_VERSION].
                 */
                this == IntellijIdea -> {
                    with(Version.parse(version)) {
                        when {
                            isBuildNumber() && this < Constraints.UNIFIED_INTELLIJ_IDEA_BUILD_NUMBER -> IntellijIdeaUltimate
                            !isBuildNumber() && this < Constraints.UNIFIED_INTELLIJ_IDEA_VERSION -> IntellijIdeaUltimate
                            else -> IntellijIdea
                        }
                    }
                }

                this == PyCharm -> {
                    with(Version.parse(version)) {
                        when {
                            isBuildNumber() && this < Constraints.UNIFIED_PYCHARM_BUILD_NUMBER -> PyCharmProfessional
                            !isBuildNumber() && this < Constraints.UNIFIED_PYCHARM_VERSION -> PyCharmProfessional
                            else -> PyCharm
                        }
                    }
                }

                else -> this
            }
        }
    }

    override fun toString() = code
}

/**
 * Maps the value to an [IntelliJPlatformType].
 *
 * This extension function transforms [Any] value that can be converted into [IntelliJPlatformType].
 * Also validates that the type-version combination is valid.
 *
 * @receiver [Any] value that can be converted to [IntelliJPlatformType]
 * @param version The version string to help determine the correct type
 * @return [IntelliJPlatformType] instance
 * @throws IllegalArgumentException if the value cannot be converted to [IntelliJPlatformType] or if the combination is invalid
 */
@Throws(IllegalArgumentException::class)
fun Any.toIntelliJPlatformType(version: String) = when (this) {
    is IntelliJPlatformType -> this
    is String -> IntelliJPlatformType.fromCode(this, version)
    else -> throw IllegalArgumentException("Invalid argument type: '$javaClass'. Supported types: String or ${IntelliJPlatformType::class.java}")
}

/**
 * Validates that the given version is compatible with this IntelliJ Platform type.
 * For IntelliJ IDEA Community (IC), versions 2025.3+ are not available.
 *
 * @receiver The [IntelliJPlatformType] to validate
 * @param version The version string to validate
 * @return The same [IntelliJPlatformType] if valid
 * @throws IllegalArgumentException if the version is not compatible with this type
 */
@Throws(IllegalArgumentException::class)
fun IntelliJPlatformType.validateVersion(version: String) = also {
    fun test(buildConstraint: Version, versionConstraint: Version, message: String) = with(Version.parse(version)) {
        this >= when {
            isBuildNumber() -> buildConstraint
            else -> versionConstraint
        }
    }.let { matches ->
        if (matches) {
            throw IllegalArgumentException(message)
        }
    }

    when (this) {
        IntelliJPlatformType.IntellijIdeaCommunity -> test(
            Constraints.UNIFIED_INTELLIJ_IDEA_BUILD_NUMBER,
            Constraints.UNIFIED_INTELLIJ_IDEA_VERSION,
            "IntelliJ IDEA Community (IC) is no longer published since ${Constraints.UNIFIED_INTELLIJ_IDEA_VERSION} (${Constraints.UNIFIED_INTELLIJ_IDEA_BUILD_NUMBER}), use: intellijIdea(\"$version\")",
        )

        IntelliJPlatformType.PyCharmCommunity -> test(
            Constraints.UNIFIED_PYCHARM_BUILD_NUMBER,
            Constraints.UNIFIED_PYCHARM_VERSION,
            "PyCharm Community (PC) is no longer published since ${Constraints.UNIFIED_PYCHARM_VERSION} (${Constraints.UNIFIED_PYCHARM_BUILD_NUMBER}), use: pycharm(\"$version\")",
        )

        else -> {}
    }
}

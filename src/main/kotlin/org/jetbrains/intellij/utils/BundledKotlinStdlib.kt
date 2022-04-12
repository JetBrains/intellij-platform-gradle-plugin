package org.jetbrains.intellij.utils

import org.jetbrains.intellij.Version

private val bundledStdlibApiMap = mapOf(
    "2022.1" to "1.6.20",
    "2021.3" to "1.5.10",
    "2021.2" to "1.5.10",
    "2021.1" to "1.4.32",
    "2020.3" to "1.4.0",
    "2020.2" to "1.3.70",
    "2020.1" to "1.3.70",
    "2019.3" to "1.3.31",
    "2019.2" to "1.3.3",
    "2019.1" to "1.3.11",
)

fun getBundledKotlinStdlib(ideVersion: String): String? {
    val version = ideVersion.let(Version.Companion::parse)
    val key = bundledStdlibApiMap.keys
        .map(Version.Companion::parse)
        .filter { it <= version }
        .maxOf { it }.version
    return bundledStdlibApiMap[key]
}

fun getClosestKotlinStdlib(kotlinVersion: String): String {
    val version = kotlinVersion.let(Version.Companion::parse)
    return bundledStdlibApiMap.values
        .map(Version.Companion::parse)
        .filter { it <= version }
        .maxOf { it }.version
}

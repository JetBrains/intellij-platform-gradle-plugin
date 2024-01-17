// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

class Version(
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
    val version: String = "",
) : Comparable<Version> {

    private inline fun Int.or(other: () -> Int) = takeIf { this != 0 } ?: other()

    override fun compareTo(other: Version) =
        (major - other.major)
            .or { minor - other.minor }
            .or { patch - other.patch }
            .or {
                when {
                    version.contains('-') && !other.version.contains('-') -> -1
                    !version.contains('-') && other.version.contains('-') -> 1
                    else -> version.compareTo(other.version, ignoreCase = true)
                }
            }
            .or { version.compareTo(other.version, ignoreCase = true) }

    override fun toString() = version.takeIf(String::isNotEmpty) ?: "$major.$minor.$patch"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Version

        if (major != other.major) return false
        if (minor != other.minor) return false
        if (patch != other.patch) return false
        if (!toString().equals(other.toString(), ignoreCase = true)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = major
        result = 31 * result + minor
        result = 31 * result + patch
        result = 31 * result + version.hashCode()
        return result
    }

    companion object {
        fun parse(versionString: String) =
            versionString.split(' ', '.', '-', '"', '_')
                .map(String::toIntOrNull)
                .dropWhile { it == null }
                .takeWhile { it != null }
                .filterNotNull()
                .let { it + List(3) { 0 } }
                .let { (major, minor, patch) -> Version(major, minor, patch, versionString) }
    }
}

internal fun String.toVersion() = Version.parse(this)

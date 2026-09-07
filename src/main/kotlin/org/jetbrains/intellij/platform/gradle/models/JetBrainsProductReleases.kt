// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.Serializable

@Serializable
data class JetBrainsProductReleases(
    val code: String,
    val releases: List<Release> = mutableListOf(),
) {

    @Serializable
    data class Release(
        val type: String,
        val version: String,
        val build: String,
    ) {
        var downloads: Map<String, Download> = emptyMap()

        @Serializable
        data class Download(
            val link: String,
            val size: Long? = null,
            val checksumLink: String? = null,
        )
    }
}

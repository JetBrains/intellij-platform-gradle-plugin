// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AndroidStudioReleases(
    val content: Content,
) {
    val version
        get() = content.version

    val items
        get() = content.items

    @Serializable
    data class Content(
        @SerialName("item") val items: List<Item>,
        val version: Int,
    )

    @Serializable
    data class Item(
        val name: String,
        val build: String,
        val version: String,
        val channel: String,
        val platformBuild: String,
        val platformVersion: String,
        val date: String,
        @SerialName("download") val downloads: List<Download>,
    ) {

        @Serializable
        data class Download(
            val link: String,
            val size: String,
            val checksum: String,
        )
    }
}

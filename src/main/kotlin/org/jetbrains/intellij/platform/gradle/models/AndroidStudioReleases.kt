// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class AndroidStudioReleases(
    val version: Int,
    @XmlSerialName("item") val items: List<Item>,
) {

    @Serializable
    data class Item(
        @XmlElement val name: String,
        @XmlElement val build: String,
        @XmlElement val version: String,
        @XmlElement val channel: String,
        @XmlElement val platformBuild: String,
        @XmlElement val platformVersion: String,
        @XmlElement val date: String,
        @XmlSerialName("download") val downloads: List<Download>,
    ) {

        @Serializable
        data class Download(
            @XmlElement val link: String,
            @XmlElement val size: String,
            @XmlElement val checksum: String,
        )
    }
}

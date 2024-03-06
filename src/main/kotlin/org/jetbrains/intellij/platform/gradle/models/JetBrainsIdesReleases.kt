// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class JetBrainsIdesReleases(
    @XmlSerialName("product") val products: List<Product>,
) {

    @Serializable
    data class Product(
        val name: String,
        @XmlSerialName("code") val codes: List<String>,
        @XmlSerialName("channel") val channels: List<Channel>,
    ) {

        @Serializable
        data class Channel(
            val id: String,
            val name: String,
            val status: String,
            val url: String,
            val feedback: String,
            val majorVersion: Int,
            val licensing: String,
            @XmlSerialName("build") val builds: List<Build>,
        ) {

            @Serializable
            data class Build(
                val number: String,
                val version: String,
                val releaseDate: String = "",
                val fullNumber: String = "",
                @XmlElement val message: String = "",
                @XmlElement @XmlSerialName("blogPost") val blogPost: BlogPost?,
                @XmlSerialName("button") val buttons: List<Button>,
                @XmlSerialName("patch") val patches: List<Patch>,
            ) {

                @Serializable
                data class Button(
                    val name: String,
                    val url: String,
                    val download: Boolean = false,
                )

                @Serializable
                data class Patch(
                    val from: String,
                    val size: String,
                    val fullFrom: String,
                )

                @Serializable
                data class BlogPost(
                    val url: String,
                )
            }
        }
    }
}

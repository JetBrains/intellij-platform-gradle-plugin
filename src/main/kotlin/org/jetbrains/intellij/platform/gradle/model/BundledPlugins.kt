// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.api.GradleException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText

@Serializable
data class BundledPlugins(
    val plugins: List<BundledPlugin> = mutableListOf(),
)

@Serializable
data class BundledPlugin(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val path: String = "",
    val dependencies: List<String> = mutableListOf(),
)

internal fun Path.resolveBundledPluginsPath(name: String = "bundled-plugins.json") =
    listOf(this, resolve(name), resolve("Resources").resolve(name))
        .find { it.name == name && it.exists() }
        ?: throw GradleException("Could not resolve $name file in: $this")

private val json = Json { ignoreUnknownKeys = true }
fun Path.bundledPlugins() = json.decodeFromString<BundledPlugins>(readText())

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.net.URL
import java.util.jar.Manifest

/**
 * Obtains the current IntelliJ Platform Gradle Plugin version.
 */
abstract class CurrentPluginVersionValueSource : ValueSource<String, ValueSourceParameters.None> {

    override fun obtain() = javaClass
        .run { getResource("${simpleName.substringBefore('$')}.class") }
        .runCatching {
            val manifestPath = with(this?.path) {
                when {
                    this == null -> return@runCatching null
                    startsWith("jar:") -> this
                    startsWith("file:") -> "jar:$this"
                    else -> return@runCatching null
                }
            }.run { substring(0, lastIndexOf("!") + 1) } + "/META-INF/MANIFEST.MF"
            URL(manifestPath).openStream().use {
                Manifest(it).mainAttributes.getValue("Version")
            }
        }.getOrNull() ?: "0.0.0"
}

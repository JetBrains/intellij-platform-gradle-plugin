// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import com.jetbrains.plugin.structure.intellij.extractor.PluginBeanExtractor
import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.PluginInstantiationException
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.util.GradleVersion
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.MINIMAL_SUPPORTED_GRADLE_VERSION
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_NAME
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.inputStream

fun <T> T?.or(other: T): T = this ?: other

fun <T> T?.or(block: () -> T): T = this ?: block()

fun <T> T?.ifNull(block: () -> Unit): T? = this ?: block().let { null }

fun <T> T?.throwIfNull(block: () -> Exception) = this ?: throw block()

internal val FileSystemLocation.asPath
    get() = asFile.toPath().absolute()

internal val <T : FileSystemLocation> Provider<T>.asFile
    get() = get().asFile

internal val <T : FileSystemLocation> Provider<T>.asPath
    get() = get().asFile.toPath().absolute()

internal fun checkGradleVersion() {
    if (GradleVersion.current() < GradleVersion.version(MINIMAL_SUPPORTED_GRADLE_VERSION)) {
        throw PluginInstantiationException("$PLUGIN_NAME requires Gradle $MINIMAL_SUPPORTED_GRADLE_VERSION and higher")
    }
}

fun <T> Property<T>.isSpecified() = isPresent && when (val value = orNull) {
    null -> false
    is String -> value.isNotEmpty()
    is RegularFile -> value.asFile.exists()
    else -> true
}

internal fun parsePluginXml(pluginXml: Path) = runCatching {
    pluginXml.inputStream().use {
        val document = JDOMUtil.loadDocument(it)
        PluginBeanExtractor.extractPluginBean(document)
    }
}.getOrNull()

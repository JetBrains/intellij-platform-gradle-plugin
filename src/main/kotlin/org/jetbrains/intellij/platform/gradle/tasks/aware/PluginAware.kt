// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import com.jetbrains.plugin.structure.intellij.beans.PluginBean
import com.jetbrains.plugin.structure.intellij.extractor.PluginBeanExtractor
import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.intellij.platform.gradle.utils.asPath
import kotlin.io.path.inputStream

/**
 * This interface provides information about the currently built plugin.
 * It resolves and parses the final `plugin.xml` descriptor file, making its details easily accessible.
 */
interface PluginAware {

    /**
     * Holds the path to the patched `plugin.xml` file.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    val pluginXml: RegularFileProperty
}

/**
 * Parses the `plugin.xml` file and provides access to the [PluginBean] object through the [block].
 */
fun <T : Any> RegularFile.parse(block: PluginBean.() -> T) = asPath.inputStream().use {
    val document = JDOMUtil.loadDocument(it)
    PluginBeanExtractor.extractPluginBean(document)
}.block()

/**
 * Parses the `plugin.xml` file and provides access to the [PluginBean] object through the [block].
 */
fun <T : Any> Provider<RegularFile>.parse(block: PluginBean.() -> T) = map { it.parse(block) }

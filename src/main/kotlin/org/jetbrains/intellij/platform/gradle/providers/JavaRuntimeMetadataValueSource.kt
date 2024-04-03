// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.launchFor
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.io.path.pathString

/**
 * Obtains the architecture of the provided Java Runtime executable by requesting the list of its internal properties.
 *
 * It is used to properly pick the [ProductInfo.Launch] when calling the [ProductInfo.launchFor] helper method.
 */
abstract class JavaRuntimeMetadataValueSource : ValueSource<Map<String, String>, JavaRuntimeMetadataValueSource.Parameters> {

    @get:Inject
    abstract val execOperations: ExecOperations

    interface Parameters : ValueSourceParameters {
        /**
         * Java Runtime executable.
         */
        val executable: RegularFileProperty
    }

    override fun obtain() = ByteArrayOutputStream().use { os ->
        execOperations.exec {
            commandLine(
                parameters.executable.asPath.pathString,
                "-XshowSettings:properties",
                "-version",
            )
            errorOutput = os
        }

        val separator = " = "
        os.toString()
            .lines()
            .dropWhile { !it.contains(separator) }
            .dropLastWhile { !it.contains(separator) }
            .joinToString("\n")
            .trimIndent()
            .replace("\n +".toRegex(), ",")
            .lines()
            .filter { it.contains(separator) }
            .associate { it.split(separator).let { (key, value) -> key to value } }
    }
}

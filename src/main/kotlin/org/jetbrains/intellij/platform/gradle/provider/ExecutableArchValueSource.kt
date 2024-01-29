// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.provider

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.io.path.pathString

abstract class ExecutableArchValueSource : ValueSource<String, ExecutableArchValueSource.Parameters> {

    @get:Inject
    abstract val execOperations: ExecOperations

    interface Parameters : ValueSourceParameters {
        val executable: RegularFileProperty
    }

    override fun obtain() = ByteArrayOutputStream().use { os ->
        execOperations.exec {
            commandLine(
                parameters.executable.get().asPath.pathString,
                "-XshowSettings:properties",
                "-version",
            )
            errorOutput = os
        }

        os.toString().lines()
            .find { it.trim().startsWith("os.arch") }
            ?.substringAfter(" = ")
    }
}

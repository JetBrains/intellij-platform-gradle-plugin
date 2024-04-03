// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.jetbrains.intellij.platform.gradle.resolvers.path.resolveJavaRuntimeExecutable
import org.jetbrains.intellij.platform.gradle.utils.asPath
import kotlin.io.path.pathString

/**
 * Prepares the mocked [JavaLauncher] to inject it into [Test] and [JavaExec] tasks, so they could run using a custom JVM.
 */
abstract class JavaLauncherValueSource : ValueSource<JavaLauncher, JavaLauncherValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        /**
         * Java Runtime directory.
         */
        val runtimeDirectory: DirectoryProperty

        /**
         * Java Runtime metadata map.
         */
        val runtimeMetadata: MapProperty<String, String>
    }

    override fun obtain(): JavaLauncher {
        val dir = parameters.runtimeDirectory.get()
        val file = dir.file(dir.asPath.resolveJavaRuntimeExecutable().pathString)
        val metadata = parameters.runtimeMetadata.get()

        return object : JavaLauncher {
            override fun getMetadata() = object : JavaInstallationMetadata {
                override fun getLanguageVersion() = JavaLanguageVersion.of(metadata["java.specification.version"].orEmpty())

                override fun getJavaRuntimeVersion() = metadata["java.runtime.version"].orEmpty()

                override fun getJvmVersion() = metadata["java.version"].orEmpty()

                override fun getVendor() = metadata["java.vendor"].orEmpty()

                override fun getInstallationPath() = dir

                override fun isCurrentJvm() = false
            }

            override fun getExecutablePath() = file
        }
    }
}

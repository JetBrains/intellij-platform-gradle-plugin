// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories

/**
 * Resolves the path to the IntelliJ Plugin Verifier executable.
 *
 * @property intellijPluginVerifier The [Configurations.INTELLIJ_PLUGIN_VERIFIER] configuration.
 * @property localPath The local path to the IntelliJ Plugin Verifier file.
 */
class ModuleDescriptorsPathResolver(
    private val platformPath: Path,
    private val cacheDirectory: Path,
) : PathResolver(
    subject = "Module Descriptors",
) {

    override val predictions: Sequence<Pair<String, () -> Path?>>
        get() = sequenceOf(
            /**
             * Checks if there is `modules/module-descriptors.jar` file bundled within the current IntelliJ Platform.
             */
            "$subject bundled within the IntelliJ Platform" to {
                platformPath
                    .resolve("modules/module-descriptors.jar")
                    .takeIfExists()
            },
            /**
             * Resolves the `module-descriptors.jar` file from the IntelliJ Platform cache.
             */
            "$subject available in the IntelliJ Platform cache" to {
                cacheDirectory
                    .resolve("module-descriptors.jar")
                    .takeIfExists()
            },
            /**
             * Copies the `module-descriptors.jar` file from resources into the IntelliJ Platform cache and passes its path.
             */
            "$subject copied from resources into the IntelliJ Platform cache" to {
                cacheDirectory
                    .createDirectories()
                    .resolve("module-descriptors.jar")
                    .also { jar ->
                        javaClass.classLoader
                            .getResource("module-descriptors.jar")
                            ?.openStream()
                            ?.let { Files.copy(it, jar, StandardCopyOption.REPLACE_EXISTING) }
                    }
                    .takeIfExists()
            }
        )
}

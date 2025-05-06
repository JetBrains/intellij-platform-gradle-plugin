// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import com.jetbrains.plugin.structure.ide.Ide
import com.jetbrains.plugin.structure.ide.createIde
import com.jetbrains.plugin.structure.ide.layout.MissingLayoutFileMode.SKIP_SILENTLY
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.pathString

/**
 * Abstract service class for managing IntelliJ Platform IDE instances configured within Gradle builds.
 */
abstract class IdesManagerService : BuildService<BuildServiceParameters.None> {

    private val log = Logger(javaClass)

    private val ides = ConcurrentHashMap<String, Ide>()

    fun resolve(platformPath: Path) = ides.computeIfAbsent(platformPath.pathString) {
        log.info("Creating new IDE instance from: $platformPath")
        createIde {
            missingLayoutFileMode = SKIP_SILENTLY
            path = platformPath
        }
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

abstract class ModuleDescriptorCoordinatesService : BuildService<BuildServiceParameters.None> {

    private val coordinates = ConcurrentHashMap<String, Set<Coordinates>>()

    fun resolve(platformPath: Path, loader: () -> Set<Coordinates>) =
        coordinates.computeIfAbsent(platformPath.safePathString) {
            loader()
        }
}

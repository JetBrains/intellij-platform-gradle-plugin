// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Input
import org.jetbrains.intellij.platform.gradle.artifacts.repositories.PluginArtifactRepository
import org.jetbrains.intellij.platform.gradle.shim.PluginArtifactoryShim
import org.jetbrains.intellij.platform.gradle.shim.PluginArtifactoryShim.ShimServer
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

abstract class ShimManagerService : BuildService<ShimManagerService.Parameters> {

    interface Parameters : BuildServiceParameters {

        @get:Input
        val port: Property<Int>
    }

    private val shims = ConcurrentHashMap<URI, ShimServer>()

    fun start(repository: PluginArtifactRepository) =
        shims.computeIfAbsent(repository.url) {
            PluginArtifactoryShim(repository, parameters.port.get()).start()
        }

    fun stop(url: URI) = shims[url]?.close()
}

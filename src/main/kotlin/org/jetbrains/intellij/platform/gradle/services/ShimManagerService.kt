// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Input
import org.jetbrains.intellij.platform.gradle.artifacts.repositories.BaseArtifactRepository
import org.jetbrains.intellij.platform.gradle.artifacts.repositories.PluginArtifactRepository
import org.jetbrains.intellij.platform.gradle.shim.PluginArtifactoryShim
import org.jetbrains.intellij.platform.gradle.shim.Shim
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

abstract class ShimManagerService : BuildService<ShimManagerService.Parameters>, AutoCloseable {

    private val log = Logger(javaClass)

    interface Parameters : BuildServiceParameters {

        @get:Input
        val port: Property<Int>
    }

    private val shims = ConcurrentHashMap<String, Shim.Server>()

    fun start(repository: BaseArtifactRepository): Shim.Server {
        return shims.computeIfAbsent(repository.url.toString()) {
            val port = parameters.port.get()
            log.info("Creating new shim server for ${repository.url} (port: ${port})")

            when (repository) {
                is PluginArtifactRepository -> PluginArtifactoryShim(repository, port)
                else -> throw GradleException("Unsupported repository type: ${repository.javaClass.simpleName}")
            }.start()
        }
    }

    fun stop(url: URI) {
        shims.remove(url.toString())
            ?.also { log.info("Stopping shim server for $url") }
            ?.close()
    }

    override fun close() {
        shims.keys.forEach {
            shims.remove(it)?.close()
        }
    }
}

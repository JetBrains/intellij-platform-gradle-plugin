// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.flow

import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.jetbrains.intellij.platform.gradle.services.ShimManagerService
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.net.URI

@Suppress("UnstableApiUsage")
class StopShimServerAction : FlowAction<StopShimServerAction.Parameters> {

    interface Parameters : FlowParameters {

        @get:ServiceReference
        val service: Property<ShimManagerService>

        @get:Input
        val buildResult: Property<Boolean>

        @get:Input
        val url: Property<URI>
    }

    private val log = Logger(javaClass)

    override fun execute(parameters: Parameters) {
        log.info("StopShimServerAction run with buildResult: ${parameters.buildResult.get()}")
        parameters.service.get().stop(parameters.url.get())
    }
}

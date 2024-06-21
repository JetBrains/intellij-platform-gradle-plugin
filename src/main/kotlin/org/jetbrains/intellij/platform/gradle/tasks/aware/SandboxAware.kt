// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension

/**
 * The interface provides quick access to the sandbox container and specific directories located within it.
 * The path to the sandbox container is obtained using the [IntelliJPlatformExtension.sandboxContainer] extension property and the type and version
 * of the IntelliJ Platform applied to the project.
 *
 * @see IntelliJPlatformExtension.sandboxContainer
 */
interface SandboxAware : SandboxStructure {

    @get:Internal
    val sandboxProducer: Property<String>
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension

/**
 * The interface provides the path to the Marketplace ZIP Signer executable.
 * It is required to have a dependency on the Marketplace ZIP Signer added to the project with [IntelliJPlatformDependenciesExtension.zipSigner].
 *
 * @see IntelliJPlatformDependenciesExtension.zipSigner
 */
interface SigningAware {

    /**
     * Path to the Marketplace ZIP Signer executable.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    val zipSignerExecutable: RegularFileProperty
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.intellij.platform.gradle.resolvers.path.RuntimeResolver

/**
 * This interface provides access to the Java Runtime (i.e., JetBrains Runtime) resolved with [RuntimeResolver].
 *
 * @see RuntimeResolver
 */
interface RuntimeAware : IntelliJPlatformVersionAware {

    /**
     * Java Runtime parent directory.
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val runtimeDirectory: DirectoryProperty

    /**
     * Path to the Java Runtime executable.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val runtimeExecutable: RegularFileProperty

    /**
     * An architecture of the Java Runtime currently used for running Gradle.
     */
    @get:Internal
    val runtimeArch: Property<String>
}

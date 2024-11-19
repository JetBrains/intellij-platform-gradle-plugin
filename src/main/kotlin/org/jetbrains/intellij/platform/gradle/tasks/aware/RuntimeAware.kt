// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaLauncher
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.resolvers.path.JavaRuntimePathResolver

/**
 * This interface provides access to the Java Runtime (i.e., JetBrains Runtime) resolved with [JavaRuntimePathResolver].
 *
 * @see JavaRuntimePathResolver
 */
interface RuntimeAware : IntelliJPlatformVersionAware {

    /**
     * Holds the [Configurations.JETBRAINS_RUNTIME] configuration with the JetBrains Runtime dependency added.
     * It should not be directly accessed.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val jetbrainsRuntimeConfiguration: ConfigurableFileCollection

    /**
     * Java Runtime parent directory.
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val runtimeDirectory: DirectoryProperty

    /**
     * An architecture of the Java Runtime currently used for running Gradle.
     */
    @get:Internal
    val runtimeArchitecture: Property<String>

    /**
     * Metadata object of the Java Runtime currently used for running Gradle.
     */
    @get:Internal
    val runtimeMetadata: MapProperty<String, String>

    /**
     * A custom [JavaLauncher] instance configured with the resolved [runtimeDirectory].
     */
    @get:Internal
    val runtimeLauncher: Property<JavaLauncher>
}

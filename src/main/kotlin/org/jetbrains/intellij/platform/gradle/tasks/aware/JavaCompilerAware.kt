// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.jetbrains.intellij.platform.gradle.tasks.InstrumentCodeTask

/**
 * Provides the dependency on Java Compiler used by Ant tasks.
 * This dependency is required, i.e., for [InstrumentCodeTask] to properly configure Ant tasks provided by the IntelliJ Platform.
 */
interface JavaCompilerAware {

    /**
     * Java Compiler configuration.
     */
    @get:Classpath
    val javaCompilerConfiguration: ConfigurableFileCollection
}

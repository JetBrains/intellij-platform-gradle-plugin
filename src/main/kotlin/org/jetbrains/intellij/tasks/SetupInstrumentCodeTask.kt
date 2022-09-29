// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.createDir
import com.jetbrains.plugin.structure.base.utils.deleteQuietly
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class SetupInstrumentCodeTask : DefaultTask() {

    @get:Input
    abstract val instrumentationEnabled: Property<Boolean>

    @get:Internal
    abstract val instrumentedDir: DirectoryProperty

    @TaskAction
    fun setupInstrumentCode() {
        instrumentedDir.get().asFile.toPath().run {
            if (!instrumentationEnabled.get()) {
                deleteQuietly()
            }
            createDir()
        }
    }
}

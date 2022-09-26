// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.createDir
import com.jetbrains.plugin.structure.base.utils.deleteQuietly
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

open class SetupInstrumentCodeTask @Inject constructor(
    objectFactory: ObjectFactory,
) : DefaultTask() {

    @get:Input
    val instrumentationEnabled = objectFactory.property<Boolean>()

    @get:Internal
    val instrumentedDir = objectFactory.directoryProperty()

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

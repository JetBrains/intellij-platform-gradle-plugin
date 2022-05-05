// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.internal.ConventionTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.dependency.IdeaDependency
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class SetupDependenciesTask @Inject constructor(
    objectFactory: ObjectFactory,
) : ConventionTask() {

    @Internal
    val idea: Property<IdeaDependency> = objectFactory.property(IdeaDependency::class.java)

    @TaskAction
    fun setupDependencies() {
    }
}

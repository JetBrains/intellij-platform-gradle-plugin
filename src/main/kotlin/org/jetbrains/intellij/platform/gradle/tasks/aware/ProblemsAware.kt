// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import javax.inject.Inject
import org.gradle.api.problems.Problems as GradleProblems

/**
 * Provides access to Gradle's Problems API for structured issue reporting.
 *
 * Implement this interface in Gradle tasks or services that need to report
 * problems via the incubating Problems API.
 */
@Suppress("UnstableApiUsage")
interface ProblemsAware {

    @get:Inject
    val problems: GradleProblems
}
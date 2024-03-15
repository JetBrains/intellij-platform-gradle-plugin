// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import java.nio.file.Path

/**
 * Resolves the path to the `module-descriptors.jar` file used to exclude the transitive dependencies of IntelliJ Platform dependencies,
 * such as Test Framework.
 *
 * @property platformPath The path to the currently used IntelliJ Platform.
 */
class ModuleDescriptorsPathResolver(private val platformPath: Path) : PathResolver() {

    override val subject = "Module Descriptors"

    override val subjectInput = platformPath

    override val predictions = sequenceOf(
        /**
         * Checks if there is `modules/module-descriptors.jar` file bundled within the current IntelliJ Platform.
         */
        "$subject bundled within the IntelliJ Platform" to {
            platformPath
                .resolve("modules/module-descriptors.jar")
                .takeIfExists()
        },
    )
}

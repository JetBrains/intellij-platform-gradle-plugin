// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

class ClassPathShorteningTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "class-path-shortening",
    useCache = false
) {
    @Test
    fun `build with shortened class path should succeed`() {
        build(Tasks.RUN_IDE,
            // The `idea.reset.classpath.from.manifest` property was introduced in version 2025.3
            projectProperties = defaultProjectProperties + mapOf("intellijPlatform.version" to "2025.3"),
            // Force path shortening by overriding the OS-defined command line limit
            systemProperties = mapOf("org.gradle.internal.cmdline.max.length" to 100),
            args = listOf("--info")
        ) {
            output containsText "Shortening Java classpath"
        }
    }
}

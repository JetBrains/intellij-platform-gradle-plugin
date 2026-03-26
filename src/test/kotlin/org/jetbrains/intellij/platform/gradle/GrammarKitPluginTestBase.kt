// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import kotlin.test.BeforeTest

abstract class GrammarKitPluginTestBase : IntelliJPluginTestBase() {

    @BeforeTest
    override fun setup() {
        super.setup()

        buildFile write //language=kotlin
                """
                apply(plugin = "org.jetbrains.intellij.platform.grammarkit")
                """.trimIndent()
    }

    protected fun adjustPath(value: String) = value.replace("\\", "/")
}

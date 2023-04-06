// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import kotlin.test.Test

class ThrowingExceptionsIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "throwing-exceptions",
) {

    @Test
    fun `test throwing exceptions`() {
        buildAndFail("buildPlugin").let {
            it.output containsText "FAILURE: Build failed with an exception."
            it.output containsText " > Both 'intellij.localPath' and 'intellij.version' are specified, but one of these is allowed to be present."
            it.output containsText "BUILD FAILED"
        }
    }
}

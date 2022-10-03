// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import org.gradle.api.component.SoftwareComponent

internal class IntelliJPluginLibrary : SoftwareComponent {

    override fun getName() = "intellij-plugin"
}

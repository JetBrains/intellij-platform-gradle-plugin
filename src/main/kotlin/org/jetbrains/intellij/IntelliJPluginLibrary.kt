package org.jetbrains.intellij

import org.gradle.api.component.SoftwareComponent

@Suppress("UnstableApiUsage")
class IntelliJPluginLibrary : SoftwareComponent {

    override fun getName() = "intellij-plugin"
}

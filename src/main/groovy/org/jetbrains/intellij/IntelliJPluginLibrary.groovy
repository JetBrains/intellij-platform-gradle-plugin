package org.jetbrains.intellij

import org.gradle.api.component.SoftwareComponent


class IntelliJPluginLibrary implements SoftwareComponent {
    @Override
    String getName() {
        return 'intellij-plugin'
    }
}

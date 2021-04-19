package org.jetbrains.intellij

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.dependency.IdeaDependency

class IntelliJPluginExtensionGr extends IntelliJPluginExtension {

    IntelliJPluginExtensionGr(@NotNull ObjectFactory objects) {
        super(objects)
    }

    private Project project

    def setExtensionProject(@NotNull Project project) {
        this.project = project
    }

    String getBuildVersion() {
        return IdeVersion.createIdeVersion(getIdeaDependency().buildNumber).asStringWithoutProductCode()
    }
}

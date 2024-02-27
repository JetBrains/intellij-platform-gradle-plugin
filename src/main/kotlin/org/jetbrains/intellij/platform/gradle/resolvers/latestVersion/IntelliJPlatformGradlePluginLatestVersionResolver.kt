// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.latestVersion

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Locations

class IntelliJPlatformGradlePluginLatestVersionResolver : LatestVersionResolver(
    subject = IntelliJPluginConstants.PLUGIN_NAME,
    url = Locations.GITHUB_REPOSITORY,
) {

    // TODO: use when 2.0 published to GPP
    //  latestFromMaven(
    //      "IntelliJ Platform Gradle Plugin",
    //      "${Locations.MAVEN_GRADLE_PLUGIN_PORTAL_REPOSITORY}/org/jetbrains/intellij/platform/intellij-platform-gradle-plugin/maven-metadata.xml",
    //  )
    override fun resolve() = fromGitHub()
}

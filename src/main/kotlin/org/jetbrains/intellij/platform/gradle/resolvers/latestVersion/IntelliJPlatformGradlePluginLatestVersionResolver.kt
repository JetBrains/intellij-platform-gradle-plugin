// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.latestVersion

import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import java.net.URL

class IntelliJPlatformGradlePluginLatestVersionResolver : LatestVersionResolver(
    url = URL(Locations.GITHUB_REPOSITORY),
) {

    override val subject = Constants.PLUGIN_NAME

    // TODO: use when 2.0 published to GPP
    //  latestFromMaven(
    //      "IntelliJ Platform Gradle Plugin",
    //      "${Locations.MAVEN_GRADLE_PLUGIN_PORTAL_REPOSITORY}/org/jetbrains/intellij/platform/intellij-platform-gradle-plugin/maven-metadata.xml",
    //  )
    override fun resolve() = fromGitHub()
}

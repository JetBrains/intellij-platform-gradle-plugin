// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.latestVersion

import org.jetbrains.intellij.platform.gradle.Constants.Locations

class RobotServerPluginLatestVersionResolver : LatestVersionResolver(
    subject = "Robot Server Plugin",
    url = "${Locations.INTELLIJ_DEPENDENCIES_REPOSITORY}/com/intellij/remoterobot/robot-server-plugin/maven-metadata.xml",
) {

    override fun resolve() = fromMaven()
}

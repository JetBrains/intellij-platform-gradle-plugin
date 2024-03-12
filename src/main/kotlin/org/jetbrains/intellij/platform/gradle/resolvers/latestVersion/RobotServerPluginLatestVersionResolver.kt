// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.latestVersion

import org.jetbrains.intellij.platform.gradle.Constants.Locations
import java.net.URL

class RobotServerPluginLatestVersionResolver : LatestVersionResolver(
    url = URL("${Locations.INTELLIJ_DEPENDENCIES_REPOSITORY}/com/intellij/remoterobot/robot-server-plugin/maven-metadata.xml"),
) {

    override val subject = "Robot Server Plugin"

    override fun resolve() = fromMaven()
}

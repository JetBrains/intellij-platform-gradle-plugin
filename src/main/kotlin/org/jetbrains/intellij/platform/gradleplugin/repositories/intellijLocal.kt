// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused")

package org.jetbrains.intellij.platform.gradleplugin.repositories

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import java.net.URI

fun RepositoryHandler.intellijLocal(): ArtifactRepository =
    ivy {
        // URL of the Ivy repository


        val ivyPath = "/Users/hsz/Projects/JetBrains/intellij-plugin-template/.gradle/intellijPlatform/ivy"

        url = URI("file://$ivyPath")
        ivyPattern("$ivyPath/[module]-[revision].[ext]")


        val idePath =
            "/Users/hsz/Applications/IntelliJ IDEA Ultimate.app/Contents"

        artifactPattern("$idePath/[artifact].[ext]")
    }

//        ivy {
//            val ivyDirectory = gradleIntelliJPlatform.resolve("ivy")
//
////            url = ivyDirectory.toUri()
////            ivyPattern("$ivyDirectory/[module]-[revision]-ivy.[ext]") // ivy xml
//            ivyPattern("/Users/hsz/.gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/2022.3.3/19e52733ac61e1d2e675720f92daf5959355cb1e/ideaIC-2022.3.3/ideaIC-2022.3.3-2-withKotlin-withSources.xml")
//            artifactPattern("/Users/hsz/.gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/2022.3.3/19e52733ac61e1d2e675720f92daf5959355cb1e/ideaIC-2022.3.3/[artifact].[ext]")
////            artifactPattern("/Users/hsz/Applications/IntelliJ IDEA Ultimate.app/Contents/lib/app.jar")
//        }


//ivy {
//    url = dependency.classes.toURI()
//    ivyPattern("${ivyFile.parent}/[module]-[revision].[ext]") // ivy xml
//    artifactPattern("${dependency.classes.path}/[artifact].[ext]") // idea libs
//    if (dependency.sources != null) {
//        artifactPattern("${dependency.sources.parent}/[artifact]-[revision]-[classifier].[ext]")
//    }
//}

//ivy {
//    val ivyFileSuffix = plugin.getFqn().substring("${plugin.id}-${plugin.version}".length)
//    ivyPattern("$cacheDirectoryPath/[organisation]/[module]-[revision]$ivyFileSuffix.[ext]") // ivy xml
//    ideaDependency.classes.let {
//        artifactPattern("$it/plugins/[module]/[artifact](.[ext])") // builtin plugins
//        artifactPattern("$it/[artifact](.[ext])") // plugin sources delivered with IDE
//    }
//    artifactPattern("$cacheDirectoryPath(/[classifier])/[module]-[revision]/[artifact](.[ext])") // external zip plugins
//    if (ideaDependency.sources != null) {
//        artifactPattern("${ideaDependency.sources.parent}/[artifact]-${ideaDependency.version}(-[classifier]).[ext]")
//    }
//}

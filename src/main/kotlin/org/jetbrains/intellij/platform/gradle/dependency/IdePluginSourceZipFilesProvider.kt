// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.dependency

import java.nio.file.Path
import kotlin.io.path.name

object IdePluginSourceZipFilesProvider {

    private val pluginIdToSourceZipFileName = mapOf(
        "com.intellij.css" to "src_css-api.zip",
        "com.intellij.database" to "src_database-openapi.zip",
        "com.intellij.java" to "src_jam-openapi.zip",
        "com.intellij.javaee" to "src_javaee-openapi.zip",
        "com.intellij.persistence" to "src_persistence-openapi.zip",
        "com.intellij.spring" to "src_spring-openapi.zip",
        "com.intellij.spring.boot" to "src_spring-boot-openapi.zip",
        "Tomcat" to "src_tomcat.zip"
    )

    /**
     * Provides source ZIP file for a given `platformPluginId` in the IDE directory specified by the `ideaDependency`.
     */
    fun getSourceZips(sourceZipFiles: Collection<Path>, platformPluginId: String?): Path? {
        val sourceZipFileName = pluginIdToSourceZipFileName[platformPluginId]
        return sourceZipFiles.firstOrNull { it.name == sourceZipFileName }
    }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import java.io.File

internal object IdePluginSourceZipFilesProvider {

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
    fun getSourceZips(ideaDependency: IdeaDependency, platformPluginId: String?): File? {
        val sourceZipFileName = pluginIdToSourceZipFileName[platformPluginId]
        return ideaDependency.sourceZipFiles.firstOrNull { it.name == sourceZipFileName }
    }

}

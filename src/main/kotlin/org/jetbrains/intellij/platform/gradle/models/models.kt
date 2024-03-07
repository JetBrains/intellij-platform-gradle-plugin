// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.StringFormat
import kotlinx.serialization.decodeFromString
import org.jdom2.Document
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.jetbrains.intellij.platform.gradle.Constants.Locations.GITHUB_REPOSITORY
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.io.InputStream
import java.io.StringWriter
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal fun transformXml(document: Document, path: Path) {
    val xmlOutput = XMLOutputter()
    xmlOutput.format.apply {
        indent = "  "
        omitDeclaration = true
        textMode = Format.TextMode.TRIM
    }

    StringWriter().use {
        xmlOutput.output(document, it)
        path.writeText(it.toString())
    }
}

internal inline fun <reified T> StringFormat.decode(url: URL) = decode<T>(url.openStream())

internal inline fun <reified T> StringFormat.decode(inputStream: InputStream) = decode<T>(inputStream.bufferedReader().use { it.readText() })

internal inline fun <reified T> StringFormat.decode(path: Path) = decode<T>(path.readText())

internal inline fun <reified T> StringFormat.decode(input: String) =
    runCatching { decodeFromString<T>(input) }
        .onFailure { exception ->
            Logger(T::class.java).error(
                """
                Cannot parse the provided XML input as ${T::class.java.name}.
                Please file an issue attaching the XML content and exception message to: $GITHUB_REPOSITORY/issues/new
                
                ## Model:
                ```
                ${T::class.java.name}
                ```
                
                ## Content:
                ```
                ${input.replaceIndent("                ").trimStart()}
                ```
                
                ## Exception:
                ```
                ${exception.stackTraceToString().replaceIndent("                ").trimStart()}
                ```
                """.trimIndent(),
            )
        }
        .getOrNull()

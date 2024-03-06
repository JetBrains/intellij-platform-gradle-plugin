// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.model

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import org.jdom2.Document
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import java.io.InputStream
import java.io.StringWriter
import java.nio.file.Path
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

class XmlExtractor<T>(private val context: String? = null) {

    private val jaxbContext by lazy {
        JAXBContext.newInstance("org.jetbrains.intellij.platform.gradle.model", ObjectFactory::class.java.classLoader)
    }

    @Throws(JAXBException::class)
    fun unmarshal(path: Path) = path.inputStream().use { unmarshal(it) }

    @Suppress("UNCHECKED_CAST")
    @Throws(JAXBException::class)
    fun unmarshal(inputStream: InputStream) = jaxbContext.createUnmarshaller().unmarshal(inputStream) as T

    @Throws(JAXBException::class)
    fun marshal(bean: T, path: Path) {
        jaxbContext.createMarshaller().marshal(bean, path.outputStream())

        path
            .inputStream()
            .use { JDOMUtil.loadDocument(it) }
            .let { transformXml(it, path) }
    }
}

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

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.model

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import org.jetbrains.intellij.transformXml
import org.jetbrains.intellij.warn
import java.io.File
import java.io.InputStream
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException

internal class XmlExtractor<T>(private val context: String? = null) {

    private val jaxbContext by lazy {
        JAXBContext.newInstance("org.jetbrains.intellij.model", ObjectFactory::class.java.classLoader)
    }

    @Throws(JAXBException::class)
    fun unmarshal(file: File) = file.inputStream().use { unmarshal(it) }

    @Suppress("UNCHECKED_CAST")
    @Throws(JAXBException::class)
    fun unmarshal(inputStream: InputStream) = jaxbContext.createUnmarshaller().unmarshal(inputStream) as T

    @Throws(JAXBException::class)
    fun marshal(bean: T, file: File) {
        jaxbContext.createMarshaller().marshal(bean, file)
        val document = file.inputStream().use { JDOMUtil.loadDocument(it) }
        transformXml(document, file)
    }

    fun fetch(path: String) = runCatching {
        unmarshal(File(path))
    }.onFailure {
        warn(context, "Failed to get products releases list: ${it.message}", it)
    }.getOrNull()
}

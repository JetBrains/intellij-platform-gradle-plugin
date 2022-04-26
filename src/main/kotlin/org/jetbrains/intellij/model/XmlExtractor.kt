package org.jetbrains.intellij.model

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import org.jetbrains.intellij.transformXml
import org.jetbrains.intellij.warn
import java.io.File
import java.io.InputStream
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException

class XmlExtractor<T>(private val context: String? = null) {

    private val jaxbContext by lazy {
        JAXBContext.newInstance("org.jetbrains.intellij.model", ObjectFactory::class.java.classLoader)
    }

    @Throws(JAXBException::class)
    fun unmarshal(file: File) = unmarshal(file.inputStream())

    @Suppress("UNCHECKED_CAST")
    @Throws(JAXBException::class)
    fun unmarshal(inputStream: InputStream) = jaxbContext.createUnmarshaller().unmarshal(inputStream) as T

    @Throws(JAXBException::class)
    fun marshal(bean: T, file: File) {
        jaxbContext.createMarshaller().marshal(bean, file)
        val document = JDOMUtil.loadDocument(file.inputStream())
        transformXml(document, file)
    }

    fun fetch(path: String) = runCatching {
        unmarshal(File(path))
    }.onFailure {
        warn(context, "Failed to get products releases list: ${it.message}", it)
    }.getOrNull()
}

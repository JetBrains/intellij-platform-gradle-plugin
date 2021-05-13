package org.jetbrains.intellij.model

import org.jdom2.Document
import org.jdom2.transform.JDOMSource
import java.io.File
import java.io.Serializable
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlElementWrapper
import javax.xml.bind.annotation.XmlRootElement

@XmlRootElement(name = "plugins")
data class PluginsCache(

    @set:XmlElement(name = "plugin")
    var plugins: List<PluginsCachePlugin> = emptyList(),
) : Serializable

data class PluginsCachePlugin(

    @set:XmlAttribute
    var id: String,

    @set:XmlAttribute
    var directoryName: String,

    @set:XmlElement(name = "dependency")
    @set:XmlElementWrapper
    var dependencies: List<String> = mutableListOf(),
) : Serializable

object PluginsCacheExtractor {

    private val jaxbContext by lazy {
        JAXBContext.newInstance(
            PluginsCache::class.java,
            PluginsCachePlugin::class.java,
        )
    }

    @Throws(JAXBException::class)
    fun unmarshal(document: Document) = jaxbContext.createUnmarshaller().unmarshal(JDOMSource(document)) as PluginsCache

    @Throws(JAXBException::class)
    fun marshal(bean: PluginsCache, file: File) = jaxbContext.createMarshaller().marshal(bean, file)
}

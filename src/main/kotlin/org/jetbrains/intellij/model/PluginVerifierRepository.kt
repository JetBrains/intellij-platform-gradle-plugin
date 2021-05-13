package org.jetbrains.intellij.model

import org.jdom2.Document
import org.jdom2.transform.JDOMSource
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlElementWrapper
import javax.xml.bind.annotation.XmlRootElement

@XmlRootElement(name = "metadata")
data class PluginVerifierRepository(

    @set:XmlElement
    var groupId: String? = null,

    @set:XmlElement
    var artifactId: String? = null,

    @set:XmlElement
    var versioning: PluginVerifierRepositoryVersioning? = null,
)

data class PluginVerifierRepositoryVersioning(

    @set:XmlElement
    var latest: String? = null,

    @set:XmlElement
    var release: String? = null,

    @set:XmlElement
    var lastUpdated: String? = null,

    @set:XmlElement(name = "version")
    @set:XmlElementWrapper
    var versions: List<String>? = mutableListOf(),
)

object PluginVerifierRepositoryExtractor {

    private val jaxbContext by lazy {
        JAXBContext.newInstance(
            PluginVerifierRepository::class.java,
            PluginVerifierRepositoryVersioning::class.java,
        )
    }

    @Throws(JAXBException::class)
    fun unmarshal(document: Document) = jaxbContext.createUnmarshaller().unmarshal(JDOMSource(document)) as PluginVerifierRepository
}

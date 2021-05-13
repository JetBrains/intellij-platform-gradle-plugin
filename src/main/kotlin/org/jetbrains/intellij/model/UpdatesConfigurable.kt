package org.jetbrains.intellij.model

import org.jdom2.Document
import org.jdom2.transform.JDOMSource
import java.io.File
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

@XmlRootElement(name = "application")
data class UpdatesConfigurable(

    @set:XmlElement(name = "component")
    var components: List<UpdatesConfigurableComponent> = emptyList(),
)

data class UpdatesConfigurableComponent(

    @set:XmlAttribute
    var name: String? = null,

    @set:XmlElement(name = "option")
    var options: List<UpdatesConfigurableOption> = emptyList(),
)

data class UpdatesConfigurableOption(

    @set:XmlAttribute
    var name: String? = null,

    @set:XmlAttribute
    var value: Boolean? = null,
)

object UpdatesConfigurableExtractor {

    private val jaxbContext by lazy {
        JAXBContext.newInstance(
            UpdatesConfigurable::class.java,
            UpdatesConfigurableComponent::class.java,
            UpdatesConfigurableOption::class.java,
        )
    }

    @Throws(JAXBException::class)
    fun unmarshal(document: Document) = jaxbContext.createUnmarshaller().unmarshal(JDOMSource(document)) as UpdatesConfigurable

    @Throws(JAXBException::class)
    fun marshal(bean: UpdatesConfigurable, file: File) = jaxbContext.createMarshaller().marshal(bean, file)
}

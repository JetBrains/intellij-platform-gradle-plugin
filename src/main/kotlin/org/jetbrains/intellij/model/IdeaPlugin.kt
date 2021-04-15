package org.jetbrains.intellij.model

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText

@JacksonXmlRootElement(localName = "idea-plugin")
data class IdeaPlugin(
    var name: String? = null,
    var id: String? = null,
    var description: String? = null,
    @JacksonXmlProperty(localName = "change-notes")
    var changeNotes: String? = null,
    var version: String? = null,
    @JacksonXmlProperty(localName = "idea-version")
    var ideaVersion: IdeaVersion? = null,
    // TODO: Vendor type
    var vendor: String? = null,
    @JacksonXmlElementWrapper(useWrapping = false, localName = "depends")
    var depends: List<Depends> = emptyList(),
    @JacksonXmlProperty(localName = "download-url")
    var downloadUrl: String? = null,
)

data class IdeaVersion(
    @JacksonXmlProperty(localName = "since-build", isAttribute = true)
    var sinceBuild: String? = null,
    @JacksonXmlProperty(localName = "until-build", isAttribute = true)
    var untilBuild: String? = null,
)

@JsonDeserialize(using = AttributeAndTextTypeDeserializer::class)
data class Depends(
    @JacksonXmlText
    var value: String? = null,
    @JacksonXmlProperty(localName = "optional", isAttribute = true)
    var optional: String? = null,
    @JacksonXmlProperty(localName = "config-file", isAttribute = true)
    var configFile: String? = null,
)

class AttributeAndTextTypeDeserializer : StdDeserializer<Depends>(Depends::class.java) {

    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?) =
        when (val node = p?.codec?.readTree<JsonNode>(p)) {
            is TextNode -> node.run {
                Depends(textValue())
            }
            is ObjectNode -> node.run {
                Depends(get("")?.textValue(), get("optional")?.textValue(), get("config-file")?.textValue())
            }
            else -> Depends()
        }
}

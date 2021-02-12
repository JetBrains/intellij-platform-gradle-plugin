package org.jetbrains.intellij

import com.fasterxml.jackson.annotation.JsonRootName

@JsonRootName("idea-plugin")
data class PluginXml(
    var name: String? = null,
    var id: String? = null,
    var vendor: String? = null,
)

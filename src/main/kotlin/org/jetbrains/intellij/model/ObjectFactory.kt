package org.jetbrains.intellij.model

import javax.xml.bind.annotation.XmlRegistry

@Suppress("unused")
@XmlRegistry
class ObjectFactory {

    fun createUpdatesConfigurable() = UpdatesConfigurable()

    fun createPluginsCache() = PluginsCache()

    fun createPluginVerifierRepository() = PluginVerifierRepository()
}

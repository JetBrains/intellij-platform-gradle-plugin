package org.jetbrains.intellij.model

import javax.xml.bind.annotation.XmlRegistry

@SuppressWarnings("unused")
@XmlRegistry
class ObjectFactory {

    fun createUpdatesConfigurable(): UpdatesConfigurable? {
        return UpdatesConfigurable()
    }

    fun createPluginsCache(): PluginsCache? {
        return PluginsCache()
    }

    fun createPluginVerifierRepository(): PluginVerifierRepository? {
        return PluginVerifierRepository()
    }
}

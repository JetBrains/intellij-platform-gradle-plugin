package org.jetbrains.intellij

import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.dependency.PluginDependency

/**
 * Configuration options for the {@link org.jetbrains.intellij.IntelliJPlugin}.
 */
@SuppressWarnings("GroovyUnusedDeclaration")
class IntelliJPluginExtension {
    Object[] plugins
    String localPath
    String version
    String type
    String pluginName
    String projectDirectory
    String sandboxDirectory
    String intellijRepo
    String alternativeIdePath
    String ideaDependencyCachePath
    boolean instrumentCode
    boolean updateSinceUntilBuild
    boolean sameSinceUntilBuild
    boolean downloadSources
    Publish publish

    IdeaDependency ideaDependency
    private final Set<PluginDependency> pluginDependencies = new HashSet<>()
    private final Map<String, Object> systemProperties = new HashMap<>()

    String getType() {
        if (version.startsWith("IU-") || "IU" == type) {
            return "IU"
        } 
        else if (version.startsWith("JPS-") || "JPS" == type) {
            return "JPS"
        } 
        else {
            return "IC"
        }
    }

    String getVersion() {
        if (version.startsWith('JPS-')) {
            return version.substring(4)
        }
        return version.startsWith('IU-') || version.startsWith('IC-') ? version.substring(3) : version
    }

    Set<PluginDependency> getPluginDependencies() {
        return pluginDependencies
    }

    def publish(Closure c) {
        publish.with(c)
    }

    static class Publish {
        /**
         * @deprecated intellij.publish.pluginId property is deprecated. Tag 'id' from plugin.xml will be used for uploading. 
         */
        String pluginId
        String username
        String password
        String[] channels

        /**
         * @deprecated
         */
        static def pluginId(String pluginId) {
            IntelliJPlugin.LOG.warn("intellij.publish.pluginId property is deprecated. " +
                    "Tag 'id' from plugin.xml will be used for uploading.")
        }

        def username(String username) {
            this.username = username
        }

        def password(String password) {
            this.password = password
        }

        def setChannel(String channel) {
            this.channels = [channel]
        }

        def channel(String channel) {
            channels(channel)
        }

        def channels(String... channels) {
            this.channels = channels
        }

        def setChannels(String... channels) {
            this.channels = channels
        }
    }

    Map<String, Object> getSystemProperties() {
        systemProperties
    }

    void setSystemProperties(Map<String, ?> properties) {
        systemProperties.clear()
        systemProperties.putAll(properties)
    }

    IntelliJPluginExtension systemProperties(Map<String, ?> properties) {
        systemProperties.putAll(properties)
        this
    }

    IntelliJPluginExtension systemProperty(String name, Object value) {
        systemProperties.put(name, value)
        this
    }
}

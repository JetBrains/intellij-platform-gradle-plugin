package org.jetbrains.intellij

import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.dependency.PluginDependency

/**
 * Configuration options for the {@link org.jetbrains.intellij.IntelliJPlugin}.
 */
@SuppressWarnings("GroovyUnusedDeclaration")
class IntelliJPluginExtension {
    Object[] plugins = []
    String localPath
    String version = IntelliJPlugin.DEFAULT_IDEA_VERSION
    String type = 'IC'
    String pluginName
    String sandboxDirectory
    String intellijRepo = IntelliJPlugin.DEFAULT_INTELLIJ_REPO
    String alternativeIdePath
    String ideaDependencyCachePath
    boolean instrumentCode = true
    boolean updateSinceUntilBuild = true
    boolean sameSinceUntilBuild = false
    boolean downloadSources = true
    @Deprecated
    Publish publish = new Publish()

    IdeaDependency ideaDependency
    private final Set<PluginDependency> pluginDependencies = new HashSet<>()
    @Deprecated
    private final Map<String, Object> systemProperties = new HashMap<>()

    String getType() {
        if (version == null) {
            return "IC"
        }
        if (version.startsWith("IU-") || "IU" == type) {
            return "IU"
        } else if (version.startsWith("JPS-") || "JPS" == type) {
            return "JPS"
        } else if (version.startsWith("RS-") || "RS" == type) {
            return "RS"
        } else if (version.startsWith("RD-") || "RD" == type) {
            return "RD"
        } else {
            return "IC"
        }
    }

    String getVersion() {
        if (version == null) {
            return null
        }
        if (version.startsWith('JPS-')) {
            return version.substring(4)
        }
        return version.startsWith('IU-') || version.startsWith('IC-') || version.startsWith('RS-') || version.startsWith('RD-') ? version.substring(3) : version
    }

    Set<PluginDependency> getPluginDependencies() {
        return pluginDependencies
    }

    def publish(Closure c) {
        publish.with(c)
    }

    /**
     * @deprecated
     */
    static class Publish {
        /**
         * @deprecated intellij.publish.pluginId property is deprecated. Tag 'id' from plugin.xml will be used for uploading. 
         */
        String pluginId
        String username
        String password
        String[] channels

        static def pluginId(String pluginId) {
            IntelliJPlugin.LOG.warn("intellij.publish.pluginId property is deprecated. " +
                    "Tag 'id' from plugin.xml will be used for uploading.")
        }

        def username(String username) {
            IntelliJPlugin.LOG.warn("intellij.publish.username property is deprecated. " +
                    "Use `publishPlugin { username 'username' }` instead.")
            this.username = username
        }

        def password(String password) {
            IntelliJPlugin.LOG.warn("intellij.publish.password property is deprecated. " +
                    "Use `publishPlugin { password 'password' }` instead.")
            this.password = password
        }

        def setChannel(String channel) {
            IntelliJPlugin.LOG.warn("intellij.publish.channel property is deprecated. " +
                    "Use `publishPlugin { channel 'channel' }` instead.")
            this.channels = [channel]
        }

        def channel(String channel) {
            IntelliJPlugin.LOG.warn("intellij.publish.channel property is deprecated. " +
                    "Use `publishPlugin { channel 'channel' }` instead.")
            channels(channel)
        }

        def channels(String... channels) {
            IntelliJPlugin.LOG.warn("intellij.publish.channels property is deprecated. " +
                    "Use `publishPlugin { channels 'channels' }` instead.")
            this.channels = channels
        }

        def setChannels(String... channels) {
            IntelliJPlugin.LOG.warn("intellij.publish.channels property is deprecated. " +
                    "Use `publishPlugin { channels 'channels' }` instead.")
            this.channels = channels
        }
    }

    /**
     * @deprecated
     */
    Map<String, Object> getSystemProperties() {
        systemProperties
    }

    /**
     * @deprecated
     */
    void setSystemProperties(Map<String, ?> properties) {
        IntelliJPlugin.LOG.warn("intellij.systemProperties property is deprecated. " +
                "Use systemProperties property of a particular task like `runIde` or `test`.")
        systemProperties.clear()
        systemProperties.putAll(properties)
    }

    /**
     * @deprecated
     */
    IntelliJPluginExtension systemProperties(Map<String, ?> properties) {
        IntelliJPlugin.LOG.warn("intellij.systemProperties method is deprecated. " +
                "Use systemProperties method of a particular task like `runIde` or `test`.")
        systemProperties.putAll(properties)
        this
    }

    /**
     * @deprecated
     */
    IntelliJPluginExtension systemProperty(String name, Object value) {
        IntelliJPlugin.LOG.warn("intellij.systemProperty method is deprecated. " +
                "Use systemProperty method of a particular task like `runIde` or `test`.")
        systemProperties.put(name, value)
        this
    }
}

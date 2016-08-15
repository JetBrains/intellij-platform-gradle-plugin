package org.jetbrains.intellij.tasks

import com.intellij.structure.domain.PluginManager
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.*
import org.gradle.util.CollectionUtils
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.dependency.PluginDependencyManager
import org.jetbrains.intellij.pluginRepository.PluginRepositoryInstance

class PublishTask extends ConventionTask {
    private File distributionFile;
    private Object host = PluginDependencyManager.DEFAULT_INTELLIJ_PLUGINS_REPO;
    private Object username;
    private Object password;
    private List<Object> channels = new ArrayList<Object>();

    @Input
    @SkipWhenEmpty
    String getHost() {
        return host.toString()
    }

    void setHost(Object host) {
        this.host = host
    }

    void host(Object host) {
        this.host = host
    }

    @InputFile
    @SkipWhenEmpty
    File getDistributionFile() {
        return distributionFile
    }

    void setDistributionFile(File distributionFile) {
        this.distributionFile = distributionFile
    }

    @Input
    @SkipWhenEmpty
    String getUsername() {
        return username != null ? username.toString() : null
    }

    void setUsername(Object username) {
        this.username = username
    }

    void username(Object username) {
        this.username = username
    }

    @Input
    @SkipWhenEmpty
    String getPassword() {
        return password != null ? password.toString() : null
    }

    void setPassword(Object password) {
        this.password = password
    }

    void password(Object password) {
        this.password = password
    }

    @Input
    String[] getChannels() {
        return CollectionUtils.stringize(channels.collect {
            it instanceof Closure ? (it as Closure).call() : it
        }.flatten())
    }

    void setChannels(Object... channels) {
        this.channels.clear()
        this.channels.addAll(channels as List)
    }

    void channels(Object... channels) {
        this.channels.addAll(channels as List)
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    @TaskAction
    protected void publishPlugin() {
        def channels = getChannels()
        if (channels.length == 0) {
            channels = ['default']
        }

        def host = getHost()
        def distributionFile = getDistributionFile()
        def pluginId = PluginManager.instance.createPlugin(distributionFile).pluginId
        for (String channel : channels) {
            IntelliJPlugin.LOG.info("Uploading plugin ${pluginId} from $distributionFile.absolutePath to $host, channel: $channel")
            try {
                def repoClient = new PluginRepositoryInstance(host, getUsername(), getPassword())
                repoClient.uploadPlugin(pluginId, distributionFile, channel && 'default' != channel ? channel : '')
                IntelliJPlugin.LOG.info("Uploaded successfully")
            }
            catch (exception) {
                throw new TaskExecutionException(this, new RuntimeException("Failed to upload plugin", exception))
            }
        }
    }
}

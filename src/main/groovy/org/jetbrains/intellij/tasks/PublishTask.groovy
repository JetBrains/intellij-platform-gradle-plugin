package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.GradleException
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.*
import org.gradle.util.CollectionUtils
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.Utils
import org.jetbrains.intellij.dependency.PluginDependencyManager
import org.jetbrains.intellij.pluginRepository.PluginRepositoryInstance

class PublishTask extends ConventionTask {
    private Object distributionFile
    private Object host = PluginDependencyManager.DEFAULT_INTELLIJ_PLUGINS_REPO
    private Object username
    private Object password
    private List<Object> channels = new ArrayList<Object>()

    PublishTask() {
        enabled = !project.gradle.startParameter.offline
    }

    @Input
    String getHost() {
        Utils.stringInput(host)
    }

    void setHost(Object host) {
        this.host = host
    }

    void host(Object host) {
        this.host = host
    }

    @InputFile
    File getDistributionFile() {
        distributionFile != null ? project.file(distributionFile) : null
    }

    void setDistributionFile(Object distributionFile) {
        this.distributionFile = distributionFile
    }

    void distributionFile(Object distributionFile) {
        this.distributionFile = distributionFile
    }

    @Input
    String getUsername() {
        Utils.stringInput(username)
    }

    void setUsername(Object username) {
        this.username = username
    }

    void username(Object username) {
        this.username = username
    }

    @Input
    String getPassword() {
        Utils.stringInput(password)
    }

    void setPassword(Object password) {
        this.password = password
    }

    void password(Object password) {
        this.password = password
    }

    @Input
    @Optional
    String[] getChannels() {
        CollectionUtils.stringize(channels.collect {
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
        if (!channels || channels.length == 0) {
            channels = ['default']
        }

        def host = getHost()
        def distributionFile = getDistributionFile()
        def creationResult = IdePluginManager.createManager().createPlugin(distributionFile)
        if (creationResult instanceof PluginCreationSuccess) {
            def pluginId = creationResult.plugin.pluginId
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
        } else if (creationResult instanceof PluginCreationFail) {
            def problems = creationResult.errorsAndWarnings.findAll { it.level == PluginProblem.Level.ERROR }.join(", ")
            throw new TaskExecutionException(this, new GradleException("Cannot upload plugin. $problems"))
        } else {
            throw new TaskExecutionException(this, new GradleException("Cannot upload plugin. $cre"))
        }
    }
}

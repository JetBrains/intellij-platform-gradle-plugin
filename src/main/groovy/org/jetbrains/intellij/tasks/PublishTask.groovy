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
import org.jetbrains.intellij.pluginRepository.PluginRepositoryInstance

class PublishTask extends ConventionTask {
    private Object distributionFile
    private Object host = "https://plugins.jetbrains.com"
    private Object username
    private Object password
    private Object token
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
    @Optional
    String getToken() {
        Utils.stringInput(token)
    }

    void setToken(Object token) {
        this.token = token
    }

    void token(Object token) {
        this.token = token
    }

    @Input
    @Optional
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
    @Optional
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
        validateInput()
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
                    def repoClient = getToken() ? new PluginRepositoryInstance(host, getToken())
                            : new PluginRepositoryInstance(host, getUsername(), getPassword())
                    repoClient.uploadPlugin(pluginId, distributionFile, channel && 'default' != channel ? channel : '')
                    IntelliJPlugin.LOG.info("Uploaded successfully")
                }
                catch (exception) {
                    throw new TaskExecutionException(this, new GradleException("Failed to upload plugin", exception))
                }
            }
        } else if (creationResult instanceof PluginCreationFail) {
            def problems = creationResult.errorsAndWarnings.findAll { it.level == PluginProblem.Level.ERROR }.join(", ")
            throw new TaskExecutionException(this, new GradleException("Cannot upload plugin. $problems"))
        } else {
            throw new TaskExecutionException(this, new GradleException("Cannot upload plugin. $creationResult"))
        }
    }

    private void validateInput() {
        if (!getToken()) {
            if (!getPassword() && !getUsername()) {
                throw new TaskExecutionException(this, new GradleException('token or username/password properties must be specified for plugin publishing'))
            } else {
                IntelliJPlugin.LOG.warn("Password authentication for uploading is deprecated. Use token access instead: http://www.jetbrains.org/intellij/sdk/docs/plugin_repository/api/plugin_upload.html")
                if (!getPassword()) {
                    throw new TaskExecutionException(this, new GradleException('No value has been specified for property \'password\''))
                } else if (!getUsername()) {
                    throw new TaskExecutionException(this, new GradleException('No value has been specified for property \'username\''))
                }
            }
        }
    }
}

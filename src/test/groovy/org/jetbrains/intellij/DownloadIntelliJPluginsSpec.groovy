package org.jetbrains.intellij

import org.gradle.api.plugins.BasePlugin

class DownloadIntelliJPluginsSpec extends IntelliJPluginSpecBase {
    def mavenCacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.plugins')
    def pluginsCacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/plugins')

    @Override
    def setup() {
        mavenCacheDir.deleteDir()
        pluginsCacheDir.deleteDir()
    }

    def 'download zip plugin'() {
        given:
        buildFile << 'intellij { plugins = ["org.intellij.plugins.markdown:2017.2.20170404"] }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def pluginDir = new File(mavenCacheDir, 'org.intellij.plugins.markdown/2017.2.20170404')
        pluginDir.list().contains('ff7b86635cc3ec6d9ea33d0e1193c3afb33c22ad')
        new File(pluginDir, 'ff7b86635cc3ec6d9ea33d0e1193c3afb33c22ad').list().contains('org.intellij.plugins.markdown-2017.2.20170404.zip')
        pluginsCacheDir.list().contains('org.intellij.plugins.markdown-2017.2.20170404')
    }

    def 'download jar plugin'() {
        given:
        buildFile << 'intellij { plugins = ["org.jetbrains.postfixCompletion:0.8-beta"] }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def pluginDir = new File(mavenCacheDir, 'org.jetbrains.postfixCompletion/0.8-beta')
        pluginDir.list().contains('4c0b55a3dea095dd8e085b1c65fcc7b917b74dd5')
        new File(pluginDir, '4c0b55a3dea095dd8e085b1c65fcc7b917b74dd5').list().contains('org.jetbrains.postfixCompletion-0.8-beta.jar')
    }
}
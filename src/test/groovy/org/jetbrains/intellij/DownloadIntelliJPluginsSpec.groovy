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
        buildFile << 'intellij { plugins = ["org.intellij.plugins.markdown:8.0.0.20150929"] }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def pluginDir = new File(mavenCacheDir, 'org.intellij.plugins.markdown/8.0.0.20150929')
        pluginDir.list().contains('24b1d87bfb3d679b5f1438764e3867a3d39c1972')
        new File(pluginDir, '24b1d87bfb3d679b5f1438764e3867a3d39c1972').list().contains('org.intellij.plugins.markdown-8.0.0.20150929.zip')
        pluginsCacheDir.list().contains('org.intellij.plugins.markdown-8.0.0.20150929')
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
package org.jetbrains.intellij

import org.gradle.api.plugins.BasePlugin

class DownloadIntelliJPluginsSpec extends IntelliJPluginSpecBase {
    def mavenCacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/')
    def pluginsCacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/')

    @Override
    def setup() {
        mavenCacheDir.deleteDir()
        pluginsCacheDir.deleteDir()
    }

    def 'download zip plugin from non-default channel'() {
        given:
        buildFile << 'intellij { plugins = ["CSS-X-Fire:1.53.nightly@nightly"] }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def pluginDir = new File(mavenCacheDir, 'nightly.com.jetbrains.plugins/CSS-X-Fire/1.53.nightly')
        pluginDir.list().contains('ece618ee1662d8df775ef57c412a00c6c6086cbb')
        new File(pluginDir, 'ece618ee1662d8df775ef57c412a00c6c6086cbb').list().contains('CSS-X-Fire-1.53.nightly.zip')
        new File(pluginsCacheDir, 'unzipped.nightly.com.jetbrains.plugins').list().contains('CSS-X-Fire-1.53.nightly')
    }

    def 'download zip plugin'() {
        given:
        buildFile << 'intellij { plugins = ["org.intellij.plugins.markdown:191.5849.16"] }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def pluginDir = new File(mavenCacheDir, 'com.jetbrains.plugins/org.intellij.plugins.markdown/191.5849.16')
        pluginDir.list().contains('d06aeb05643e50406ff5f9325b63f9b993a6d4c')
        new File(pluginDir, 'd06aeb05643e50406ff5f9325b63f9b993a6d4c').list().contains('org.intellij.plugins.markdown-191.5849.16.zip')
        new File(pluginsCacheDir, 'unzipped.com.jetbrains.plugins').list().contains('org.intellij.plugins.markdown-191.5849.16')
    }

    def 'download jar plugin'() {
        given:
        buildFile << 'intellij { plugins = ["org.jetbrains.postfixCompletion:0.8-beta"] }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def pluginDir = new File(mavenCacheDir, 'com.jetbrains.plugins/org.jetbrains.postfixCompletion/0.8-beta')
        pluginDir.list().contains('4c0b55a3dea095dd8e085b1c65fcc7b917b74dd5')
        new File(pluginDir, '4c0b55a3dea095dd8e085b1c65fcc7b917b74dd5').list().contains('org.jetbrains.postfixCompletion-0.8-beta.jar')
    }
}
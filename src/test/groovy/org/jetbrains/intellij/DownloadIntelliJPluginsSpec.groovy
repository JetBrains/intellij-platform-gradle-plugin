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
        buildFile << 'intellij { plugins = ["CSS-X-Fire:1.51.nightly@nightly"] }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def pluginDir = new File(mavenCacheDir, 'nightly.com.jetbrains.plugins/CSS-X-Fire/1.51.nightly')
        pluginDir.list().contains('7da8696282e4e0a93fe8061ab062f6ae431dfd11')
        new File(pluginDir, '7da8696282e4e0a93fe8061ab062f6ae431dfd11').list().contains('CSS-X-Fire-1.51.nightly.zip')
        new File(pluginsCacheDir, 'unzipped.nightly.com.jetbrains.plugins').list().contains('CSS-X-Fire-1.51.nightly')
    }

    def 'download zip plugin'() {
        given:
        buildFile << 'intellij { plugins = ["org.intellij.plugins.markdown:2017.2.20170404"] }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def pluginDir = new File(mavenCacheDir, 'com.jetbrains.plugins/org.intellij.plugins.markdown/2017.2.20170404')
        pluginDir.list().contains('ff7b86635cc3ec6d9ea33d0e1193c3afb33c22ad')
        new File(pluginDir, 'ff7b86635cc3ec6d9ea33d0e1193c3afb33c22ad').list().contains('org.intellij.plugins.markdown-2017.2.20170404.zip')
        new File(pluginsCacheDir, 'unzipped.com.jetbrains.plugins').list().contains('org.intellij.plugins.markdown-2017.2.20170404')
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
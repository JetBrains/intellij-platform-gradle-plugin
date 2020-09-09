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
        buildFile << 'intellij { plugins = ["CSS-X-Fire:1.55@nightly"] }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def pluginDir = new File(mavenCacheDir, 'nightly.com.jetbrains.plugins/CSS-X-Fire/1.55')
        pluginDir.list().contains('b36713d18b7845349268d9ba4ce4fedaff50d7a5')
        new File(pluginDir, 'b36713d18b7845349268d9ba4ce4fedaff50d7a5').list().contains('CSS-X-Fire-1.55.zip')
        new File(pluginsCacheDir, 'unzipped.nightly.com.jetbrains.plugins').list().contains('CSS-X-Fire-1.55')
    }

    def 'download zip plugin'() {
        given:
        buildFile << 'intellij { plugins = ["org.intellij.plugins.markdown:201.6668.74"] }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def pluginDir = new File(mavenCacheDir, 'com.jetbrains.plugins/org.intellij.plugins.markdown/201.6668.74')
        pluginDir.list().contains('5c9f1865c461d37e60c7083e8db9ad40c4bed98c')
        new File(pluginDir, '5c9f1865c461d37e60c7083e8db9ad40c4bed98c').list().contains('org.intellij.plugins.markdown-201.6668.74.zip')
        new File(pluginsCacheDir, 'unzipped.com.jetbrains.plugins').list().contains('org.intellij.plugins.markdown-201.6668.74')
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

    def 'download plugin from custom repository'() {
        URL resource = this.getClass().getClassLoader().getResource("custom-repo/plugins.xml")

        given:
        buildFile << "intellij { pluginsRepo { custom('${resource}') }; plugins = [\"com.intellij.plugins.emacskeymap:201.6251.22\"] }"

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        new File(pluginsCacheDir, 'unzipped.com.jetbrains.plugins').list().contains('com.intellij.plugins.emacskeymap-201.6251.22')
    }
}
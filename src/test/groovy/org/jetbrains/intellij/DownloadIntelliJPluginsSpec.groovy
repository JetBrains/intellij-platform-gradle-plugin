package org.jetbrains.intellij

import org.gradle.api.plugins.BasePlugin
import org.gradle.internal.os.OperatingSystem
import org.junit.Assume

class DownloadIntelliJPluginsSpec extends IntelliJPluginSpecBase {
    def pluginsRepoCacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.plugins')
    def pluginsNightlyRepoCacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/nightly.com.jetbrains.plugins')
    def pluginsCacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/unzipped.com.jetbrains.plugins')
    def pluginsNightlyCacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/unzipped.nightly.com.jetbrains.plugins')

    @Override
    def setup() {
        pluginsRepoCacheDir.deleteDir()
        pluginsNightlyRepoCacheDir.deleteDir()
        pluginsCacheDir.deleteDir()
        pluginsNightlyCacheDir.deleteDir()
    }

    def 'download zip plugin from non-default channel'() {
        given:
        buildFile << 'intellij { plugins = ["CSS-X-Fire:1.55@nightly"] }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def pluginDir = new File(pluginsNightlyRepoCacheDir, 'CSS-X-Fire/1.55')
        pluginDir.list().contains('9cab70cc371b245cd808ade65630f505a6443b0d')
        new File(pluginDir, '9cab70cc371b245cd808ade65630f505a6443b0d').list().contains('CSS-X-Fire-1.55.zip')
        pluginsNightlyCacheDir.list().contains('CSS-X-Fire-1.55')
    }

    def 'download zip plugin'() {
        given:
        buildFile << 'intellij { plugins = ["org.intellij.plugins.markdown:201.6668.74"] }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def pluginDir = new File(pluginsRepoCacheDir, 'org.intellij.plugins.markdown/201.6668.74')
        pluginDir.list().contains('17328855fcd031f39a805db934c121eaa25dedfb')
        new File(pluginDir, '17328855fcd031f39a805db934c121eaa25dedfb').list().contains('org.intellij.plugins.markdown-201.6668.74.zip')
        pluginsCacheDir.list().contains('org.intellij.plugins.markdown-201.6668.74')
    }

    def 'download jar plugin'() {
        given:
        buildFile << 'intellij { plugins = ["org.jetbrains.postfixCompletion:0.8-beta"] }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def pluginDir = new File(pluginsRepoCacheDir, 'org.jetbrains.postfixCompletion/0.8-beta')
        pluginDir.list().contains('dd37fa3fb1ecbf3d1c2fdb0049ba821fd32f2565')
        new File(pluginDir, 'dd37fa3fb1ecbf3d1c2fdb0049ba821fd32f2565').list().contains('org.jetbrains.postfixCompletion-0.8-beta.jar')
    }

    def 'download plugin from custom repository'() {
        URL resource = this.getClass().getClassLoader().getResource("custom-repo/updatePlugins.xml")

        given:
        buildFile << "intellij { pluginsRepo { custom('${resource}') }; plugins = [\"com.intellij.plugins.emacskeymap:201.6251.22\"] }"

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        pluginsCacheDir.list().contains('com.intellij.plugins.emacskeymap-201.6251.22')
    }

    def 'download plugin from custom repository 2'() {
        URL resource = this.getClass().getClassLoader().getResource("custom-repo-2/plugins.xml")

        given:
        buildFile << "intellij { pluginsRepo { custom('${resource}') }; plugins = [\"com.intellij.plugins.emacskeymap:201.6251.22\"] }"

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        pluginsCacheDir.list().contains('com.intellij.plugins.emacskeymap-201.6251.22')
    }

    def 'download plugin from custom repository with query'() {
        Assume.assumeFalse(OperatingSystem.current().isWindows())

        URL resource = this.getClass().getClassLoader().getResource("custom-repo-2/plugins.xml")

        given:
        buildFile << "intellij { pluginsRepo { custom('${resource}?query=1') }; plugins = [\"com.intellij.plugins.emacskeymap:201.6251.22\"] }"

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        pluginsCacheDir.list().contains('com.intellij.plugins.emacskeymap-201.6251.22')
    }

    def 'download plugin from custom repository without xml'() {
        URL resource = this.getClass().getClassLoader().getResource("custom-repo")

        given:
        buildFile << "intellij { pluginsRepo { custom('${resource}') }; plugins = [\"com.intellij.plugins.emacskeymap:201.6251.22\"] }"

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        pluginsCacheDir.list().contains('com.intellij.plugins.emacskeymap-201.6251.22')
    }

    def 'download plugin from custom repository without xml with query'() {
        URL resource = this.getClass().getClassLoader().getResource("custom-repo")

        given:
        buildFile << "intellij { pluginsRepo { custom('${resource}?query=1') }; plugins = [\"com.intellij.plugins.emacskeymap:201.6251.22\"] }"

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        pluginsCacheDir.list().contains('com.intellij.plugins.emacskeymap-201.6251.22')
    }
}
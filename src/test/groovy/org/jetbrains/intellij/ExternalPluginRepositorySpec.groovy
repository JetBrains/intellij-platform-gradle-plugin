package org.jetbrains.intellij

class ExternalPluginRepositorySpec extends IntelliJPluginSpecBase {
    def 'find jar-type plugin'() {
        given:
        def repository = new ExternalPluginRepository('http://plugins.jetbrains.com', gradleHome)

        when:
        def plugin = repository.findPlugin('org.jetbrains.postfixCompletion', '0.8-beta', null)

        then:
        assert plugin != null
        assert plugin.file.name == 'org.jetbrains.postfixCompletion-master-0.8-beta.jar'
        assert collectFilePaths(plugin.jarFiles, repository.cacheDirectory.absolutePath) ==
                ['/plugins.jetbrains.com/org.jetbrains.postfixCompletion-master-0.8-beta.jar'] as Set
    }

    def 'find zip-type plugin'() {
        given:
        def repository = new ExternalPluginRepository('http://plugins.jetbrains.com', gradleHome)

        when:
        def plugin = repository.findPlugin('org.intellij.plugins.markdown', '8.5.0.20160208', null)

        then:
        assert plugin != null
        assert plugin.file.name == 'org.intellij.plugins.markdown-master-8.5.0.20160208'
        assert collectFilePaths(plugin.jarFiles, repository.cacheDirectory.absolutePath) ==
                ['/plugins.jetbrains.com/org.intellij.plugins.markdown-master-8.5.0.20160208/lib/markdown-javafx-preview.jar',
                 '/plugins.jetbrains.com/org.intellij.plugins.markdown-master-8.5.0.20160208/lib/Loboevolution.jar',
                 '/plugins.jetbrains.com/org.intellij.plugins.markdown-master-8.5.0.20160208/lib/intellij-markdown.jar',
                 '/plugins.jetbrains.com/org.intellij.plugins.markdown-master-8.5.0.20160208/lib/markdown.jar'] as Set
    }

    def collectFilePaths(Collection<File> files, String cacheDir) {
        def paths = new HashSet()
        files.each {
            paths << adjustWindowsPath(it.absolutePath.substring(cacheDir.length()))
        }
        paths
    }

}
package org.jetbrains.intellij

import groovy.io.FileType

class PrepareSandboxTaskSpec extends IntelliJPluginSpecBase {
    def 'prepare sandbox task'() {
        given:
        writeJavaFile()
        file('src/main/resources/META-INF/other.xml') << ''
        file('src/main/resources/META-INF/nonIncluded.xml') << ''
        pluginXml << '<idea-plugin version="2"><depends config-file="other.xml"/></idea-plugin>'
        buildFile << """version='0.42.123'
intellij { 
    version = '14.1.4'
    pluginName = 'myPluginName' 
    plugins = ['copyright'] 
}
dependencies { 
    compile 'joda-time:joda-time:2.8.1'
}

"""
        when:
        def project = run(PrepareSandboxTask.NAME)

        then:
        File sandbox = new File(project.buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)
        assert collectPaths(sandbox) == ['/plugins/myPluginName/classes/App.class',
                                         '/plugins/myPluginName/classes/META-INF/nonIncluded.xml',
                                         '/plugins/myPluginName/lib/joda-time-2.8.1.jar',
                                         '/plugins/myPluginName/META-INF/other.xml',
                                         '/plugins/myPluginName/META-INF/plugin.xml']
        assertFileContent(new File(sandbox, 'plugins/myPluginName/META-INF/plugin.xml'), """<idea-plugin version="2">
  <depends config-file="other.xml"/>
  <version>0.42.123</version>
  <idea-version since-build="141.1532.4" until-build="141.9999"/>
</idea-plugin>""");
    }

    def 'prepare custom sandbox task'() {
        given:
        writeJavaFile()
        file('src/main/resources/META-INF/other.xml') << ''
        file('src/main/resources/META-INF/nonIncluded.xml') << ''
        pluginXml << '<idea-plugin version="2"><depends config-file="other.xml"/></idea-plugin>'
        def sandboxPath = adjustWindowsPath("${dir.root.absolutePath}/customSandbox")
        buildFile << """version='0.42.123'
intellij { 
    version = '14.1.4'
    pluginName = 'myPluginName' 
    plugins = ['copyright'] 
    sandboxDirectory = '${sandboxPath}'
}
dependencies { 
    compile 'joda-time:joda-time:2.8.1'
}

"""
        when:
        run(PrepareSandboxTask.NAME)

        then:
        assert collectPaths(new File(sandboxPath)) == ['/plugins/myPluginName/classes/App.class',
                                                       '/plugins/myPluginName/classes/META-INF/nonIncluded.xml',
                                                       '/plugins/myPluginName/lib/joda-time-2.8.1.jar',
                                                       '/plugins/myPluginName/META-INF/other.xml',
                                                       '/plugins/myPluginName/META-INF/plugin.xml']
        assertFileContent(new File(sandboxPath, 'plugins/myPluginName/META-INF/plugin.xml'), """<idea-plugin version="2">
  <depends config-file="other.xml"/>
  <version>0.42.123</version>
  <idea-version since-build="141.1532.4" until-build="141.9999"/>
</idea-plugin>""")
    }

    def 'use gradle project name if plugin name is not defined'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'

        when:
        def project = run(PrepareSandboxTask.NAME)

        then:
        assert collectPaths(new File(project.buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)) == ["/plugins/${project.name}/META-INF/plugin.xml"]
    }

    private static ArrayList collectPaths(File directory) {
        assert directory.exists()
        def paths = []
        directory.eachFileRecurse(FileType.FILES) {
            paths << adjustWindowsPath(it.absolutePath.substring(directory.absolutePath.length()))
        }
        paths
    }
}

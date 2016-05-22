package org.jetbrains.intellij

import java.util.zip.ZipFile


class BuildPluginTaskSpec extends IntelliJPluginSpecBase {
    def 'build plugin distribution'() {
        given:
        writeJavaFile()
        file('src/main/resources/META-INF/other.xml') << '<idea-plugin></idea-plugin>'
        file('src/main/resources/META-INF/nonIncluded.xml') << '<idea-plugin></idea-plugin>'
        pluginXml << '<idea-plugin version="2"><depends config-file="other.xml"/></idea-plugin>'
        buildFile << """\
            version='0.42.123'
            intellij { 
                pluginName = 'myPluginName' 
                plugins = ['copyright'] 
            }
            dependencies { 
                compile 'joda-time:joda-time:2.8.1'
            }""".stripIndent()
        when:
        def project = run(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        File distribution = new File(project.buildDirectory, 'distributions/myPluginName-0.42.123.zip')
        assert distribution.exists()

        def zipFile = new ZipFile(distribution)
        assert zipFile.entries().collect { it.name } as Set == ['myPluginName/',
                                                                'myPluginName/classes/',
                                                                'myPluginName/classes/App.class',
                                                                'myPluginName/classes/META-INF/',
                                                                'myPluginName/classes/META-INF/nonIncluded.xml',
                                                                'myPluginName/lib/',
                                                                'myPluginName/lib/joda-time-2.8.1.jar',
                                                                'myPluginName/META-INF/',
                                                                'myPluginName/META-INF/other.xml',
                                                                'myPluginName/META-INF/plugin.xml'] as Set
        zipFile.getInputStream(zipFile.getEntry('myPluginName/META-INF/plugin.xml')).text.trim() == """\
            <idea-plugin version="2">
              <version>0.42.123</version>
              <idea-version since-build="141.1010.3" until-build="141.9999"/>
              <depends config-file="other.xml"/>
            </idea-plugin>""".stripIndent()
    }

    def 'use custom sandbox for distribution'() {
        given:
        writeJavaFile()
        file('src/main/resources/META-INF/other.xml') << '<idea-plugin></idea-plugin>'
        file('src/main/resources/META-INF/nonIncluded.xml') << '<idea-plugin></idea-plugin>'
        pluginXml << '<idea-plugin version="2"><depends config-file="other.xml"/></idea-plugin>'
        def sandboxPath = adjustWindowsPath("$dir.root.absolutePath/customSandbox")
        buildFile << """\
            version='0.42.123'
            intellij { 
                pluginName = 'myPluginName' 
                plugins = ['copyright'] 
                sandboxDirectory = '$sandboxPath'
            }
            dependencies { 
                compile 'joda-time:joda-time:2.8.1'
            }""".stripIndent()
        when:
        def project = run(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        File distribution = new File(project.buildDirectory, 'distributions/myPluginName-0.42.123.zip')
        assert distribution.exists()

        def zipFile = new ZipFile(distribution)
        assert zipFile.entries().collect { it.name } as Set == ['myPluginName/',
                                                                'myPluginName/classes/',
                                                                'myPluginName/classes/App.class',
                                                                'myPluginName/classes/META-INF/',
                                                                'myPluginName/classes/META-INF/nonIncluded.xml',
                                                                'myPluginName/lib/',
                                                                'myPluginName/lib/joda-time-2.8.1.jar',
                                                                'myPluginName/META-INF/',
                                                                'myPluginName/META-INF/other.xml',
                                                                'myPluginName/META-INF/plugin.xml'] as Set
        zipFile.getInputStream(zipFile.getEntry('myPluginName/META-INF/plugin.xml')).text.trim() == """\
            <idea-plugin version="2">
              <version>0.42.123</version>
              <idea-version since-build="141.1010.3" until-build="141.9999"/>
              <depends config-file="other.xml"/>
            </idea-plugin>""".stripIndent()
    }

    def 'use gradle project name for distribution if plugin name is not defined'() {
        given:
        buildFile << 'version="0.42.123"'
        pluginXml << '<idea-plugin version="2"></idea-plugin>'

        when:
        def project = run(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        //noinspection GrEqualsBetweenInconvertibleTypes
        assert new File(project.buildDirectory, "distributions").list() == ["$project.name-0.42.123.zip"]
    }


    def 'can compile classes that depends on external plugins'() {
        given:
        file('src/main/java/App.java') << """
import java.lang.String;
import org.jetbrains.annotations.NotNull;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
class App {
    public static void main(@NotNull String[] strings) {
        System.out.println(MarkdownLanguage.INSTANCE.getDisplayName());
    }
}
"""
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        buildFile << """\
            version='0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['org.intellij.plugins.markdown:8.0.0.20150929']
            }
            """.stripIndent()
        when:
        def project = run(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        File distribution = new File(project.buildDirectory, 'distributions/myPluginName-0.42.123.zip')
        assert distribution.exists()
        assert (new ZipFile(distribution).entries().collect { it.name }).contains(
                'myPluginName/classes/App.class'
        )
    }
}

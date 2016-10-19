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
                                                                'myPluginName/lib/',
                                                                'myPluginName/lib/joda-time-2.8.1.jar',
                                                                'myPluginName/lib/projectName-0.42.123.jar'] as Set

        def tmp = File.createTempFile("gradle-test", "jar")
        tmp.deleteOnExit()
        tmp << zipFile.getInputStream(zipFile.getEntry('myPluginName/lib/projectName-0.42.123.jar'))

        def jar = new ZipFile(tmp)
        assert jar.entries().collect { it.name } as Set == ['META-INF/',
                                                            'META-INF/MANIFEST.MF',
                                                            'App.class',
                                                            'META-INF/nonIncluded.xml',
                                                            'META-INF/other.xml',
                                                            'META-INF/plugin.xml'] as Set


        assert jar.getInputStream(jar.getEntry('META-INF/plugin.xml')).text.trim() == """\
            <idea-plugin version="2">
              <version>0.42.123</version>
              <idea-version since-build="141.1010" until-build="141.*"/>
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
                                                                'myPluginName/lib/',
                                                                'myPluginName/lib/joda-time-2.8.1.jar',
                                                                'myPluginName/lib/projectName-0.42.123.jar'] as Set
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

        def zip = new ZipFile(distribution)
        def tmp = File.createTempFile("gradle-test", "jar")
        tmp.deleteOnExit()
        tmp << zip.getInputStream(zip.getEntry('myPluginName/lib/projectName-0.42.123.jar'))
        assert (new ZipFile(tmp).entries().collect { it.name }).contains('App.class')
    }

    def 'build plugin without sources'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        buildFile << """\
            version='0.42.123'
            intellij { pluginName = 'myPluginName' }
            """.stripIndent()

        when:
        def project = run(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        File distribution = new File(project.buildDirectory, 'distributions/myPluginName-0.42.123.zip')
        assert distribution.exists()
        
        def zip = new ZipFile(distribution)
        assert zip.entries().collect { it.name } == ['myPluginName/', 
                                                     'myPluginName/lib/', 
                                                     'myPluginName/lib/projectName-0.42.123.jar'] 
        def tmp = File.createTempFile("gradle-test", "jar")
        tmp.deleteOnExit()
        tmp << zip.getInputStream(zip.getEntry('myPluginName/lib/projectName-0.42.123.jar'))
        assert new ZipFile(tmp).entries().collect { it.name } == ['META-INF/', 
                                                                  'META-INF/MANIFEST.MF', 
                                                                  'META-INF/plugin.xml']
    }
}

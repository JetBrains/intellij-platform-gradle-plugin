package org.jetbrains.intellij

import groovy.io.FileType
import org.gradle.tooling.model.GradleProject

import java.util.zip.ZipFile

class PrepareSandboxTaskSpec extends IntelliJPluginSpecBase {
    def 'prepare sandbox for two plugins'() {
        given:
        writeJavaFile()
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        buildFile << """\
            version='0.42.123'
            intellij.pluginName = 'myPluginName'
            """.stripIndent()
        file('settings.gradle') << "include 'nestedProject'"

        file('nestedProject/build.gradle') << buildFile.text
        file('nestedProject/build.gradle') << """\
            intellij.pluginName = 'myNestedPluginName'
            intellij.sandboxDirectory = "\${rootProject.buildDir}/idea-sandbox" 
            """.stripIndent()

        file('nestedProject/src/main/java/NestedAppFile.java') << "class NestedAppFile{}"
        file('nestedProject/src/main/resources/META-INF/plugin.xml') << pluginXml.text

        when:
        def project = run(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        File sandbox = sandbox(project)
        assert collectPaths(sandbox) == ['/plugins/myPluginName/lib/projectName-0.42.123.jar',
                                         '/plugins/myNestedPluginName/lib/nestedProject-0.42.123.jar',
                                         '/config/options/updates.xml'] as Set

        assert new ZipFile(new File(sandbox, '/plugins/myPluginName/lib/projectName-0.42.123.jar')).entries().collect {
            it.name
        } == ['META-INF/', 'META-INF/MANIFEST.MF', 'App.class', 'META-INF/plugin.xml']
        assert new ZipFile(new File(sandbox, '/plugins/myNestedPluginName/lib/nestedProject-0.42.123.jar')).entries().collect {
            it.name
        } == ['META-INF/', 'META-INF/MANIFEST.MF', 'NestedAppFile.class', 'META-INF/plugin.xml']
    }

    def 'prepare sandbox task without plugin_xml'() {
        given:
        writeJavaFile()
        buildFile << """\
            version='0.42.123'
            intellij { 
                pluginName = 'myPluginName' 
                plugins = ['copyright'] 
            }
            dependencies { 
                compile 'joda-time:joda-time:2.8.1'
            }\
            """.stripIndent()

        when:
        def project = run(true, IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        File sandbox = new File(project.buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)
        assert collectPaths(sandbox) == ['/plugins/myPluginName/lib/projectName-0.42.123.jar',
                                         '/plugins/myPluginName/lib/joda-time-2.8.1.jar',
                                         '/config/options/updates.xml'] as Set
    }

    def 'prepare sandbox task'() {
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
            }\
            """.stripIndent()

        when:
        def project = run(true, IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        File sandbox = new File(project.buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)
        assert collectPaths(sandbox) == ['/plugins/myPluginName/lib/projectName-0.42.123.jar',
                                         '/plugins/myPluginName/lib/joda-time-2.8.1.jar',
                                         '/config/options/updates.xml'] as Set
        def jar = new ZipFile(new File(sandbox, '/plugins/myPluginName/lib/projectName-0.42.123.jar'))
        assert jar.entries().collect {
            it.name
        } == ['META-INF/', 'META-INF/MANIFEST.MF', 'App.class', 'META-INF/nonIncluded.xml', 'META-INF/other.xml', 'META-INF/plugin.xml']
        assert jar.getInputStream(jar.getEntry('META-INF/plugin.xml')).text.trim() == """\
            <idea-plugin version="2">
              <version>0.42.123</version>
              <idea-version since-build="141.1010" until-build="141.*"/>
              <depends config-file="other.xml"/>
            </idea-plugin>""".stripIndent()
    }

    def 'prepare sandbox with external jar-type plugin'() {
        given:
        writeJavaFile()
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        buildFile << """\
            intellij {
                plugins = ['org.jetbrains.postfixCompletion:0.8-beta']
                pluginName = 'myPluginName'
            }
            """.stripIndent()
        when:
        def project = run(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        assert collectPaths(sandbox(project)) == [
                '/plugins/intellij-postfix.jar',
                '/plugins/myPluginName/lib/projectName.jar',
                '/config/options/updates.xml'
        ] as Set
    }

    def 'prepare sandbox with external zip-type plugin'() {
        given:
        writeJavaFile()
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        buildFile << """\
            intellij {
                plugins = ['org.intellij.plugins.markdown:8.0.0.20150929']
                pluginName = 'myPluginName'
            }
            """.stripIndent()
        when:
        def project = run(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        assert collectPaths(sandbox(project)) == [
                '/plugins/myPluginName/lib/projectName.jar',
                '/plugins/markdown/lib/default.css',
                '/plugins/markdown/lib/markdown.jar',
                '/plugins/markdown/lib/darcula.css',
                '/config/options/updates.xml',
                '/plugins/markdown/lib/kotlin-runtime.jar',
                '/plugins/markdown/lib/Loboevolution.jar',
                '/plugins/markdown/lib/intellij-markdown.jar',
                '/plugins/markdown/lib/kotlin-reflect.jar'
        ] as Set
    }

    def 'prepare custom sandbox task'() {
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
            }\
            """.stripIndent()
        when:
        run(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        def sandbox = new File(sandboxPath)
        then:
        assert collectPaths(sandbox) == ['/plugins/myPluginName/lib/projectName-0.42.123.jar',
                                         '/plugins/myPluginName/lib/joda-time-2.8.1.jar',
                                         '/config/options/updates.xml'] as Set
    }

    def 'use gradle project name if plugin name is not defined'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'

        when:
        def project = run(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        assert collectPaths(new File(project.buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)) == [
                "/plugins/$project.name/lib/projectName.jar",
                '/config/options/updates.xml'] as Set
    }

    def 'disable ide update without updates.xml'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'

        when:
        def project = run(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        assertFileContent(new File(project.buildDirectory, "$IntelliJPlugin.DEFAULT_SANDBOX/config/options/updates.xml"), '''\
            <application>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="false"/>
              </component>
            </application>''')
    }

    def 'disable ide update without updates component'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        def updatesFile = new File(directory("build/$IntelliJPlugin.DEFAULT_SANDBOX/config/options"), 'updates.xml')
        updatesFile.text = '''\
            <application>
              <component name="SomeOtherComponent">
                <option name="SomeOption" value="false"/>
              </component>
            </application>'''.stripIndent()

        when:
        def project = run(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        assertFileContent(new File(project.buildDirectory, "$IntelliJPlugin.DEFAULT_SANDBOX/config/options/updates.xml"), '''\
            <application>
              <component name="SomeOtherComponent">
                <option name="SomeOption" value="false"/>
              </component>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="false"/>
              </component>
            </application>''')
    }

    def 'disable ide update without check_needed option'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        def updatesFile = new File(directory("build/$IntelliJPlugin.DEFAULT_SANDBOX/config/options"), 'updates.xml')
        updatesFile.text = '''\
            <application>
              <component name="UpdatesConfigurable">
                <option name="SomeOption" value="false"/>
              </component>
            </application>'''.stripIndent()

        when:
        def project = run(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        assertFileContent(new File(project.buildDirectory, "$IntelliJPlugin.DEFAULT_SANDBOX/config/options/updates.xml"), '''\
            <application>
              <component name="UpdatesConfigurable">
                <option name="SomeOption" value="false"/>
                <option name="CHECK_NEEDED" value="false"/>
              </component>
            </application>''')
    }

    def 'disable ide update without value attribute'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        def updatesFile = new File(directory("build/$IntelliJPlugin.DEFAULT_SANDBOX/config/options"), 'updates.xml')
        updatesFile.text = '''\
            <application>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED"/>
              </component>
            </application>'''.stripIndent()

        when:
        def project = run(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        assertFileContent(new File(project.buildDirectory, "$IntelliJPlugin.DEFAULT_SANDBOX/config/options/updates.xml"), '''\
            <application>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="false"/>
              </component>
            </application>''')
    }

    def 'disable ide update'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'
        def updatesFile = new File(directory("build/$IntelliJPlugin.DEFAULT_SANDBOX/config/options"), 'updates.xml')
        updatesFile.text = '''\
            <application>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="true"/>
              </component>
            </application>'''.stripIndent()

        when:
        def project = run(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)

        then:
        assertFileContent(new File(project.buildDirectory, "$IntelliJPlugin.DEFAULT_SANDBOX/config/options/updates.xml"), '''\
            <application>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="false"/>
              </component>
            </application>''')
    }

    private static Set collectPaths(File directory) {
        assert directory.exists()
        def paths = new HashSet()
        directory.eachFileRecurse(FileType.FILES) {
            paths << adjustWindowsPath(it.absolutePath.substring(directory.absolutePath.length()))
        }
        paths
    }

    private static File sandbox(GradleProject project) {
        return new File(project.buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)
    }
}

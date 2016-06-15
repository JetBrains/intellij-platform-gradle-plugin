package org.jetbrains.intellij

import groovy.io.FileType

class PrepareSandboxTaskSpec extends IntelliJPluginSpecBase {
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
        def project = run(PrepareSandboxTask.NAME)

        then:
        File sandbox = new File(project.buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)
        assert collectPaths(sandbox) == ['/plugins/myPluginName/classes/App.class',
                                         '/plugins/myPluginName/classes/META-INF/nonIncluded.xml',
                                         '/plugins/myPluginName/lib/joda-time-2.8.1.jar',
                                         '/config/options/updates.xml',
                                         '/plugins/myPluginName/META-INF/other.xml',
                                         '/plugins/myPluginName/META-INF/plugin.xml'] as Set
        assertFileContent(new File(sandbox, 'plugins/myPluginName/META-INF/plugin.xml'), """\
            <idea-plugin version="2">
              <version>0.42.123</version>
              <idea-version since-build="141.1010" until-build="141.*"/>
              <depends config-file="other.xml"/>
            </idea-plugin>""");
    }

    def 'prepare sandbox with x:include'() {
        given:
        writeJavaFile()
        file('src/main/resources/META-INF/other.xml') << '<idea-plugin></idea-plugin>'
        file('src/main/resources/META-INF/otherFallback.xml') << '<idea-plugin></idea-plugin>'
        file('src/main/resources/META-INF/nonIncluded.xml') << '<idea-plugin></idea-plugin>'
        pluginXml << '''\
            <idea-plugin version="2" xmlns:xi="http://www.w3.org/2001/XInclude">
                <xi:include href="other.xml" xpointer="xpointer(/idea-plugin/*)">
                    <xi:fallback>
                        <xi:include href="otherFallback.xml" xpointer="xpointer(/idea-plugin/*)"/>
                    </xi:fallback>
                </xi:include>
            </idea-plugin>\
            '''.stripIndent()
        buildFile << """\
            version='0.42.123'
            intellij { 
                pluginName = 'myPluginName'  
            }""".stripIndent()

        when:
        def project = run(PrepareSandboxTask.NAME)

        then:
        File sandbox = new File(project.buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)
        assert collectPaths(sandbox) == ['/plugins/myPluginName/classes/App.class',
                                         '/plugins/myPluginName/classes/META-INF/nonIncluded.xml',
                                         '/config/options/updates.xml',
                                         '/plugins/myPluginName/META-INF/other.xml',
                                         '/plugins/myPluginName/META-INF/otherFallback.xml',
                                         '/plugins/myPluginName/META-INF/plugin.xml'] as Set
        assertFileContent(new File(sandbox, 'plugins/myPluginName/META-INF/plugin.xml'), """\
            <idea-plugin version="2">
              <version>0.42.123</version>
              <idea-version since-build="141.1010" until-build="141.*"/>
              <xi:include xmlns:xi="http://www.w3.org/2001/XInclude" href="other.xml" xpointer="xpointer(/idea-plugin/*)">
                <xi:fallback>
                  <xi:include href="otherFallback.xml" xpointer="xpointer(/idea-plugin/*)"/>
                </xi:fallback>
              </xi:include>
            </idea-plugin>""");
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
        def project = run(PrepareSandboxTask.NAME)

        then:
        File sandbox = new File(project.buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)
        assert collectPaths(sandbox) == [
                '/plugins/intellij-postfix.jar',
                '/plugins/myPluginName/META-INF/plugin.xml',
                '/plugins/myPluginName/classes/App.class',
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
        def project = run(PrepareSandboxTask.NAME)

        then:
        File sandbox = new File(project.buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)
        assert collectPaths(sandbox) == [
                '/plugins/markdown/lib/default.css',
                '/plugins/myPluginName/classes/App.class',
                '/plugins/markdown/lib/markdown.jar',
                '/plugins/markdown/lib/darcula.css',
                '/plugins/myPluginName/META-INF/plugin.xml',
                '/config/options/updates.xml',
                '/plugins/markdown/lib/kotlin-runtime.jar',
                '/plugins/markdown/lib/Loboevolution.jar',
                '/plugins/markdown/lib/intellij-markdown.jar',
                '/plugins/markdown/lib/kotlin-reflect.jar'
        ] as Set
    }

    def 'test transitive xml dependencies'() {
        given:
        writeJavaFile()
        file('src/main/resources/META-INF/other.xml') << '''\
            <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
                <depends config-file="transitiveDepends.xml"/>
                <xi:include href="transitiveInclude.xml" xpointer="xpointer(/idea-plugin/*)"/>
            </idea-plugin>'''
        file('src/main/resources/META-INF/transitiveDepends.xml') << '<idea-plugin></idea-plugin>'
        file('src/main/resources/META-INF/transitiveInclude.xml') << '<idea-plugin></idea-plugin>'
        pluginXml << '''\
            <idea-plugin version="2">
                <depends config-file="other.xml"/>                
            </idea-plugin>\
            '''.stripIndent()
        buildFile << """\
            version='0.42.123'
            intellij { 
                pluginName = 'myPluginName'  
            }""".stripIndent()

        when:
        def project = run(PrepareSandboxTask.NAME)

        then:
        File sandbox = new File(project.buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)
        assert collectPaths(sandbox) == ['/plugins/myPluginName/classes/App.class',
                                         '/plugins/myPluginName/classes/META-INF/transitiveDepends.xml',
                                         '/config/options/updates.xml',
                                         '/plugins/myPluginName/META-INF/other.xml',
                                         '/plugins/myPluginName/META-INF/transitiveInclude.xml',
                                         '/plugins/myPluginName/META-INF/plugin.xml'] as Set
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
        run(PrepareSandboxTask.NAME)

        then:
        assert collectPaths(new File(sandboxPath)) == ['/plugins/myPluginName/classes/App.class',
                                                       '/plugins/myPluginName/classes/META-INF/nonIncluded.xml',
                                                       '/plugins/myPluginName/lib/joda-time-2.8.1.jar',
                                                       '/config/options/updates.xml',
                                                       '/plugins/myPluginName/META-INF/other.xml',
                                                       '/plugins/myPluginName/META-INF/plugin.xml'] as Set
        assertFileContent(new File(sandboxPath, 'plugins/myPluginName/META-INF/plugin.xml'), """\
            <idea-plugin version="2">
              <version>0.42.123</version>
              <idea-version since-build="141.1010" until-build="141.*"/>
              <depends config-file="other.xml"/>
            </idea-plugin>""")
    }

    def 'use gradle project name if plugin name is not defined'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'

        when:
        def project = run(PrepareSandboxTask.NAME)

        then:
        assert collectPaths(new File(project.buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)) == ["/plugins/$project.name/META-INF/plugin.xml", '/config/options/updates.xml'] as Set
    }

    def 'disable ide update without updates.xml'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'

        when:
        def project = run(PrepareSandboxTask.NAME)

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
        def project = run(PrepareSandboxTask.NAME)

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
        def project = run(PrepareSandboxTask.NAME)

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
        def project = run(PrepareSandboxTask.NAME)

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
        def project = run(PrepareSandboxTask.NAME)

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
}

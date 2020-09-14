package org.jetbrains.intellij

import groovy.json.JsonSlurper

class RunPluginVerifierTaskSpec extends IntelliJPluginSpecBase {
    def 'run plugin verifier in specified version'() {
        given:
        buildFile << """
            version = "1.0.0"
            
            runPluginVerifier {
                verifierVersion = "1.241"
            }
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Starting the IntelliJ Plugin Verifier 1.241")
    }

    def 'run plugin verifier in the latest version'() {
        given:
        buildFile << """
            version = "1.0.0"
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        def url = new URL("https://api.bintray.com/packages/jetbrains/intellij-plugin-service/intellij-plugin-verifier/versions/_latest")
        def version = new JsonSlurper().parse(url)["name"]
        result.output.contains("Starting the IntelliJ Plugin Verifier $version")
    }

    def 'fallback to ide version specified in intellij configuration'() {
        given:
        buildFile << """
            version = "1.0.0"
            
            intellij {
                type = "CL"
                version = "2020.2"
            }
            """.stripIndent()
        pluginXml << """
            <idea-plugin version="2">
                <name>PluginName</name>
                <description>Plugin description</description>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        println result.output
        result.output.contains("PluginName:1.0.0 against CL-202.6397.106")
    }

    def 'fail for missing plugin descriptor'() {
        given:
        buildFile << """
            version = "1.0.0"
            """.stripIndent()
        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        println result.output
        result.output.contains("Plugin descriptor 'plugin.xml' is not found")
    }

    def 'test plugin against two IDEs'() {
        given:
        writeJavaFile()
        buildFile << """
            version = "1.0.0"
            
            runPluginVerifier {
                ides = ["IC-2020.2", "PY-2020.1"]
            }

            """.stripIndent()
        pluginXml << """
            <idea-plugin version="2">
                <name>PluginName</name>
                <description>Plugin description</description>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Plugin PluginName:1.0.0 against IC-202.6397.94: Compatible")
        result.output.contains("Plugin PluginName:1.0.0 against PY-201.6668.115: Compatible")
    }

    def 'set verification reports directory'() {
        given:
        writeJavaFile()
        buildFile << """
            version = "1.0.0"
            
            runPluginVerifier {
                verificationReportsDir = "\${project.buildDir}/foo"
            }

            """.stripIndent()
        pluginXml << """
            <idea-plugin version="2">
                <name>PluginName</name>
                <description>Plugin description</description>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        def directory = file("build/foo").absolutePath
        result.output.contains("Verification reports directory: /private$directory")
    }
}

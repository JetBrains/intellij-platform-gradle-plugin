package org.jetbrains.intellij

import org.jetbrains.intellij.tasks.RunPluginVerifierTask

class RunPluginVerifierTaskSpec extends IntelliJPluginSpecBase {
    def 'run plugin verifier in specified version'() {
        given:
        writePluginXmlFile()
        buildFile << """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = "2020.2.3"
                verifierVersion = "1.255"
            }
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Starting the IntelliJ Plugin Verifier 1.255")
    }

    def 'run plugin verifier in old version hosted on Bintray'() {
        given:
        writePluginXmlFile()
        buildFile << """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = "2020.2.3"
                verifierVersion = "1.254"
            }
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Starting the IntelliJ Plugin Verifier 1.254")
    }

    def 'run plugin verifier in the latest version'() {
        given:
        writePluginXmlFile()
        buildFile << """
            version = "1.0.0"

            runPluginVerifier {
                ideVersions = "2020.2.3"
            }
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        def version = RunPluginVerifierTask.resolveLatestVerifierVersion()
        result.output.contains("Starting the IntelliJ Plugin Verifier $version")
    }

    def 'test plugin against two IDEs'() {
        given:
        writeJavaFile()
        writePluginXmlFile()
        buildFile << """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = "IC-2020.2.3,PS-2020.1.3"
            }
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Plugin PluginName:1.0.0 against IC-202.7660.26: Compatible")
        result.output.contains("Plugin PluginName:1.0.0 against PS-201.8538.41: Compatible")
    }

    def 'set verification reports directory'() {
        given:
        writeJavaFile()
        writePluginXmlFile()
        buildFile << """
            version = "1.0.0"
            
            runPluginVerifier {
                verificationReportsDirectory = "\${project.buildDir}/foo"
                ideVersions = ["IC-2020.2.3"]
            }
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        def directory = file("build/foo").canonicalPath
        result.output.contains("Verification reports directory: $directory")
    }

    def 'fail on missing ideVersions property'() {
        given:
        writeJavaFile()
        writePluginXmlFile()
        buildFile << """
            version = "1.0.0"
            """.stripIndent()

        when:
        def result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("`ideVersions` and `localPaths` properties should not be empty")
    }

    def 'fail on :verifyPlugin task'() {
        given:
        writeJavaFile()
        buildFile << """
            version = "1.0.0"
            """.stripIndent()

        when:
        def result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Plugin descriptor 'plugin.xml' is not found")
        result.output.contains("Task :verifyPlugin FAILED")
    }

    def 'fail on Deprecated API usages'() {
        given:
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile << """
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                failureLevel = FailureLevel.DEPRECATED_API_USAGES
                ideVersions = "2020.2.3"
            }
            """.stripIndent()

        when:
        def result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Deprecated API usages")
        result.output.contains("org.gradle.api.GradleException: DEPRECATED_API_USAGES")
    }

    def 'pass on Deprecated API usages'() {
        given:
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile << """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = "2020.2.3"
            }
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Deprecated API usages")
        !result.output.contains("org.gradle.api.GradleException: DEPRECATED_API_USAGES")
    }

    def 'fail on incorrect ideVersion'() {
        given:
        writeJavaFile()
        writePluginXmlFile()
        buildFile << """
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = "foo,foo,,foo"
            }
            """.stripIndent()

        when:
        def result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("IDE 'foo' cannot be downloaded.")
    }

    def 'fail on any failureLevel'() {
        given:
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile << """
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = "2020.2.3"
                failureLevel = FailureLevel.ALL
            }
            """.stripIndent()

        when:
        def result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Deprecated API usages")
        result.output.contains("org.gradle.api.GradleException: DEPRECATED_API_USAGES")
    }

    def 'pass on any failureLevel'() {
        given:
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile << """
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = "2020.2.3"
                failureLevel = FailureLevel.NONE
            }
            """.stripIndent()

        when:
        def result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        then:
        result.output.contains("Deprecated API usages")
        !result.output.contains("org.gradle.api.GradleException: DEPRECATED_API_USAGES")
    }

    def 'run plugin verifier in offline mode'() {
        given:
        writePluginXmlFile()
        warmupGradle()
        buildFile << """
            version = "1.0.0"

            runPluginVerifier {
                ideVersions = "2020.1.3"
                verifierPath = "\${project.buildDir}/pluginVerifier.jar"
            }
            """.stripIndent()

        when:
        def version = RunPluginVerifierTask.resolveLatestVerifierVersion()

        file("build/pluginVerifier.jar").withOutputStream { out ->
            out << new URL("${IntelliJPlugin.DEFAULT_INTELLIJ_PLUGIN_VERIFIER_REPO}/org/jetbrains/intellij/plugins/verifier-cli/$version/verifier-cli-$version-all.jar").openStream()
        }

        def result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME, "--offline")

        then:
        result.output.contains("Gradle runs in offline mode.")
    }

    private void warmupGradle() {
        buildFile << """
            version = "1.0.0"

            runPluginVerifier {
                ideVersions = "2020.2.3"
            }
            """.stripIndent()
        build(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)
    }

    private void writeJavaFileWithDeprecation() {
        file('src/main/java/App.java') << """  
            import java.lang.String;
            import org.jetbrains.annotations.NotNull;
            import com.intellij.openapi.util.text.StringUtil;
            class App {
                public static void main(@NotNull String[] strings) {
                    StringUtil.firstLetterToUpperCase("foo");
                }
            }
            """.stripIndent()
    }

    private void writePluginXmlFile() {
        pluginXml << """
            <idea-plugin version="2">
                <name>PluginName</name>
                <description>Plugin description</description>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.stripIndent()
    }
}

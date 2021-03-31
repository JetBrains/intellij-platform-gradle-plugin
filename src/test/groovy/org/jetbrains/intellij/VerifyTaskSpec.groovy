package org.jetbrains.intellij

class VerifyTaskSpec extends IntelliJPluginSpecBase {
    def 'skip verifying empty directory'() {
        given:
        buildFile << "verifyPlugin { pluginDirectory = null }"

        when:
        def result = build(IntelliJPlugin.VERIFY_PLUGIN_TASK_NAME)

        then:
        result.output.contains('verifyPlugin NO-SOURCE')
    }

    def 'do not fail on warning by default'() {
        given:
        buildFile << "version '1.0'"
        pluginXml << """
            <idea-plugin version="2">
                <name>PluginName</name>
                <description>Привет, Мир!</description>
                <vendor>Zolotov</vendor>
            </idea-plugin>""".stripIndent()

        when:
        def result = build(IntelliJPlugin.VERIFY_PLUGIN_TASK_NAME)

        then:
        result.output.contains('Description is too short')
    }

    def 'fail on warning if option is disabled'() {
        given:
        buildFile << "version '1.0'\nverifyPlugin { ignoreWarnings = false }"
        pluginXml << """
            <idea-plugin version="2">
                <name>PluginName</name>
                <description>Привет, Мир!</description>
                <vendor>Zolotov</vendor>
            </idea-plugin>""".stripIndent()

        when:
        def result = buildAndFail(IntelliJPlugin.VERIFY_PLUGIN_TASK_NAME)

        then:
        result.output.contains('Description is too short')
    }


    def 'fail on errors by default'() {
        when:
        def result = buildAndFail(IntelliJPlugin.VERIFY_PLUGIN_TASK_NAME)

        then:
        result.output.contains("Plugin descriptor 'plugin.xml' is not found")
    }

    def 'do not fail on errors if option is enabled'() {
        given:
        buildFile << "verifyPlugin { ignoreFailures = true }"

        when:
        def result = build(IntelliJPlugin.VERIFY_PLUGIN_TASK_NAME)

        then:
        result.output.contains("Plugin descriptor 'plugin.xml' is not found")
    }

    def 'do not fail if there are no errors and warnings'() {
        given:
        buildFile << "version '1.0'\nverifyPlugin { ignoreWarnings = false }"
        pluginXml << """
            <idea-plugin version="2">
                <name>Verification test</name>
                <description>some description longlonglonglonglonglonglonglonglonglong enough</description>
                <vendor>Zolotov</vendor>
                <depends>com.intellij.modules.lang</depends>
            </idea-plugin>""".stripIndent()

        when:
        def result = build(IntelliJPlugin.VERIFY_PLUGIN_TASK_NAME)

        then:
        !result.output.contains('Plugin verification')
    }
}

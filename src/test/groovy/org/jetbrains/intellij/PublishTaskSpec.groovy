package org.jetbrains.intellij

class PublishTaskSpec extends IntelliJPluginSpecBase {
    def 'skip publishing plugin is distribution file is missing'() {
        given:
        buildFile << "publishPlugin { username = 'username'; password = 'password'; distributionFile = null; }\n" +
                "verifyPlugin { ignoreFailures = true }"

        when:
        def result = buildAndFail(IntelliJPlugin.PUBLISH_PLUGIN_TASK_NAME)

        then:
        result.output.contains('No value has been specified for property \'distributionFile\'')
    }

    def 'skip publishing if username is missing'() {
        given:
        buildFile << "publishPlugin { password = 'pass' }\nverifyPlugin { ignoreFailures = true }"

        when:
        def result = buildAndFail(IntelliJPlugin.PUBLISH_PLUGIN_TASK_NAME)

        then:
        result.output.contains('No value has been specified for property \'username\'')
    }

    def 'skip publishing if password is missing'() {
        given:
        buildFile << "publishPlugin { username = 'username' }\nverifyPlugin { ignoreFailures = true }"

        when:
        def result = buildAndFail(IntelliJPlugin.PUBLISH_PLUGIN_TASK_NAME)

        then:
        result.output.contains('No value has been specified for property \'password\'')
    }

}

package org.jetbrains.intellij

import org.gradle.tooling.BuildException

class PublishTaskSpec extends IntelliJPluginSpecBase {
    def 'skip publishing plugin is distribution file is missing'() {
        given:
        buildFile << "intellij { publish { username = 'username'; password = 'password' } }"

        when:
        run(true, IntelliJPlugin.PUBLISH_PLUGIN_TASK_NAME)

        then:
        thrown(BuildException)
        stderr.contains('No value has been specified for property \'distributionFile\'')
    }

    def 'skip publishing if username is missing'() {
        given:
        buildFile << "intellij { publish { password = 'pass' } }"

        when:
        run(true, IntelliJPlugin.PUBLISH_PLUGIN_TASK_NAME)

        then:
        thrown(BuildException)
        stderr.contains('No value has been specified for property \'username\'')
    }

    def 'skip publishing if password is missing'() {
        given:
        buildFile << "intellij { publish { username = 'username' } }"

        when:
        run(true, IntelliJPlugin.PUBLISH_PLUGIN_TASK_NAME)

        then:
        thrown(BuildException)
        stderr.contains('No value has been specified for property \'password\'')
    }

}

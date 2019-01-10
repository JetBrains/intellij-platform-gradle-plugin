package org.jetbrains.intellij

class BuildSearchableOptionsTaskSpec extends IntelliJPluginSpecBase {
    def 'skip building searchable options using IDEA prior 2019.1'() {
        given:
        buildFile << 'intellij { }'

        when:
        def result = build(IntelliJPlugin.BUILD_SEARCHABLE_OPTIONS_TASK_NAME)

        then:
        result.output.contains("$IntelliJPlugin.BUILD_SEARCHABLE_OPTIONS_TASK_NAME SKIPPED")
    }
}

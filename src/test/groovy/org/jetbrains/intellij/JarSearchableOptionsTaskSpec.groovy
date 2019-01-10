package org.jetbrains.intellij

class JarSearchableOptionsTaskSpec extends IntelliJPluginSpecBase {
    def 'skip jarring searchable options using IDEA prior 2019.1'() {
        given:
        buildFile << 'intellij { }'

        when:
        def result = build(IntelliJPlugin.JAR_SEARCHABLE_OPTIONS_TASK_NAME)

        then:
        result.output.contains("$IntelliJPlugin.JAR_SEARCHABLE_OPTIONS_TASK_NAME SKIPPED")
    }
}

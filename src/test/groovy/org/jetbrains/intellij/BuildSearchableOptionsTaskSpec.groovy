package org.jetbrains.intellij

class BuildSearchableOptionsTaskSpec extends SearchableOptionsSpecBase {
    def 'skip building searchable options using IDEA prior 2019.1'() {
        given:
        buildFile << "intellij { version = '14.1.4' }"

        when:
        def result = build(IntelliJPlugin.BUILD_SEARCHABLE_OPTIONS_TASK_NAME)

        then:
        result.output.contains("$IntelliJPlugin.BUILD_SEARCHABLE_OPTIONS_TASK_NAME SKIPPED")
    }

    def 'build searchable options produces XML'() {
        given:
        pluginXml << pluginXmlWithSearchableConfigurable
        buildFile << "intellij { version = '$intellijVersion' }"
        testSearchableConfigurableJava << searchableConfigurableCode

        when:
        def result = build(IntelliJPlugin.BUILD_SEARCHABLE_OPTIONS_TASK_NAME)

        then:
        result.output.contains("Starting searchable options index builder")
        result.output.contains("Searchable options index builder completed")

        def text = getSearchableOptionsXml("projectName").getText("UTF-8")
        text.contains("<configurable id=\"test.searchable.configurable\" configurable_name=\"Test Searchable Configurable\">")
        text.contains("hit=\"Label for Test Searchable Configurable\"")
    }
}

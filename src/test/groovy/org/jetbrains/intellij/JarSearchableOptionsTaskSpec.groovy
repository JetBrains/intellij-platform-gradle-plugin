package org.jetbrains.intellij

class JarSearchableOptionsTaskSpec extends SearchableOptionsSpecBase {
    def 'skip jarring searchable options using IDEA prior 2019.1'() {
        given:
        buildFile << "intellij { version = '14.1.4' }"

        when:
        def result = build(IntelliJPlugin.JAR_SEARCHABLE_OPTIONS_TASK_NAME)

        then:
        result.output.contains("$IntelliJPlugin.JAR_SEARCHABLE_OPTIONS_TASK_NAME SKIPPED")
    }

    def 'jar searchable options produces archive'() {
        given:
        pluginXml << pluginXmlWithSearchableConfigurable
        buildFile << "intellij { version = '$intellijVersion' }"
        testSearchableConfigurableJava << searchableConfigurableCode

        when:
        build(IntelliJPlugin.JAR_SEARCHABLE_OPTIONS_TASK_NAME)

        then:
        def libsSearchableOptions = new File(buildDirectory, 'libsSearchableOptions')
        libsSearchableOptions.exists()
        collectPaths(libsSearchableOptions) == ['/lib/searchableOptions.jar'] as Set
    }
}

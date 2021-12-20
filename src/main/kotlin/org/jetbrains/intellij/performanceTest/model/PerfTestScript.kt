package org.jetbrains.intellij.performanceTest.model

class PerfTestScript private constructor(val projectName: String?, val scriptContent: String?, val assertionTimeout: Long?) {
    data class Builder(var projectName: String? = null, var scriptContent: String? = null, var assertionTimeout: Long? = null) {
        fun projectName(value: String?): Builder = apply { this.projectName = value }

        fun scriptContent(value: String?): Builder = apply { this.scriptContent = value }
        fun appendScriptContent(value: String): Builder = apply {
            if (scriptContent == null) scriptContent = "$value\n" else scriptContent += "$value\n"
        }

        fun assertionTimeout(value: Long?): Builder = apply { this.assertionTimeout = value }
        fun build() = PerfTestScript(projectName, scriptContent, assertionTimeout)
    }
}

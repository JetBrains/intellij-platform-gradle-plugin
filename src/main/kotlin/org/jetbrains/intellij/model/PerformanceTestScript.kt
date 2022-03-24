package org.jetbrains.intellij.model

class PerformanceTestScript private constructor(
    val projectName: String?,
    val scriptContent: String?,
    val assertionTimeout: Long?,
) {

    data class Builder(
        var projectName: String? = null,
        var scriptContent: String? = null,
        var assertionTimeout: Long? = null,
    ) {
        fun projectName(value: String?) = apply { projectName = value }

        fun scriptContent(value: String?) = apply { scriptContent = value }

        fun appendScriptContent(value: String) = apply {
            when (scriptContent) {
                null -> scriptContent = "$value\n"
                else -> scriptContent += "$value\n"
            }
        }

        fun assertionTimeout(value: Long?) = apply { assertionTimeout = value }

        fun build() = PerformanceTestScript(projectName, scriptContent, assertionTimeout)
    }
}

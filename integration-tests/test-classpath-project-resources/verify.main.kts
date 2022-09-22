#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

__FILE__.init {
    runGradleTask("test").let { logs ->
        val classpathEntries = logs.lines()
            .filter { it.startsWith("test-classpath-project-resources: Test classpath entry:") }
        val testResourcesEntryIndex = classpathEntries
            .indexOfFirst { it.contains("/test-classpath-project-resources/build/resources/test") }
        val powerMockDependencyEntryIndex = classpathEntries
            .indexOfFirst { it.contains("powermock") }
        val firstIdeJarEntryIndex = classpathEntries
            .indexOfFirst { it.contains("/.gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/") }
        assert(testResourcesEntryIndex < powerMockDependencyEntryIndex)
        assert(testResourcesEntryIndex < firstIdeJarEntryIndex)
    }

}

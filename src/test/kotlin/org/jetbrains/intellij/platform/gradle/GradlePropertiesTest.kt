package org.jetbrains.intellij.platform.gradle

import org.gradle.api.provider.ProviderFactory
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import org.jetbrains.intellij.platform.gradle.utils.splitCommaSeparated
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GradlePropertiesTest {

    private lateinit var tempDir: Path
    private lateinit var providers: ProviderFactory
    private lateinit var defaultDir: Path

    private fun createTempDir(): Path = kotlin.io.path.createTempDirectory("gradle-properties-test")

    @BeforeTest
    fun setUp() {
        tempDir = createTempDir()
        val project = ProjectBuilder.builder().build()
        providers = project.providers
        defaultDir = tempDir.resolve("default")
    }

    @Test
    fun `resolveDir returns default directory when provider has no value`() {
        // Create a provider that has no value
        val emptyProvider = providers.provider { "" }

        val result = emptyProvider.resolvePath().orElse(defaultDir).get()

        assertEquals(defaultDir.toAbsolutePath(), result)
    }

    @Test
    fun `resolveDir returns default directory when provider value is empty`() {
        // Create a provider with an empty string
        val emptyProvider = providers.provider { "" }

        val result = emptyProvider.resolvePath().orElse(defaultDir).get()

        assertEquals(defaultDir.toAbsolutePath(), result)
    }

    @Test
    fun `resolveDir returns default directory when provider value is blank`() {
        // Create a provider with a blank string
        val blankProvider = providers.provider { "   " }

        val result = blankProvider.resolvePath().orElse(defaultDir).get()

        assertEquals(defaultDir.toAbsolutePath(), result)
    }

    @Test
    fun `resolveDir expands tilde to user home directory`() {
        // Create a provider with a tilde path
        val tildeProvider = providers.provider { "~/.intellijPlatform/cache" }

        val result = tildeProvider.resolvePath().orElse(defaultDir).get()
        assertNotNull(result)
        assertEquals(result.safePathString, Path.of(System.getProperty("user.home") + "/.intellijPlatform/cache").safePathString)
    }

    @Test
    fun `resolveDir uses custom absolute path when provider has value`() {
        val customPath = tempDir.resolve("custom").pathString
        // Create a provider with a custom path
        val customProvider = providers.provider { customPath }

        val result = customProvider.resolvePath().orElse(defaultDir).get()
        assertNotNull(result)

        assertEquals(tempDir.resolve("custom").toAbsolutePath().safePathString, result.safePathString)
    }

    @Test
    fun `resolveDir creates directories if they don't exist`() {
        val customPath = tempDir.resolve("deep/nested/path").pathString
        // Create a provider with a nested path
        val nestedProvider = providers.provider { customPath }

        val result = nestedProvider.resolvePath().orElse(defaultDir).get()
        assertNotNull(result)

        assertEquals(tempDir.resolve("deep/nested/path").safePathString, result.safePathString)
    }

    @Test
    fun `resolveDir handles tilde in relative path`() {
        // Create a provider with tilde in a relative path
        val tildeRelativeProvider = providers.provider { "~/my-project/.cache/intellij" }

        val result = tildeRelativeProvider.resolvePath().orElse(defaultDir).get()
        assertNotNull(result)

        assertEquals(result.safePathString, Path.of(System.getProperty("user.home") + "/my-project/.cache/intellij").safePathString)
    }

    @Test
    fun `testIde bundled plugins classpath excludes property has openRewrite default`() {
        val property = GradleProperties.TestIdeBundledPluginsClasspathExcludes

        assertEquals("org.jetbrains.intellij.platform.testIdeBundledPluginsClasspathExcludes", property.toString())
        assertEquals("com.intellij.openRewrite,com.intellij.ja,com.intellij.ko,com.intellij.zh,org.jetbrains.plugins.vue", property.defaultValue)
        assertEquals(listOf("com.intellij.openRewrite", "com.intellij.ja", "com.intellij.ko", "com.intellij.zh", "org.jetbrains.plugins.vue"), providers[property].get().splitCommaSeparated())
    }

    @Test
    fun `default sandbox exclusions property is enabled by default`() {
        val property = GradleProperties.UseDefaultSandboxExclusions

        assertEquals("org.jetbrains.intellij.platform.useDefaultSandboxExclusions", property.toString())
        assertEquals(true, property.defaultValue)
        assertEquals(true, providers[property].get())
    }

    @Test
    fun `splitCommaSeparated trims values and skips empty entries`() {
        val provider = providers.provider { " com.intellij.foo,com.intellij.bar, , com.intellij.baz " }

        assertEquals(
            listOf("com.intellij.foo", "com.intellij.bar", "com.intellij.baz"),
            provider.get().splitCommaSeparated(),
        )
    }

    @Test
    fun `splitCommaSeparated returns empty list for blank value`() {
        val provider = providers.provider { "   " }

        assertEquals(emptyList(), provider.get().splitCommaSeparated())
    }
}

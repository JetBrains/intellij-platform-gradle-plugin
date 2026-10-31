import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LoadedPluginsTest : BasePlatformTestCase() {

    fun testExpectedPluginsLoaded() {
        val expectedPlugins = System.getProperty("expectedPlugins").split(',').filter(String::isNotBlank) +
            "org.jetbrains.plugins.integration-tests.test-ide-bundled-plugins-classpath"
        val missingPlugins = expectedPlugins.filter {
            val descriptor = PluginManagerCore.getPlugin(PluginId.getId(it))
            descriptor == null || !descriptor.isEnabled || descriptor.pluginClassLoader == null
        }

        assertTrue("Expected plugins not loaded: $missingPlugins", missingPlugins.isEmpty())
    }
}

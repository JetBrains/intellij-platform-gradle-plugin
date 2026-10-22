import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LoadedPluginsTest : BasePlatformTestCase() {

    fun testExpectedPluginsLoaded() {
        val expectedPlugins = System.getProperty("expectedPlugins").split(',').filter(String::isNotBlank)
        val missingPlugins = expectedPlugins.filterNot { PluginManagerCore.isLoaded(PluginId.getId(it)) }

        assertTrue("Expected plugins not loaded: $missingPlugins", missingPlugins.isEmpty())
    }
}

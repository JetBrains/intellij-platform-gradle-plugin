import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.plugins.regressions.YamlSupportService;

/**
 * The optional YAML plugin dependency is loaded only when the YAML plugin is available, e.g., not excluded or disabled.
 *
 * https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/115
 * https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1887
 */
public class OptionalDependencyTest extends BasePlatformTestCase {

    public void testOptionalDependency() {
        boolean yamlExpected = Boolean.parseBoolean(System.getProperty("yamlExpected", "true"));

        assertEquals("YAML plugin loaded", yamlExpected, SmokeTest.isLoaded("org.jetbrains.plugins.yaml"));
        assertEquals(
            "Optional YAML support registered",
            yamlExpected,
            ApplicationManager.getApplication().getService(YamlSupportService.class) != null
        );
    }
}

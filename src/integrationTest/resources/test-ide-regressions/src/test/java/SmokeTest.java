import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the test environment against the expectations passed with system properties by the integration test.
 */
public class SmokeTest extends BasePlatformTestCase {

    static final String TESTED_PLUGIN_ID = "org.jetbrains.plugins.integration-tests.test-ide-regressions";

    static List<String> listProperty(String name) {
        List<String> result = new ArrayList<>();
        for (String item : System.getProperty(name, "").split(",")) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
    }

    static boolean isLoaded(String id) {
        IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(PluginId.getId(id));
        return descriptor != null && descriptor.isEnabled() && descriptor.getPluginClassLoader() != null;
    }

    // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1997
    public void testIdeaHomePath() throws Exception {
        String ideaHomePath = System.getProperty("idea.home.path");
        assertNotNull("Expected the 'idea.home.path' system property to be set", ideaHomePath);

        Path home = Path.of(ideaHomePath);
        assertTrue(
            "Expected product-info.json in: " + home,
            Files.exists(home.resolve("product-info.json")) || Files.exists(home.resolve("Resources/product-info.json"))
        );
        assertTrue("Expected PathManager home path to be: " + home, Files.isSameFile(home, Path.of(PathManager.getHomePath())));
    }

    // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2015
    public void testExpectedBuild() {
        String expectedBuild = System.getProperty("expectedBuild", "");
        if (!expectedBuild.isEmpty()) {
            assertEquals(expectedBuild, ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode());
        }
    }

    public void testTestedPluginLoaded() {
        assertTrue("Expected the tested plugin to be loaded", isLoaded(TESTED_PLUGIN_ID));
    }

    // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2137
    public void testSvgIconLoaded() {
        assertTrue(AllIcons.Actions.Close.getIconWidth() > 0);
    }

    public void testExpectedPluginsLoaded() {
        List<String> notLoaded = new ArrayList<>();
        for (String id : listProperty("expectedPlugins")) {
            if (!isLoaded(id)) {
                notLoaded.add(id);
            }
        }
        assertTrue("Expected plugins not loaded: " + notLoaded, notLoaded.isEmpty());
    }

    public void testUnexpectedPluginsNotLoaded() {
        List<String> loaded = new ArrayList<>();
        for (String id : listProperty("unexpectedPlugins")) {
            if (isLoaded(id)) {
                loaded.add(id);
            }
        }
        assertTrue("Unexpected plugins loaded: " + loaded, loaded.isEmpty());
    }

    // File types provided by bundled plugins are available without declaring the plugins
    public void testExpectedFileTypes() {
        List<String> mismatches = new ArrayList<>();
        for (String entry : listProperty("expectedFileTypes")) {
            String[] parts = entry.split(":", 2);
            String extension = parts[0];
            String expected = parts[1];

            String registered = FileTypeManager.getInstance().getFileTypeByExtension(extension).getName();
            PsiFile file = myFixture.configureByText("test." + extension, "");
            String configured = file.getFileType().getName();

            if (!expected.equals(registered) || !expected.equals(configured)) {
                mismatches.add(extension + " -> " + registered + "/" + configured + " (expected: " + expected + ")");
            }
        }
        assertTrue("Unexpected file types: " + mismatches, mismatches.isEmpty());
    }

    // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1710
    public void testExpectedServices() {
        List<String> missing = new ArrayList<>();
        for (String className : listProperty("expectedServices")) {
            try {
                Class<?> serviceClass = Class.forName(className);
                Object service = getProject().getService(serviceClass);
                if (service == null) {
                    service = ApplicationManager.getApplication().getService(serviceClass);
                }
                if (service == null) {
                    missing.add(className);
                }
            } catch (ClassNotFoundException e) {
                missing.add(className + " (class not found)");
            }
        }
        assertTrue("Expected services not available: " + missing, missing.isEmpty());
    }
}

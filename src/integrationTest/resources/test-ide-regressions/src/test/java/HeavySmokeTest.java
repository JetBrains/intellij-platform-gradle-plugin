import com.intellij.testFramework.HeavyPlatformTestCase;

/**
 * Opens a project, which runs the startup activities and initializes the project services of all loaded plugins.
 * Errors logged meanwhile fail the test, as the IntelliJ Platform Gradle Plugin enables `intellij.testFramework.rethrow.logged.errors`.
 */
public class HeavySmokeTest extends HeavyPlatformTestCase {

    public void testProjectOpened() {
        assertTrue(getProject().isOpen());
        assertFalse(getProject().isDisposed());
        assertTrue("Expected the tested plugin to be loaded", SmokeTest.isLoaded(SmokeTest.TESTED_PLUGIN_ID));
    }
}

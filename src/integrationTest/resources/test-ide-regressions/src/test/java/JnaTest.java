import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * JNA, which the IDE uses, e.g., for the native macOS integration, is available in tests.
 * The JNA loader is accessed reflectively, as its module isn't on the compile classpath of all IntelliJ Platform versions.
 *
 * https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1815
 */
public class JnaTest extends BasePlatformTestCase {

    public void testJnaLoaded() throws Exception {
        Object loaded = Class.forName("com.intellij.jna.JnaLoader").getMethod("isLoaded").invoke(null);
        assertEquals("Expected JNA to be loaded", Boolean.TRUE, loaded);
    }
}

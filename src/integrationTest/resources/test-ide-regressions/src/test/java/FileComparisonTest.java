import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.File;
import java.nio.file.Files;

/**
 * The file comparison error requires classes of `idea_rt.jar`, which moved from the IntelliJ Platform to the Java plugin in 2026.3.
 *
 * https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1632
 * https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2273
 */
public class FileComparisonTest extends BasePlatformTestCase {

    public void testFileComparisonFailure() throws Exception {
        File expectedFile = File.createTempFile("expected", ".txt");
        expectedFile.deleteOnExit();
        Files.writeString(expectedFile.toPath(), "expected");

        Throwable error = null;
        try {
            assertSameLinesWithFile(expectedFile.getPath(), "actual");
        } catch (Throwable t) {
            error = t;
        }

        assertNotNull("Expected the file comparison to fail", error);
        assertTrue("Expected an assertion error, got: " + error, error instanceof AssertionError);
        assertTrue("Expected a file comparison error, got: " + error.getClass().getName(), error.getClass().getName().contains("FileComparison"));
    }
}

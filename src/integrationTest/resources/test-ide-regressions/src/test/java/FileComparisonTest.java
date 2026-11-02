import com.intellij.testFramework.UsefulTestCase;
import junit.framework.TestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * The file comparison error requires classes of `idea_rt.jar`, which moved from the IntelliJ Platform to the Java plugin in 2026.3.
 *
 * https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1632
 * https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2273
 */
public class FileComparisonTest extends TestCase {

    public void testFileComparisonFailure() throws Exception {
        Path expectedFile = Files.createTempFile("expected", ".txt");
        try {
            Files.writeString(expectedFile, "expected");

            Throwable error = null;
            try {
                UsefulTestCase.assertSameLinesWithFile(expectedFile.toString(), "actual");
            } catch (Throwable t) {
                error = t;
            }

            assertNotNull("Expected the file comparison to fail", error);
            assertTrue("Expected an assertion error, got: " + error, error instanceof AssertionError);
            assertTrue("Expected a file comparison error, got: " + error.getClass().getName(), error.getClass().getName().contains("FileComparison"));

            if (Boolean.getBoolean("fileComparisonDataExpected")) {
                assertEquals("com.intellij.platform.testFramework.core.FileComparisonFailedError", error.getClass().getName());
                assertTrue("Expected the error to implement FileComparisonData", Arrays.stream(error.getClass().getInterfaces())
                    .anyMatch(type -> type.getName().equals("com.intellij.rt.execution.junit.FileComparisonData")));
            }
        } finally {
            Files.deleteIfExists(expectedFile);
        }
    }
}

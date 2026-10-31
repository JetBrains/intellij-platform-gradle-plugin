import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class FileComparisonFailedErrorTest : BasePlatformTestCase() {

    // FileComparisonFailedError implements FileComparisonData from idea_rt.jar, which is provided by the Java plugin since 2026.3
    fun testFileComparisonFailedErrorThrown() {
        val expectedFile = File.createTempFile("expected", ".txt").apply {
            deleteOnExit()
            writeText("expected")
        }

        val error = try {
            assertSameLinesWithFile(expectedFile.path, "actual")
            null
        } catch (e: AssertionError) {
            e
        }

        assertNotNull("Expected the file comparison to fail", error)
        assertEquals("com.intellij.platform.testFramework.core.FileComparisonFailedError", error!!.javaClass.name)
        assertTrue(error.javaClass.interfaces.any { it.name == "com.intellij.rt.execution.junit.FileComparisonData" })
    }
}

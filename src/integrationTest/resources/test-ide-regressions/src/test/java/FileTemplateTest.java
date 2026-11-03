import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * The internal file template of the tested plugin is found exactly once.
 *
 * https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/562
 * https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1640
 */
public class FileTemplateTest extends BasePlatformTestCase {

    public void testInternalTemplateOfTestedPlugin() throws Exception {
        String phase = System.getenv("WINDOWS_DIAGNOSTIC_PHASE");
        if (phase != null) {
            var plugin = PluginManagerCore.getPlugin(PluginId.getId("com.intellij.code.provenance"));
            assertNotNull("Code provenance remains present", plugin);
            assertTrue("Code provenance remains enabled", plugin.isEnabled());
            assertNotNull("Code provenance remains loaded", plugin.getPluginClassLoader());
            boolean analytics = Registry.is("code.provenance.chatter.analytics.enabled");
            Path evidence = Path.of(System.getenv("WINDOWS_DIAGNOSTIC_EVIDENCE"), phase);
            Files.createDirectories(evidence);
            Files.writeString(evidence.resolve("FileTemplateTest-runtime.txt"),
                    "phase=" + phase + "\nroot=" + FileUtilRt.getTempDirectory() +
                    "\nprojectBase=" + getProject().getBasePath() + "\njvmPid=" + ProcessHandle.current().pid() +
                    "\ncodeProvenanceLoaded=true\nchatterAnalytics=" + analytics +
                    "\njavaVersion=" + System.getProperty("java.version") + "\n");
            assertEquals("Startup analytics gate", !"candidate".equals(phase), analytics);
        }

        FileTemplateManager manager = FileTemplateManager.getInstance(getProject());
        FileTemplate template = manager.getInternalTemplate("RegressionTemplate");

        assertNotNull(template);
        assertEquals("Regression template for ${NAME}", template.getText().trim());

        int count = 0;
        for (FileTemplate internalTemplate : manager.getInternalTemplates()) {
            if ("RegressionTemplate".equals(internalTemplate.getName())) {
                count++;
            }
        }
        assertEquals(1, count);
    }

    @Override
    protected void tearDown() throws Exception {
        String root = FileUtilRt.getTempDirectory();
        String projectBase = getProject().getBasePath();
        try {
            super.tearDown();
        } catch (Throwable failure) {
            String capture = System.getenv("WINDOWS_DIAGNOSTIC_CAPTURE");
            if (capture != null) {
                try {
                    Path evidence = Path.of(System.getenv("WINDOWS_DIAGNOSTIC_EVIDENCE"),
                            System.getenv("WINDOWS_DIAGNOSTIC_PHASE"));
                    Files.createDirectories(evidence);
                    StringWriter trace = new StringWriter();
                    failure.printStackTrace(new PrintWriter(trace));
                    Files.writeString(evidence.resolve("FileTemplateTest-teardown.txt"), trace.toString());
                    Process process = new ProcessBuilder("pwsh.exe", "-NoProfile", "-NonInteractive",
                            "-File", capture, "-Root", root, "-ProjectBase", String.valueOf(projectBase),
                            "-TestJvmPid", Long.toString(ProcessHandle.current().pid()))
                            .redirectErrorStream(true)
                            .redirectOutput(evidence.resolve("handle-capture.log").toFile())
                            .start();
                    if (!process.waitFor(25, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        throw new IllegalStateException("Directory-owner capture exceeded 25 seconds");
                    }
                    if (process.exitValue() != 0) {
                        throw new IllegalStateException("Directory-owner capture failed: " + process.exitValue());
                    }
                } catch (Throwable captureFailure) {
                    failure.addSuppressed(captureFailure);
                }
            }
            throw failure;
        }
    }
}

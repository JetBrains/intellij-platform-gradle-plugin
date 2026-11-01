import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * The internal file template of the tested plugin is found exactly once.
 *
 * https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/562
 * https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1640
 */
public class FileTemplateTest extends BasePlatformTestCase {

    public void testInternalTemplateOfTestedPlugin() {
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
}

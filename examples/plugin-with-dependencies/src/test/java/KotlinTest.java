import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.kotlin.psi.KtFile;

public class KotlinTest extends LightPlatformCodeInsightFixtureTestCase {
    public void testCreatingKotlinFile() {
        myFixture.configureByText("test.kt", "");
        assertInstanceOf(myFixture.getFile(), KtFile.class);
    }
}

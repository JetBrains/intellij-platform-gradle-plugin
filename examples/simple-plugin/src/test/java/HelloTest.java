import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class HelloTest extends BasePlatformTestCase {
    public void testHello() {
        DemoComponent component = ApplicationManager.getApplication().getComponent(DemoComponent.class);
        assertInstanceOf(component.getService(), DemoService.class);
    }
}

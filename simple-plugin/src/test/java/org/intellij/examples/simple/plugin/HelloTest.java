package org.intellij.examples.simple.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.examples.simple.plugin.DemoComponent;
import org.intellij.examples.simple.plugin.DemoService;

public class HelloTest extends BasePlatformTestCase {

    public void testHello() {
        DemoComponent component = ApplicationManager.getApplication().getComponent(DemoComponent.class);
        assertInstanceOf(component.getService(), DemoService.class);
    }
}

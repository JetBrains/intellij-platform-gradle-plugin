package org.intellij.examples.dependencies.plugin;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.kotlin.psi.KtFile;

public class KotlinTest extends BasePlatformTestCase {

    public void testCreatingKotlinFile() {
        myFixture.configureByText("test.kt", "");
        assertInstanceOf(myFixture.getFile(), KtFile.class);
    }
}

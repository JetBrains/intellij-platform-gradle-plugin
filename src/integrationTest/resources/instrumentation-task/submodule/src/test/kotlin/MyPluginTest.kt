import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MyPluginTest : BasePlatformTestCase() {

    fun testService() {
        val myProjectService = project.service<MyProjectService>()

        assertEquals(4, myProjectService.getRandomNumber())
    }
}

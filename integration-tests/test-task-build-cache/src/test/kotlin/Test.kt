import com.intellij.testFramework.LightJavaCodeInsightTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TestClass : LightJavaCodeInsightTestCase() {
    @Test
    fun aTest() {
        println("test method ran for $project")
    }
}
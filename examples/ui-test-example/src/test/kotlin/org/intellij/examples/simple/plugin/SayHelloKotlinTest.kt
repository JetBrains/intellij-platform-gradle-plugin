package org.intellij.examples.simple.plugin

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.search.locators.byXpath
import org.intellij.examples.simple.plugin.pages.WelcomeFrame
import org.intellij.examples.simple.plugin.utils.StepsLogger
import org.intellij.examples.simple.plugin.utils.uiTest
import org.junit.jupiter.api.Test

class SayHelloKotlinTest {
    init {
        StepsLogger.init()
    }

    @Test
    fun checkHelloMessage() = uiTest {
        find(WelcomeFrame::class.java).text("Say Hello").click()

        val helloDialog = find(HelloWorldDialog::class.java)

        assert(helloDialog.textPane.hasText("Hello World!"))
        helloDialog.ok.click()
    }

    @DefaultXpath("title Hello", "//div[@title='Hello' and @class='MyDialog']")
    class HelloWorldDialog(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : ContainerFixture(remoteRobot, remoteComponent) {
        val textPane: ComponentFixture
            get() = find(byXpath("//div[@class='JTextPane']"))
        val ok: ComponentFixture
            get() = find(byXpath("//div[@class='JButton' and @text='OK']"))
    }
}
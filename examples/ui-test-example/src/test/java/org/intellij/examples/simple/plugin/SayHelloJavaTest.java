package org.intellij.examples.simple.plugin;

import com.jetbrains.test.RemoteRobot;
import org.intellij.examples.simple.plugin.pages.ActionLinkFixture;
import org.intellij.examples.simple.plugin.pages.WelcomeFrame;
import org.intellij.examples.simple.plugin.utils.StepsLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.jetbrains.test.search.locators.LocatorKt.byXpath;

public class SayHelloJavaTest {
    @BeforeAll
    public static void initLogging() {
        StepsLogger.init();
    }

    @Test
    void checkSayHello() {
        final RemoteRobot remoteRobot = new RemoteRobot("http://127.0.0.1:8082");
        remoteRobot.find(WelcomeFrame.class).text("Say Hello").click();
        final SayHelloKotlinTest.HelloWorldDialog helloDialog = remoteRobot.find(SayHelloKotlinTest.HelloWorldDialog.class);
        assert (helloDialog.getTextPane().hasText("Hello World!"));
        helloDialog.getOk().click();
    }

    @Test
    void checkActionLink() {
        final RemoteRobot remoteRobot = new RemoteRobot("http://127.0.0.1:8082");
        final ActionLinkFixture createNewProject = remoteRobot.find(ActionLinkFixture.class, byXpath("//div[@class='ActionLink' and @text='Create New Project']"));
        createNewProject.click();
    }
}

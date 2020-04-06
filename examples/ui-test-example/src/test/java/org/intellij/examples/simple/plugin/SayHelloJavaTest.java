package org.intellij.examples.simple.plugin;

import com.jetbrains.test.RemoteRobot;
import org.assertj.swing.core.MouseButton;
import org.intellij.examples.simple.plugin.pages.WelcomeFrame;
import org.intellij.examples.simple.plugin.utils.StepsLogger;
import org.junit.BeforeClass;
import org.junit.jupiter.api.Test;

public class SayHelloJavaTest {
    @BeforeClass
    public static void initLogging() {
        StepsLogger.INSTANCE.init();
    }

    @Test
    void checkSayHello() {
        final RemoteRobot remoteRobot = new RemoteRobot("http://127.0.0.1:8082");
        remoteRobot.find(WelcomeFrame.class).text("Say Hello").click(MouseButton.LEFT_BUTTON);
        final SayHelloTest.HelloWorldDialog helloDialog = remoteRobot.find(SayHelloTest.HelloWorldDialog.class);
        assert (helloDialog.getTextPane().hasText("Hello World!"));
        helloDialog.getOk().click();
    }
}

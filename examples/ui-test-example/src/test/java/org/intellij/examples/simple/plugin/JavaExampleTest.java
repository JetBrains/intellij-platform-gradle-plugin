package org.intellij.examples.simple.plugin;

import com.jetbrains.test.RemoteRobot;
import com.jetbrains.test.stepsProcessing.StepLogger;
import com.jetbrains.test.stepsProcessing.StepWorker;
import kotlin.Unit;
import org.assertj.swing.core.MouseButton;
import org.intellij.examples.simple.plugin.SimplePluginTest.HelloWorldDialog;
import org.intellij.examples.simple.plugin.pages.WelcomeFrame;
import org.junit.jupiter.api.Test;

import static com.jetbrains.test.stepsProcessing.StepWorkerKt.step;
import static org.intellij.examples.simple.plugin.pages.JDialogFixture.*;

public class JavaExampleTest {
    public JavaExampleTest() {
        StepWorker.registerProcessor(new StepLogger());
    }

    @Test
    void javaTest() {
        RemoteRobot remoteRobot = new RemoteRobot("http://127.0.0.1:8082");
        step("Click Say Hello", () -> {
            remoteRobot.find(WelcomeFrame.class).text("Say Hello").click(MouseButton.LEFT_BUTTON);
            return Unit.INSTANCE;
        });
        HelloWorldDialog helloDialog = remoteRobot.find(HelloWorldDialog.class);
        assert (helloDialog.getTextPane().hasText("Hello World!"));
        helloDialog.getOk().click();
    }
}

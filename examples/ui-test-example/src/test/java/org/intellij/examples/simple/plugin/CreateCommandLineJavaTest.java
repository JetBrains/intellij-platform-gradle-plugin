package org.intellij.examples.simple.plugin;

import com.jetbrains.test.RemoteRobot;
import com.jetbrains.test.fixtures.ContainerFixture;
import com.jetbrains.test.utils.Keyboard;
import org.assertj.swing.core.MouseButton;
import org.intellij.examples.simple.plugin.pages.IdeaFrame;
import org.intellij.examples.simple.plugin.steps.JavaExampleSteps;
import org.intellij.examples.simple.plugin.utils.StepsLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.time.Duration;

import static com.jetbrains.test.search.locators.LocatorKt.byXpath;
import static com.jetbrains.test.stepsProcessing.StepWorkerKt.step;
import static com.jetbrains.test.utils.KeyboardUtilsKt.autocomplete;
import static com.jetbrains.test.utils.RepeatUtilsKt.waitFor;
import static org.intellij.examples.simple.plugin.pages.ActionMenuFixtureKt.actionMenu;
import static org.intellij.examples.simple.plugin.pages.ActionMenuFixtureKt.actionMenuItem;
import static org.intellij.examples.simple.plugin.pages.EditorKt.editor;

public class CreateCommandLineJavaTest {

    private final RemoteRobot remoteRobot = new RemoteRobot("http://127.0.0.1:8082");
    private final JavaExampleSteps steps = new JavaExampleSteps(remoteRobot);
    private final Keyboard keyboard = new Keyboard(remoteRobot);

    @BeforeAll
    public static void initLogging() {
        StepsLogger.init();
    }

    @AfterEach
    public void closeProject() {
        step("Close the project", () -> {
            actionMenu(remoteRobot, "File").click();
            actionMenuItem(remoteRobot, "Close Project").click();
        });
    }

    @Test
    void createCommandLineProject() {
        steps.createNewCommandLineProject();
        steps.closeTipOfTheDay();

        final IdeaFrame idea = remoteRobot.find(IdeaFrame.class);

        step("Create New Kotlin file", () -> {
            final ContainerFixture projectView = idea.getProjectViewTree();

            projectView.text(idea.getProjectName()).doubleClick();
            waitFor(() -> projectView.hasText("src"));
            projectView.findText("src").click(MouseButton.RIGHT_BUTTON);
            actionMenu(remoteRobot, "New").click();
            actionMenuItem(remoteRobot, "Kotlin File/Class").click();
            keyboard.enterText("App");
            keyboard.enter();
        });

        final ContainerFixture editor = editor(idea, "App.kt");

        step("Write a code", () -> {
            autocomplete(remoteRobot, "main");
            autocomplete(remoteRobot, "sout");
            keyboard.enterText("\"");
            keyboard.enterText("Hello from UI test");
        });

        step("Launch the application", () -> {
            editor.findText("main").click();
            keyboard.hotKey(KeyEvent.VK_ALT, KeyEvent.VK_ENTER);
            keyboard.enter();
        });

        assert (idea.find(
                ContainerFixture.class,
                byXpath("//div[@class='ConsoleViewImpl']"),
                Duration.ofMinutes(1)
        ).hasText("Hello from UI test"));
    }
}

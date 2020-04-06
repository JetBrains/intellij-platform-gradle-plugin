package org.intellij.examples.simple.plugin;

import com.jetbrains.test.RemoteRobot;
import com.jetbrains.test.fixtures.ContainerFixture;
import com.jetbrains.test.search.locators.Locator;
import com.jetbrains.test.utils.Keyboard;
import org.assertj.swing.core.MouseButton;
import org.intellij.examples.simple.plugin.pages.IdeaFrame;
import org.intellij.examples.simple.plugin.steps.JavaExampleSteps;
import org.intellij.examples.simple.plugin.utils.StepsLogger;
import org.junit.BeforeClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.time.Duration;

import static com.jetbrains.test.search.locators.LocatorKt.byXpath;
import static com.jetbrains.test.utils.KeyboardUtilsKt.autocomplete;
import static com.jetbrains.test.utils.RepeatUtilsKt.waitFor;
import static org.intellij.examples.simple.plugin.pages.ActionMenuFixtureKt.actionMenu;
import static org.intellij.examples.simple.plugin.pages.ActionMenuFixtureKt.actionMenuItem;
import static org.intellij.examples.simple.plugin.pages.EditorKt.editor;

public class CreateCommandLineJavaTest {

    private final RemoteRobot remoteRobot = new RemoteRobot("http://127.0.0.1:8082");
    private final JavaExampleSteps steps = new JavaExampleSteps(remoteRobot);
    private final Keyboard keyboard = new Keyboard(remoteRobot);

    @BeforeClass
    public static void initLogging() {
        StepsLogger.INSTANCE.init();
    }

    @AfterEach
    public void closeProject() {
        actionMenu(remoteRobot, "File").click();
        actionMenuItem(remoteRobot, "Close Project").click();
    }

    @Test
    void createCommandLineProject() {
        steps.createNewCommandLineProject();
        steps.closeTipOfTheDay();

        final IdeaFrame idea = remoteRobot.find(IdeaFrame.class);
        final ContainerFixture projectView = idea.getProjectViewTree();
        projectView.text(idea.getProjectName()).doubleClick();
        waitFor(() -> projectView.hasText("src"));
        projectView.text("src").click(MouseButton.RIGHT_BUTTON);
        actionMenu(remoteRobot, "New").click();
        actionMenuItem(remoteRobot, "Kotlin File/Class").click();
        keyboard.enterText("App");
        keyboard.enter();

        final ContainerFixture editor = editor(idea, "App.kt");
        autocomplete(remoteRobot, "main");
        autocomplete(remoteRobot, "sout");
        keyboard.enterText("\"");
        keyboard.enterText("Hello from UI test");
        editor.text("main").click();
        keyboard.hotKey(KeyEvent.VK_ALT, KeyEvent.VK_ENTER);
        keyboard.enter();

        final Locator consoleLocator = byXpath("//div[@class='ConsoleViewImpl']");
        waitFor(Duration.ofMinutes(1), () -> idea.findAll(ContainerFixture.class, consoleLocator).size() > 0);

        assert (idea.find(ContainerFixture.class, consoleLocator).hasText("Hello from UI test"));
    }
}

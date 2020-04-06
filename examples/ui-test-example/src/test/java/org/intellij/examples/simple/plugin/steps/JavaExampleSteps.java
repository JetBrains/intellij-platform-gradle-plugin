package org.intellij.examples.simple.plugin.steps;

import com.jetbrains.test.RemoteRobot;
import com.jetbrains.test.fixtures.ComponentFixture;
import com.jetbrains.test.utils.Keyboard;
import kotlin.Unit;
import org.intellij.examples.simple.plugin.pages.DialogFixture;
import org.intellij.examples.simple.plugin.pages.IdeaFrame;
import org.intellij.examples.simple.plugin.pages.WelcomeFrame;

import java.awt.event.KeyEvent;
import java.time.Duration;

import static com.jetbrains.test.search.locators.LocatorKt.byXpath;
import static org.intellij.examples.simple.plugin.pages.DialogFixture.byTitle;

public class JavaExampleSteps {
    final private RemoteRobot remoteRobot;
    final private Keyboard keyboard;

    public JavaExampleSteps(RemoteRobot remoteRobot) {
        this.remoteRobot = remoteRobot;
        this.keyboard = new Keyboard(remoteRobot);
    }

    public void createNewCommandLineProject() {
        final WelcomeFrame welcomeFrame = remoteRobot.find(WelcomeFrame.class);
        welcomeFrame.getCreateNewProjectLink().click();

        final DialogFixture newProjectDialog = welcomeFrame.find(DialogFixture.class, DialogFixture.byTitle("New Project"), Duration.ofSeconds(20));
        newProjectDialog.text("Java").click();
        newProjectDialog.find(ComponentFixture.class,
                byXpath("FrameworksTree", "//div[@class='FrameworksTree']"))
                .text("Kotlin/JVM")
                .click();
        keyboard.key(KeyEvent.VK_SPACE, Duration.ZERO);
        newProjectDialog.button("Next").click();
        newProjectDialog.button("Finish").click();
    }

    public void closeTipOfTheDay() {
        final IdeaFrame idea = remoteRobot.find(IdeaFrame.class);
        idea.dumbAware(() -> {
            try {
                idea.find(DialogFixture.class, byTitle("Tip of the Day")).button("Close").click();
            } catch (Throwable ignore) {
            }
            return Unit.INSTANCE;
        });
    }
}

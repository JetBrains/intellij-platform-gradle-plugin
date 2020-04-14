package org.intellij.examples.simple.plugin.steps;

import com.intellij.remoterobot.RemoteRobot;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.utils.Keyboard;
import kotlin.Unit;
import org.intellij.examples.simple.plugin.pages.DialogFixture;
import org.intellij.examples.simple.plugin.pages.IdeaFrame;
import org.intellij.examples.simple.plugin.pages.WelcomeFrameFixture;

import java.awt.event.KeyEvent;
import java.time.Duration;

import static com.intellij.remoterobot.search.locators.Locators.byXpath;
import static com.intellij.remoterobot.stepsProcessing.StepWorkerKt.step;
import static org.intellij.examples.simple.plugin.pages.DialogFixture.byTitle;

public class JavaExampleSteps {
    final private RemoteRobot remoteRobot;
    final private Keyboard keyboard;

    public JavaExampleSteps(RemoteRobot remoteRobot) {
        this.remoteRobot = remoteRobot;
        this.keyboard = new Keyboard(remoteRobot);
    }

    public void createNewCommandLineProject() {
        step("Create New Command Line Project", () -> {
            final WelcomeFrameFixture welcomeFrame = remoteRobot.find(WelcomeFrameFixture.class);
            welcomeFrame.createNewProjectLink().click();

            final DialogFixture newProjectDialog = welcomeFrame.find(DialogFixture.class, DialogFixture.byTitle("New Project"), Duration.ofSeconds(20));
            newProjectDialog.findText("Java").click();
            newProjectDialog.find(ComponentFixture.class,
                    byXpath("FrameworksTree", "//div[@class='FrameworksTree']"))
                    .findText("Kotlin/JVM")
                    .click();
            keyboard.key(KeyEvent.VK_SPACE, Duration.ZERO);
            newProjectDialog.button("Next").click();
            newProjectDialog.button("Finish").click();
        });
    }

    public void closeTipOfTheDay() {
        step("Close Tip of the Day if it appears", () -> {
            final IdeaFrame idea = remoteRobot.find(IdeaFrame.class);
            idea.dumbAware(() -> {
                try {
                    idea.find(DialogFixture.class, byTitle("Tip of the Day")).button("Close").click();
                } catch (Throwable ignore) {
                }
                return Unit.INSTANCE;
            });
        });
    }
}

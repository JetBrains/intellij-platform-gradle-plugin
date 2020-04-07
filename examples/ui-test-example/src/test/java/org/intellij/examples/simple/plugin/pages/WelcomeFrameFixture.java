package org.intellij.examples.simple.plugin.pages;

import com.jetbrains.test.RemoteRobot;
import com.jetbrains.test.data.RemoteComponent;
import com.jetbrains.test.fixtures.ComponentFixture;
import com.jetbrains.test.fixtures.ContainerFixture;
import com.jetbrains.test.fixtures.DefaultXpath;
import com.jetbrains.test.fixtures.FixtureName;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.test.search.locators.LocatorKt.byXpath;


@DefaultXpath(by = "FlatWelcomeFrame type", xpath = "//div[@class='FlatWelcomeFrame']")
@FixtureName(name = "Welcome Frame")
public class WelcomeFrameFixture extends ContainerFixture {
    public WelcomeFrameFixture(@NotNull RemoteRobot remoteRobot, @NotNull RemoteComponent remoteComponent) {
        super(remoteRobot, remoteComponent);
    }

    public ActionLinkFixture createNewProjectLink() {
        return find(ActionLinkFixture.class, byXpath("//div[@text='Create New Project' and @class='ActionLink']"));
    }

    public ComponentFixture importProjectLink() {
        return find(ComponentFixture.class, byXpath("//div[@text='Import Project' and @class='ActionLink']"));
    }
}

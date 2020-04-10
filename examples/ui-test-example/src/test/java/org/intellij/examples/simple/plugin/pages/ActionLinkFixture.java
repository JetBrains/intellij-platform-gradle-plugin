package org.intellij.examples.simple.plugin.pages;

import com.intellij.remoterobot.RemoteRobot;
import com.intellij.remoterobot.data.RemoteComponent;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.FixtureName;
import org.jetbrains.annotations.NotNull;


@FixtureName(name = "Action Link")
public class ActionLinkFixture extends ComponentFixture {
    public ActionLinkFixture(@NotNull RemoteRobot remoteRobot, @NotNull RemoteComponent remoteComponent) {
        super(remoteRobot, remoteComponent);
    }

    public void click() {
        execute("const offset = component.getHeight()/2;" +
                "robot.click(" +
                "component, " +
                "new java.awt.Point(offset, offset), " +
                "org.assertj.swing.core.MouseButton.LEFT_BUTTON, 1);"
        );
    }
}

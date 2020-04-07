package org.intellij.examples.simple.plugin.pages;

import com.jetbrains.test.RemoteRobot;
import com.jetbrains.test.data.RemoteComponent;
import com.jetbrains.test.fixtures.ComponentFixture;
import org.jetbrains.annotations.NotNull;


public class ActionLinkFixture extends ComponentFixture {
    public ActionLinkFixture(@NotNull RemoteRobot remoteRobot, @NotNull RemoteComponent remoteComponent) {
        super(remoteRobot, remoteComponent);
    }

    // click at the left side of ActionLink
    public void click() {
        execute("const offset = component.getHeight()/2;" +
                "robot.click(" +
                "component, " +
                "new java.awt.Point(offset, offset), " +
                "org.assertj.swing.core.MouseButton.LEFT_BUTTON, 1);"
        );
    }
}

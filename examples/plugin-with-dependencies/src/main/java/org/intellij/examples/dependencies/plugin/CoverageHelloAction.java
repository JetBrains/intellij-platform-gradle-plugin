package org.intellij.examples.dependencies.plugin;

import com.intellij.coverage.CoverageExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class CoverageHelloAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Messages.showInfoMessage("Hello Coverage Plugin!\n" +
                "I know about " + CoverageExecutor.EXECUTOR_ID, "Hello");
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setIcon(AllIcons.Ide.Notification.InfoEvents);
    }
}

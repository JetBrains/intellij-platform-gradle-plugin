package org.intellij.examples.composite.plugin;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.ui.Messages;
import org.intellij.examples.subplugin.MessageProvider;
import org.jetbrains.annotations.NotNull;

public class HelloAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        MessageProvider service = ServiceManager.getService(MessageProvider.class);
        Messages.showInfoMessage(service != null ? service.getMessage() : "Service is not found", "Hello");
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setIcon(AllIcons.General.NotificationInfo);
    }
}

package org.intellij.examples.clion.plugin;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class HelloAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        DialogWrapper dialogWrapper = new DialogWrapper(PlatformDataKeys.PROJECT.getData(e.getDataContext())) {
            {
                init();
            }

            @Nullable
            @Override
            protected JComponent createCenterPanel() {
                return new HelloForm().getPanel();
            }
        };
        dialogWrapper.showAndGet();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setIcon(AllIcons.Ide.Info_notifications);
    }
}

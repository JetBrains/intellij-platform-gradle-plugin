package org.intellij.examples.dependencies.plugin;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.kotlin.idea.KotlinLanguage;

public class KotlinHelloAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Messages.showInfoMessage("Hello Kotlin Plugin!\n" +
        "I know about " + KotlinLanguage.NAME, "Hello");
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setIcon(AllIcons.Ide.Info_notifications);
  }
}

package org.intellij.examples.simple.plugin;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;

public class HelloAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Messages.showInfoMessage("Hello World!", "Hello");
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setIcon(AllIcons.General.Information);
  }
}

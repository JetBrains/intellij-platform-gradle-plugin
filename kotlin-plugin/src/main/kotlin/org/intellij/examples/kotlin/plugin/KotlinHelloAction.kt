package org.intellij.examples.kotlin.plugin

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class KotlinHelloAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) = Util.sayHello()

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.icon = AllIcons.General.Information
    }
}

package org.intellij.examples.kotlin.plugin

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.intellij.examples.kotlin.Util
import org.jetbrains.annotations.NotNull

class KotlinHelloAction : AnAction() {
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        Util.sayHello()
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.icon = AllIcons.General.Information
    }
}
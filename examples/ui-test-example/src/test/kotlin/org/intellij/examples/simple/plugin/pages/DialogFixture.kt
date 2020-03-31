package org.intellij.examples.simple.plugin.pages

import com.jetbrains.test.RemoteRobot
import com.jetbrains.test.data.RemoteComponent
import com.jetbrains.test.data.componentAs
import com.jetbrains.test.fixtures.CommonContainerFixture
import com.jetbrains.test.fixtures.ContainerFixture
import com.jetbrains.test.fixtures.FixtureName
import com.jetbrains.test.search.locators.byLambda
import com.jetbrains.test.stepsProcessing.step
import java.time.Duration
import javax.swing.JDialog

fun ContainerFixture.dialog(
        title: String,
        timeout: Duration = Duration.ofSeconds(20),
        function: JDialogFixture.() -> Unit = {}): JDialogFixture = step("Search for dialog with title $title") {
    find<JDialogFixture>(JDialogFixture.byTitle(title), timeout).apply(function)
}

@FixtureName("Dialog")
class JDialogFixture(
        remoteRobot: RemoteRobot,
        remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {

    companion object {
        fun byTitle(title: String) = byLambda("title $title") {
            it is JDialog && it.isShowing && it.title == title
        }

        fun byType() = byLambda("JDialog") {
            it is JDialog && it.isShowing
        }

        fun byTitleContains(title: String) = byLambda("title contains $title") {
            it is JDialog && it.isShowing && it.title.contains(title, true)
        }

        fun byTitle(titleRegex: Regex) = byLambda("title match $titleRegex") {
            it is JDialog && it.isShowing && it.title.matches(titleRegex)
        }
    }

    val title
        get() = remoteRobot.retrieve(this) {
            componentAs<JDialog>().title ?: ""
        }
}
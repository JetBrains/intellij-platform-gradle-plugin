package org.intellij.examples.simple.plugin.pages

import com.jetbrains.test.RemoteRobot
import com.jetbrains.test.data.RemoteComponent
import com.jetbrains.test.data.componentAs
import com.jetbrains.test.fixtures.CommonContainerFixture
import com.jetbrains.test.fixtures.ContainerFixture
import com.jetbrains.test.fixtures.FixtureName
import com.jetbrains.test.search.locators.byXpath
import com.jetbrains.test.stepsProcessing.step
import java.time.Duration
import javax.swing.JDialog

fun ContainerFixture.dialog(
        title: String,
        timeout: Duration = Duration.ofSeconds(20),
        function: DialogFixture.() -> Unit = {}): DialogFixture = step("Search for dialog with title $title") {
    find<DialogFixture>(DialogFixture.byTitle(title), timeout).apply(function)
}

@FixtureName("Dialog")
class DialogFixture(
        remoteRobot: RemoteRobot,
        remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {

    companion object {
        @JvmStatic
        fun byTitle(title: String) = byXpath("title $title", "//div[@title='$title' and @class='MyDialog']")
    }

    val title
        get() = remoteRobot.retrieve(this) {
            componentAs<JDialog>().title ?: ""
        }
}
package org.intellij.examples.simple.plugin.pages

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.jetbrains.test.RemoteRobot
import com.jetbrains.test.data.RemoteComponent
import com.jetbrains.test.data.componentAs
import com.jetbrains.test.fixtures.CommonContainerFixture
import com.jetbrains.test.fixtures.ContainerFixture
import com.jetbrains.test.fixtures.FixtureName
import com.jetbrains.test.search.locators.byXpath
import com.jetbrains.test.stepsProcessing.step
import com.jetbrains.test.utils.waitFor
import java.time.Duration

fun RemoteRobot.idea(function: IdeaFrame.() -> Unit) {
    find<IdeaFrame>(byXpath("//div[@class='IdeFrameImpl' and @visible='true']")).apply(function)
}

@FixtureName("Idea frame")
class IdeaFrame(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {

    val projectViewTree
        get() = find<ContainerFixture>(byXpath("ProjectViewTree", "//div[@class='ProjectViewTree']"))

    val projectName
        get() = step("Get project name") { return@step retrieve<String>("component.getProject().getName()") }

    fun dumbAware(timeout: Duration = Duration.ofMinutes(5), function: () -> Unit) {
        step("Wait for smart mode") {
            waitFor(duration = timeout, interval = Duration.ofSeconds(5)) {
                runCatching { isDumbMode().not() }.getOrDefault(false)
            }
            function()
            step("..wait for smart mode again") {
                waitFor(duration = timeout, interval = Duration.ofSeconds(5)) {
                    isDumbMode().not()
                }
            }
        }
    }

    private fun isDumbMode(): Boolean {
        return remoteRobot.retrieve(this, true) {
            DumbService.isDumb(componentAs<IdeFrameImpl>().project!!)
        }
    }
}
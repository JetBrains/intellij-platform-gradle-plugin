package org.intellij.examples.simple.plugin.pages

import com.jetbrains.test.RemoteRobot
import com.jetbrains.test.data.RemoteComponent
import com.jetbrains.test.fixtures.ActionLinkFixture
import com.jetbrains.test.fixtures.CommonContainerFixture
import com.jetbrains.test.search.locators.byXpath

fun RemoteRobot.welcomeFrame(function: WelcomeFrame.()->Unit) {
    find<WelcomeFrame>(byXpath("//div[@class='FlatWelcomeFrame' and @visible='true']")).apply(function)
}

class WelcomeFrame(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {
    val createNewProjectLink
        get() = actionLink(ActionLinkFixture.byText("Create New Project"))
}
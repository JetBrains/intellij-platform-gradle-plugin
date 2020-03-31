package org.intellij.examples.simple.plugin.pages

import com.jetbrains.test.RemoteRobot
import com.jetbrains.test.data.RemoteComponent
import com.jetbrains.test.fixtures.ContainerFixture
import com.jetbrains.test.search.locators.byXpath

fun RemoteRobot.welcomeFrame(function: WelcomeFrame.()->Unit) {
    find<WelcomeFrame>(byXpath("//div[@class='FlatWelcomeFrame']")).apply(function)
}
class WelcomeFrame(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : ContainerFixture(remoteRobot, remoteComponent) {

}
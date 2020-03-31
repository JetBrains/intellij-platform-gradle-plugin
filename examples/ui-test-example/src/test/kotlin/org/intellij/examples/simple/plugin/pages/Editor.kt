package org.intellij.examples.simple.plugin.pages

import com.jetbrains.test.RemoteRobot
import com.jetbrains.test.data.RemoteComponent
import com.jetbrains.test.fixtures.CommonContainerFixture
import com.jetbrains.test.fixtures.ContainerFixture
import com.jetbrains.test.fixtures.FixtureName
import com.jetbrains.test.search.locators.byXpath

fun ContainerFixture.editor(function: Editor.()->Unit) {
    find<Editor>(byXpath("//div[@class='EditorComponentImpl']")).apply(function)
}

@FixtureName("Editor")
class Editor(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {

}
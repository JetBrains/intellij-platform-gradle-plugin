package org.intellij.examples.simple.plugin.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath

@JvmOverloads
fun ContainerFixture.editor(title: String, function: Editor.() -> Unit = {}): ContainerFixture {
    find<ComponentFixture>(
            byXpath("//div[@class='EditorTabs']//div[@accessiblename='$title' and @class='SingleHeightLabel']")).click()
    return find<Editor>(
            byXpath("title '$title'", "//div[@accessiblename='Editor for $title' and @class='EditorComponentImpl']"))
            .apply { runJs("robot.moveMouse(component);") }
            .apply(function)
}

@FixtureName("Editor")
class Editor(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent)
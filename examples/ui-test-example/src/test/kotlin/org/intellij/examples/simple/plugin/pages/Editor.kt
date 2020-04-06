package org.intellij.examples.simple.plugin.pages

import com.jetbrains.test.RemoteRobot
import com.jetbrains.test.data.RemoteComponent
import com.jetbrains.test.fixtures.CommonContainerFixture
import com.jetbrains.test.fixtures.ComponentFixture
import com.jetbrains.test.fixtures.ContainerFixture
import com.jetbrains.test.fixtures.FixtureName
import com.jetbrains.test.search.locators.byXpath

@JvmOverloads
fun ContainerFixture.editor(title: String, function: Editor.() -> Unit = {}): ContainerFixture {
    find<ComponentFixture>(
            byXpath("//div[@class='EditorTabs']//div[@accessiblename='$title' and @class='SingleHeightLabel']")).click()
    return find<Editor>(byXpath("title '$title'", "//div[@accessiblename='Editor for $title' and @class='EditorComponentImpl']")).apply(function)
}

@FixtureName("Editor")
class Editor(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent)
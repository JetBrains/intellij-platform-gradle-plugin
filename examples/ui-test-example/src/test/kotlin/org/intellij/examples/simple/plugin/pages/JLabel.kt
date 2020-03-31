package org.intellij.examples.simple.plugin.pages

import com.intellij.ui.components.labels.LinkLabel
import com.jetbrains.test.RemoteRobot
import com.jetbrains.test.data.RemoteComponent
import com.jetbrains.test.data.componentAs
import com.jetbrains.test.fixtures.ComponentFixture
import com.jetbrains.test.fixtures.FixtureName
import com.jetbrains.test.search.locators.LambdaLocator
import com.jetbrains.test.stepsProcessing.step
import javax.swing.JLabel

@FixtureName("JLabel")
class JLabelFixture(
        remoteRobot: RemoteRobot,
        remoteComponent: RemoteComponent
) : ComponentFixture(remoteRobot, remoteComponent) {

    val value: String
        get() = step("..get value") { return@step retrieve("component.getText()")}

    fun isVisible(): Boolean = step("..is 'JBLabel' visible") {
        return@step remoteRobot.retrieve(this) {
            componentAs<JLabel>().isVisible
        }
    }
}
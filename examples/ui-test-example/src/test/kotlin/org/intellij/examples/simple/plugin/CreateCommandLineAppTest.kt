package org.intellij.examples.simple.plugin

import com.jetbrains.test.fixtures.ComponentFixture
import com.jetbrains.test.fixtures.ContainerFixture
import com.jetbrains.test.search.locators.byXpath
import com.jetbrains.test.stepsProcessing.StepLogger
import com.jetbrains.test.stepsProcessing.StepWorker
import com.jetbrains.test.stepsProcessing.step
import com.jetbrains.test.utils.waitFor
import org.assertj.swing.core.MouseButton
import org.intellij.examples.simple.plugin.pages.*
import org.intellij.examples.simple.plugin.utils.autocomplete
import org.intellij.examples.simple.plugin.utils.keyboard
import org.intellij.examples.simple.plugin.utils.uiTest
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent

class CreateCommandLineAppTest {
    init {
        StepWorker.registerProcessor(StepLogger())
    }

    @Test
    fun createCommandLineApp() = uiTest {
        welcomeFrame {
            createNewProjectLink.click()
            dialog("New Project") {
                text("Java").click()
                find<ComponentFixture>(byXpath("//div[@class='FrameworksTree']")).text("Kotlin/JVM").click()
                execute("robot.pressAndReleaseKey(${KeyEvent.VK_SPACE})")
                button("Next").click()
                button("Finish").click()
            }
        }
        idea {
            dumbAware {
                dialog("Tip of the Day") {
                    button("Close").click()
                }
            }
            step("Create App file") {
                with(projectViewTree) {
                    text(projectName).doubleClick()
                    waitFor { hasText("src") }
                    text("src").click(MouseButton.RIGHT_BUTTON)
                }
                actionMenu("New").click()
                actionMenuItem("Kotlin File/Class").click()
                keyboard { enterText("App"); enter() }
            }
            editor {
                step("Write a code") {
                    autocomplete("main")
                    autocomplete("sout")
                    keyboard { enterText("\""); enterText("Hello from UI test") }
                }
                step("Launch application") {
                    text("main").click()
                    keyboard { hotKey(KeyEvent.VK_ALT, KeyEvent.VK_ENTER); enter() }
                }
            }
        }
    }
}
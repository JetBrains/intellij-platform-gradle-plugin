package org.intellij.examples.simple.plugin

import com.jetbrains.test.fixtures.ComponentFixture
import com.jetbrains.test.fixtures.ContainerFixture
import com.jetbrains.test.search.locators.byXpath
import com.jetbrains.test.stepsProcessing.step
import com.jetbrains.test.utils.autocomplete
import com.jetbrains.test.utils.keyboard
import com.jetbrains.test.utils.waitFor
import org.assertj.swing.core.MouseButton
import org.intellij.examples.simple.plugin.pages.*
import org.intellij.examples.simple.plugin.steps.JavaExampleSteps
import org.intellij.examples.simple.plugin.utils.StepsLogger
import org.intellij.examples.simple.plugin.utils.uiTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import java.time.Duration

class CreateCommandLineKotlinTest {
    init {
        StepsLogger.init()
    }

    @AfterEach
    fun closeProject() = uiTest {
        idea {
            actionMenu("File").click()
            actionMenuItem("Close Project").click()
        }
    }

    @Test
    fun createCommandLineApp() = uiTest {
        val steps = JavaExampleSteps(this)

        welcomeFrame {
            createNewProjectLink.click()
            dialog("New Project") {
                text("Java").click()
                find(ComponentFixture::class.java,
                        byXpath("//div[@class='FrameworksTree']")
                ).text("Kotlin/JVM").click()
                execute("robot.pressAndReleaseKey(${KeyEvent.VK_SPACE})")
                button("Next").click()
                button("Finish").click()
            }
        }
        steps.closeTipOfTheDay()
        idea {
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
            editor("App.kt") {
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

            val consoleLocator = byXpath("ConsoleViewImpl", "//div[@class='ConsoleViewImpl']")
            step("Wait for Console appears") {
                waitFor(Duration.ofMinutes(1)) { findAll<ContainerFixture>(consoleLocator).isNotEmpty() }
            }
            step("Check the message") {
                assert(find<ContainerFixture>(consoleLocator).hasText("Hello from UI test"))
            }
        }
    }
}
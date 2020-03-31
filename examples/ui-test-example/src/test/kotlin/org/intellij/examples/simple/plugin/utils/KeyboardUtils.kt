@file:Suppress("NAME_SHADOWING")

package org.intellij.examples.simple.plugin.utils

import com.jetbrains.test.RemoteRobot
import com.jetbrains.test.fixtures.ComponentFixture
import com.jetbrains.test.fixtures.Fixture
import com.jetbrains.test.search.locators.byLambda
import com.jetbrains.test.stepsProcessing.step
import com.jetbrains.test.utils.waitFor
import org.assertj.swing.timing.Pause
import java.awt.event.KeyEvent
import java.time.Duration


inline fun Fixture.keyboard(function: Keyboard.() -> Unit) {
    Keyboard(remoteRobot).function()
}

inline fun RemoteRobot.keyboard(function: Keyboard.() -> Unit) {
    Keyboard(this).function()
}

open class Keyboard(private val remoteRobot: RemoteRobot) {
    fun key(k: Int, waitAfter: Duration = Duration.ofMillis(200)) = step("'${KeyEvent.getKeyText(k)}'") {
        val k = k
        remoteRobot.execute { robot.pressAndReleaseKey(k) }
        Thread.sleep(waitAfter.toMillis())
    }

    fun hotKey(vararg keyCodes: Int) = step("'${keyCodes.contentToString()}'") {
        val keyCodes: IntArray = keyCodes
        remoteRobot.execute {
            for (keyCode in keyCodes) {
                robot.pressKey(keyCode)
                Thread.sleep(100)
            }
            for (keyCode in keyCodes.reversed()) {
                robot.releaseKey(keyCode)
                Thread.sleep(100)
            }
        }
    }


    fun enter() = key(KeyEvent.VK_ENTER)
    fun escape(waitAfter: Duration = Duration.ofMillis(200)) = key(KeyEvent.VK_ESCAPE, waitAfter)
    fun down() = key(KeyEvent.VK_DOWN)
    fun up() = key(KeyEvent.VK_UP)
    fun backspace() = key(KeyEvent.VK_BACK_SPACE)

    fun enterText(text: String, delayBetweenCharsInMs: Long = 50) = step("Typing '$text'") {
        val text = text
        val delay = delayBetweenCharsInMs
        remoteRobot.execute {
            for (c in text) {
                robot.type(c)
                if (delay > 0) {
                    Pause.pause(delay)
                }
            }
        }
    }

    fun pressing(key: Int, doWhilePress: Keyboard.() -> Unit) {
        val pressingKey = key
        remoteRobot.execute { robot.pressKey(pressingKey) }
        this.doWhilePress()
        remoteRobot.execute { robot.releaseKey(pressingKey) }
    }

    fun selectAll() {
        if (remoteRobot.isMac()) {
            hotKey(KeyEvent.VK_META, KeyEvent.VK_A)
        } else {
            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A)
        }
    }

    fun double(keyCode: Int) = step("double press keycode $keyCode") {
        if (remoteRobot.isMac()) {
            val keyName = when (keyCode) {
                KeyEvent.VK_SHIFT -> "shift"
                KeyEvent.VK_CONTROL -> "control"
                else -> throw IllegalStateException("unknown key name with code $keyCode")
            }
            doubleKeyPressOnMac(keyName)
        } else {
            val key = keyCode
            remoteRobot.execute {
                robot.pressKey(key)
                robot.releaseKey(key)
                Thread.sleep(10)
                robot.pressKey(key)
                robot.releaseKey(key)
            }
        }
    }

    private fun doubleKeyPressOnMac(keyName: String) {
        val key = keyName
        remoteRobot.execute {
            val command = "tell application \"System Events\"\n" +
                    "  key down {$key}\n" +
                    "  key up {$key}\n" +
                    "  delay 0.1\n" +
                    "  key down {$key}\n" +
                    "  key up {$key}\n" +
                    "end tell\n" +
                    "delay 1\n"
            Runtime.getRuntime().exec(arrayOf("osascript", "-e", command))
        }
    }
}

fun RemoteRobot.autocomplete(text: String) = step("Autocomplete '$text'") {
    val popupLocator = byLambda("type HeavyWeightWindow") {
        it::class.java.name.endsWith("HeavyWeightWindow") && it.isShowing
    }
    keyboard {
        enterText(text)
        waitFor(Duration.ofSeconds(5)) { findAll<ComponentFixture>(popupLocator).isNotEmpty() }
        enter()
    }
}
package org.intellij.examples.simple.plugin.pages

import com.jetbrains.test.RemoteRobot
import com.jetbrains.test.fixtures.ComponentFixture
import com.jetbrains.test.search.locators.byXpath
import com.jetbrains.test.utils.waitFor

fun RemoteRobot.actionMenu(text: String): ComponentFixture {
    val xpath = byXpath("//div[@class='ActionMenu' and @text='$text']")
    waitFor {
        findAll<ComponentFixture>(xpath).isNotEmpty()
    }
    return findAll<ComponentFixture>(xpath).first()
}

fun RemoteRobot.actionMenuItem(text: String): ComponentFixture {
    val xpath = byXpath("//div[@class='ActionMenuItem' and @text='$text']")
    waitFor {
        findAll<ComponentFixture>(xpath).isNotEmpty()
    }
    return findAll<ComponentFixture>(xpath).first()
}
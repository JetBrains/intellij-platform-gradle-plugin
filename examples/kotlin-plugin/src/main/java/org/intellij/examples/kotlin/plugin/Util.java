package org.intellij.examples.kotlin.plugin;

import com.intellij.openapi.ui.Messages;

public class Util {

    public static void sayHello() {
        Messages.showInfoMessage("Hello Kotlin!\n", "Hello");
    }
}

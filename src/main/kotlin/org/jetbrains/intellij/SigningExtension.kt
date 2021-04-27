package org.jetbrains.intellij

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

open class SigningExtension(objects: ObjectFactory) {
    val privateKey: Property<String> = objects.property(String::class.java)
    val certificateChain: Property<String> = objects.property(String::class.java)
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}
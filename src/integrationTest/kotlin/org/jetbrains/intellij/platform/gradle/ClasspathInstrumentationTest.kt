// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import kotlin.io.path.exists
import kotlin.test.Test

class ClasspathInstrumentationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "classpath",
) {

    @Test
    fun `dependencies should not contain IDEA if setupDependencies not called`() {
        build("dependencies") {
            safeLogs containsText """
                \--- org.jetbrains:markdown:0.3.1
                     \--- org.jetbrains:markdown-jvm:0.3.1
                          +--- org.jetbrains.kotlin:kotlin-stdlib:1.5.31
                          |    +--- org.jetbrains:annotations:13.0
                          |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.5.31
                          \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.5.31
            """.trimIndent()
        }
    }

    @Test
    fun `dependencies should contain IDEA after calling setupDependencies`() {
        build("setupDependencies", "dependencies") {
            safeLogs containsText """
                +--- org.jetbrains:markdown:0.3.1
                |    \--- org.jetbrains:markdown-jvm:0.3.1
                |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.5.31
                |         |    +--- org.jetbrains:annotations:13.0 -> 24.0.1
                |         |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.5.31
                |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.5.31
                +--- org.jetbrains:annotations:24.0.1
                \--- com.jetbrains:ideaIC:2022.1
            """.trimIndent()

            safeOutput containsText """
                implementation - Implementation only dependencies for null/main. (n)
                \--- org.jetbrains:markdown:0.3.1 (n)
            """.trimIndent()

            safeOutput containsText """
                z10_intellijDefaultDependencies
                \--- org.jetbrains:annotations:24.0.1
            """.trimIndent()

            safeOutput containsText """
                z90_intellij
                \--- com.jetbrains:ideaIC:2022.1
            """.trimIndent()
        }
    }

    @Test
    fun `jacoco should work`() {
        build("build") {
            buildDirectory.resolve("jacoco/test.exec").let {
                assert(it.exists()) { "expect that $it exists" }
            }

            buildDirectory.resolve("reports/jacoco.xml").let {
                assert(it.exists())  { "expect that $it exists" }

                it containsText """
                    <method name="getRandomNumber" desc="()I" line="7">
                        <counter type="INSTRUCTION" missed="0" covered="2"/>
                        <counter type="LINE" missed="0" covered="1"/>
                        <counter type="COMPLEXITY" missed="0" covered="1"/>
                        <counter type="METHOD" missed="0" covered="1"/>
                    </method>
                """.trimIndent().replace(""">\s+<""".toRegex(), """><""")
            }
        }
    }
}

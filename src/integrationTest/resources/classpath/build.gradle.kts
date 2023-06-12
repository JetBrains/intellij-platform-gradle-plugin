// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("jacoco")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains:markdown:0.3.1")
}

kotlin {
    jvmToolchain(11)
}

intellij {
    version.set("2022.1.4")
}

tasks {
    buildSearchableOptions {
        enabled = false
    }

    withType<Test> {
        configure<JacocoTaskExtension> {
            isIncludeNoLocationClasses = true
            excludes = listOf("jdk.internal.*")
        }
    }

    test {
        // <workaround>
        // apply JUnit compatibility workaround
        // https://youtrack.jetbrains.com/issue/IDEA-278926#focus=Comments-27-5561012.0-0
        isScanForTestClasses = false
        include("**/*Test.class")
        // </workaround>

        finalizedBy(jacocoTestReport)
    }

    jacocoTestReport {
        reports {
            xml.required.set(true)
            xml.outputLocation.set(buildDir.resolve("reports/jacoco.xml"))
        }
    }
}

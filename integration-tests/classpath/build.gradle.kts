plugins {
    id("jacoco")
}

dependencies {
    implementation("org.jetbrains:markdown:0.3.1")
}

tasks {
    buildSearchableOptions {
        enabled = false
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

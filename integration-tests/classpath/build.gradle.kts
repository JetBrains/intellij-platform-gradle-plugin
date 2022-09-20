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
        finalizedBy(jacocoTestReport)
    }

    jacocoTestReport {
        reports {
            xml.required.set(true)
            xml.outputLocation.set(buildDir.resolve("reports/jacoco.xml"))
        }
    }
}

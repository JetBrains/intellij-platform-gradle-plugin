tasks {
    instrumentCode {
        enabled = false
    }

    integrationTest {
        dependsOn(buildPlugin)
    }
}

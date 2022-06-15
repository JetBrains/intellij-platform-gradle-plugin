val buildSearchableOptionsEnabledProperty = project.findProperty("buildSearchableOptionsEnabled") == "true"
val projectExecutableProperty = project.findProperty("projectExecutable")

tasks {
    buildSearchableOptions {
        enabled = buildSearchableOptionsEnabledProperty
        projectExecutableProperty?.let { projectExecutable.set("$it") }
    }
}

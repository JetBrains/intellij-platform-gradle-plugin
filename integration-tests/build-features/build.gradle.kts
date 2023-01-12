val buildSearchableOptionsEnabledProperty = project.findProperty("buildSearchableOptionsEnabled") == "true"

tasks {
    buildSearchableOptions {
        enabled = buildSearchableOptionsEnabledProperty
    }
}

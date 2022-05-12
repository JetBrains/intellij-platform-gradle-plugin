val instrumentCodeProperty = project.property("instrumentCode") == "true"

intellij {
    instrumentCode.set(instrumentCodeProperty)
}

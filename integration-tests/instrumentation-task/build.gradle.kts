sourceSets {
    main {
        java.srcDirs("customSrc")
    }
}

dependencies {
    implementation(project(":instrumentation-task:submodule"))
}

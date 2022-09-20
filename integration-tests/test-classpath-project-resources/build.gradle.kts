tasks {
    test {
        doFirst {
            classpath.files.forEach {
                println("test-classpath-project-resources: Test classpath entry: ${it.absolutePath}")
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:4.7.0")
    testImplementation("org.powermock:powermock-api-mockito2:2.0.9")
    testImplementation("org.powermock:powermock-core:2.0.9")
}

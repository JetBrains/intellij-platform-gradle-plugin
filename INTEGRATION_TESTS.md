# IntelliJ Platform Gradle Plugin Integration Tests

## Purpose of running Integration Tests

The IntelliJ Platform Gradle Plugin already contains a set of unit tests, but in some cases, it is not enough when a
more complex configuration is used.

Integration Tests are supposed to build the IntelliJ Platform Gradle Plugin from the sources held in the currently used
branch (i.e., for specific commits, pull request) and use it against the curated list of subprojects.

Each of the Integration Tests is a standalone project that focuses on a specific use-case, like patching
the `plugin.xml` file, running one of the small IDEs with extra dependencies and feature implemented, etc.

## How It Works

Running Integration Tests is based on GitHub Actions and makes use of the matrix build so that we can run all tests with
variations of different properties.

See [GitHub Workflows README](.github/workflows/README.md).

Each matrix variation is used to run the Integration Tests projects as a separate step.
Its Gradle build output is used for further verification with classic unit test assertions.

## Integration Tests Structure

```
src/integrationTest/
├── kotlin
│   └── org.jetbrains.intellij.platform.gradle
│       ├── [MyTest]IntegrationTest.kt
│       └── ...
└── resources
    ├── [my-test]
    │   ├── build.gradle.kts
    │   ├── gradle.properties
    │   ├── settings.gradle.kts
    │   └── src
    │       ├── main
    │       │   ├── kotlin
    │       │   │   └── ...
    │       │   └── resources
    │       │       └── META-INF
    │       │           └── plugin.xml
    │       └── test
    │           └── kotlin
    │               └── ...
    └── ...
```

To introduce a new Integration Tests, create `[NewTest]IntegrationTest` class inside the `src/integrationTest/kotlin/org/jetbrains/intellij/platform/gradle/` directory.

The minimal test class scaffold requires providing a related `new-test` resources directory in `src/integrationTest/resources/new-test/` and a single test:

```kotlin
class NewTestIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "new-test",
) {

    @Test
    fun `test feature`() {
        build("taskName", projectProperties = defaultProjectProperties) {
            assertTrue(true)
        }
    }
}
```

## Running Tests Locally

To run all integration tests locally, invoke the `./gradlew integrationTests` task.

It is also possible to run a single test class or just test by clicking the green play button next to its definition.

# Agent instructions

## Project

The IntelliJ Platform Gradle Plugin is a Kotlin Gradle plugin for building, testing, verifying, and publishing plugins for IntelliJ-based IDEs.

- Production code: `src/main/kotlin/org/jetbrains/intellij/platform/gradle/`. `plugins/` contains project/settings entry points, `extensions/` the consumer DSL, and `tasks/` task implementations; supporting packages handle artifacts, models, providers, and services.
- Tests: `src/test/` contains unit and Gradle TestKit tests; `src/integrationTest/` exercises fixture projects in its `resources/` directory. Shared TestKit infrastructure lives in `src/testFixtures/`.
- `build-logic/` is an included build. Dependency versions live in `gradle/libs.versions.toml`.

## Development environment

Run commands from the repository root using `./gradlew` (`gradlew.bat` on Windows). Use JDK 21 to match CI; compilation uses a Java 17 toolchain and Kotlin 2.2 language/API settings in `build.gradle.kts`. The Foojay resolver in `settings.gradle.kts` can provision the toolchain. The wrapper currently uses Gradle 9.8.1.

Initial setup and tests need network access for Gradle, toolchains, dependencies, and IDE distributions. TestKit suites can require substantial disk space and memory; the build configures an 8 GB Gradle heap and 4 GB test-worker heaps.

## Gradle sources

When discussing issues, designing features, or changing Gradle integration, inspect the sources for the version pinned in `gradle/wrapper/gradle-wrapper.properties` (currently 9.8.1). This checkout's wrapper version is the development reference, rather than the minimum supported version or a floating latest Gradle release; fetching 9.0.0 sources is unnecessary.

- Reuse the wrapper's `-all.zip` distribution: it includes binaries, documentation, and sources. Sources are under `GRADLE_USER_HOME` (default `~/.gradle`) at `wrapper/dists/gradle-<version>-all/<hash>/gradle-<version>/src/`. For the current wrapper, the default path is `~/.gradle/wrapper/dists/gradle-9.8.1-all/<hash>/gradle-9.8.1/src/`. Discover the hash directory locally rather than hard-coding it.
- Search that source tree for the relevant types and follow their implementation and lifecycle. The `-all` layout is `src/<module>/<package>/...`, for example `src/core-api/org/gradle/api/Project.java`; Git repository and `-src.zip` paths instead retain directories such as `subprojects/<module>/src/main/java/` or `platforms/<area>/<module>/src/main/java/`.
- For an uncached, one-off lookup, use the matching version's [Javadoc](https://docs.gradle.org/9.8.1/javadoc/) or [Gradle Git tag](https://github.com/gradle/gradle/tree/v9.8.1). For repeated local searches, `./gradlew --version` provisions the configured `-all` distribution if absent, without changing the wrapper; run it only when execution and downloads are authorized. Respect the audit procedure's read-only permissions.
- Treat documented public APIs as the supported contract and implementation sources as supporting evidence, not permission to depend on internal APIs (`.internal.` packages or `Internal` types). Check the [API lifecycle](https://docs.gradle.org/9.8.1/userguide/feature_lifecycle.html) for incubating and deprecated APIs. Cite the inspected version and symbol; when the wrapper changes, use matching sources, documentation, and tags instead of `/current/` links.

## Commands

| Purpose | Command |
| --- | --- |
| Initialize the build | `./gradlew help` |
| Build artifacts, including sources and documentation | `./gradlew assemble` |
| Focused unit test (replace the selector) | `./gradlew test --tests '*WriteTextAtomicallyIfChangedTest'` |
| Focused integration test (replace the selector) | `./gradlew integrationTest --tests '*PluginXmlPatchingIntegrationTest'` |
| Binary API compatibility | `./gradlew apiCheck` |
| Full local build and validation | `./gradlew build integrationTest` |

Use `--tests 'fully.qualified.ClassName.methodName'` to narrow further. The task is singular `integrationTest`, and it is not wired into `check`; request it explicitly.

`-PtestGradleVersion=9.0.0` selects the Gradle version used by nested TestKit builds, not the wrapper. CI tests minimum, current, and nightly Gradle on Linux, Windows, and macOS; consult `.github/workflows/reusable-unitTests.yml` and `.github/workflows/reusable-integrationTests.yml` when changing compatibility. Tests reuse `.gradle/testGradleHome`; override it with `-PtestGradleUserHome=<path>` or control forks with `-PtestMaxParallelForks=<count>`. Inspect failures in `build/diagnostics/` and `build/reports/tests/` before considering cache resets.

## Project constraints

- Support floors live in `Constants.Constraints` (`src/main/kotlin/org/jetbrains/intellij/platform/gradle/Constants.kt`); keep them distinct from the wrapper and default test IDE versions. Preserve the IntelliJ Platform floor of 2023.3 (build 233).
- The Gradle minimum is currently 9.0.0. If a needed public API requires a newer release, raise the declared minimum to the first version providing it rather than add compatibility workarounds solely to retain 9.0.0 support. Keep compatibility checks, minimum-version CI/tests, and documentation aligned with that change, and validate affected behavior against the declared minimum and wrapper versions. Reading newer sources alone does not change the declared support floor.
- Preserve consumer-facing DSL, task names, properties, and source/binary compatibility. `api/IntelliJPlatformGradlePlugin.api` is generated from public Kotlin declarations; regenerate with `./gradlew apiDump` only for intentional, reviewed API changes and inspect the resulting diff.
- Preserve Gradle configuration-cache support and lazy configuration. Use the existing configuration-cache test helpers when changing configuration behavior. Keep the Java/Kotlin targets and unbundled Kotlin standard library settings unless the task explicitly changes compatibility.
- Edit source inputs rather than generated outputs under `build/`: plugin descriptors come from the `gradlePlugin` block in `build.gradle.kts`, and Dokka documentation comes from source KDoc.

## Documentation

- Read [CONTRIBUTING.md](CONTRIBUTING.md) for code style, changelog, and contribution requirements.
- For integration-test or fixture work, read [INTEGRATION_TESTS.md](INTEGRATION_TESTS.md); use the task name documented above.
- For CI or compatibility changes, read [.github/workflows/README.md](.github/workflows/README.md) and the relevant workflows.
- For CLI reporting changes, read [CLI_REPORTS.md](CLI_REPORTS.md) for JSON contracts and safety requirements.
- For user-facing behavior, follow the SDK documentation linked from [README.md](README.md).

## Agent workflows

Keep always-relevant project guidance in this file. Store task-specific procedures in `docs/agents/` with descriptive filenames, and add a short pointer here explaining when to read each one; those files are not loaded automatically. Keep reports and findings in `docs/audits/`, separate from instructions.

### Repository audits

When explicitly asked to audit the project, read [docs/agents/audit.md](docs/agents/audit.md) and follow its procedure and permissions. Recheck relevant earlier dated reports in `docs/audits/`, if any, against current code, recording their dates, audited revisions, and any unknown provenance. An audit produces a report; implement fixes only when the requested task also includes remediation. When saving a report is explicitly authorized, use `docs/audits/YYYY-MM-DD-<short-sha>.md`, using the audit date and audited commit, without replacing earlier reports. Do not create placeholder reports.

## Completing a task

Run focused checks for changed behavior and broaden validation according to risk. Documentation-only changes need link and diff checks, not builds or tests. Summarize changes, exact verification commands and results, skipped checks, and anything left unverified.

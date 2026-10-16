# IntelliJ Platform CLI reports

The `intellijPlatformInfo`, `intellijPlatformDoctor`, `intellijPlatformDiscover`, and `intellijPlatformSdk` tasks expose plugin-development facts as JSON files for people, CI, and CLI-based coding agents. Parse the JSON **file**, not Gradle's console output. Existing print/dump/verification tasks are unchanged.

## Common contract (version 1)

All reports are UTF-8 JSON with stable ordering, explicit nulls, and no timestamps:

```json
{
  "schemaVersion": 1,
  "reportType": "info",
  "gradlePluginVersion": "…",
  "projectPath": ":plugin",
  "status": "complete",
  "data": {},
  "issues": [],
  "unavailable": []
}
```

- `status` is `complete`, `partial` (something could not be observed), or `failed` (the requested operation failed).
- `unavailable` entries contain `observation`, `state` (`notRequested`, `notResolved`, or `unavailable`), and `reason`. Intentionally unrequested resolution is distinct from a failure.
- `issues` contain a stable `id`, `severity`, `message`, nullable `setting`, `suggestedFix`, `documentation`, and `suppressed`.
- Additive fields are compatible within a schema version; consumers must ignore unknown fields. Breaking changes bump `schemaVersion`.

Reports are written atomically to the current project's `build/reports/intellij-platform/<type>.json`; `--output=<path>` overrides it (relative to that project; quote paths with spaces). The tasks always execute, are never restored from the build cache, and support the configuration cache. A short console message identifies the file.

## Project context

```bash
./gradlew :plugin:intellijPlatformInfo --console=plain
./gradlew :plugin:intellijPlatformInfo --resolve --output="build/reports/project context.json"
```

`info.json` does **not** resolve or download an IDE by default. `data` contains the project identity, module status, plugin identity, requested IDE metadata, declared Java/Kotlin settings, allowlisted switches, declared platform dependency identities, and the project-qualified commands that are available. Java `release`, source/target compatibility, Gradle JVM, and the requested toolchain are distinct observations; reading them does not provision a JDK. Null values mean no explicit value was configured.

`--resolve` adds `data.resolvedIde` (product code, version, build, root) and `data.resolvedPlugins` (artifact paths); it may download artifacts and respects `--offline`.

## Configuration diagnosis

```bash
./gradlew :plugin:intellijPlatformDoctor --console=plain
./gradlew :plugin:intellijPlatformDoctor --fail-on=warning --output=build/doctor.json
```

`doctor.json` evaluates the plugin project configuration against the resolved IDE: descriptor `since-build`/`until-build`, Java and Kotlin compatibility with the platform and `since-build`, the minimal supported platform and Kotlin versions, the Kotlin stdlib/coroutines dependencies, and the VCS exclusion of the platform cache. Each finding has a stable `id`, the affected `setting`, and a `suggestedFix`. The runtime-vendor check is explicitly `notRequested`, as it would require acquiring a runtime; `verifyPluginProjectConfiguration` still performs it.

Doctor resolves the selected IDE and reads the effective (patched) `plugin.xml`, but does not compile. Findings never fail the task by default; `--fail-on=none|warning|error` evaluates **unsuppressed** findings after the report is written, using ordinary Gradle success/failure. The `org.jetbrains.intellij.platform.verifyPluginProjectConfigurationMutedMessages` property also applies here; muted findings stay in the report with `suppressed: true`.

## Discovery

```bash
./gradlew :plugin:intellijPlatformDiscover --kind=plugins --query=java
./gradlew :plugin:intellijPlatformDiscover --kind=modules --query=platform
./gradlew :plugin:intellijPlatformDiscover --kind=releases --type=IU --channel=RELEASE --since-build=251 --until-build=251.*
```

`discover.json` identifies `kind`, `query`, `targetIde` (for SDK catalogs), and returns **all** matches in a deterministic order; slice the file locally instead of re-running Gradle. A query matches IDs, names, module aliases, or release notations case-insensitively. Empty matches are successful empty arrays.

`items` lists bundled plugins or modules with the exact `id`, `name`, `kind`, exported `aliases`, `archives`, `originalFile`, a report-local `ref`, and `dependencies` referring to those refs. Duplicate IDs or aliases remain separate entries. Refs are valid within a single report only. Catalog membership is **not** proof of plugin compatibility.

`releases` contains product code, version, build, channel, dependency `notation`, and platform version/build. `--type`, `--channel`, `--since-build`, and `--until-build` use the same filters as `printProductsReleases`. Release discovery uses the cached product listings and never resolves an IDE; it works offline with cached listings, while a cache miss yields an empty, `partial` report.

## SDK file map

```bash
./gradlew :plugin:intellijPlatformSdk --output=build/sdk-map.json
./gradlew :plugin:intellijPlatformSdk --owner=com.intellij.java --console=plain
```

`sdk.json` identifies the selected IDE with `targetIde` and its absolute `root`. `owners` lists the bundled plugins and modules (same shape as discovery) with SDK-relative `archives`; `--owner=<id-or-alias>` narrows this list. `compileClasspath` describes the effective main compilation classpath without compiling: SDK files have `kind: "sdk"`, an SDK-relative `path`, and the owning `owners` refs; other files are `project` or `external` with absolute paths. `sources` lists the source archives found in the known SDK locations (`src.zip`, `sources.zip`, `lib/src/*`); it is derived from files only, never from the `downloadSources` preference, and no sources are downloaded.

## Workflow recipe

1. `intellijPlatformInfo` — learn the project identity, requested IDE, settings, and available commands without resolving anything.
2. `intellijPlatformDoctor` — review unsuppressed `issues`, then apply the suggested fixes yourself; nothing is changed automatically.
3. `intellijPlatformDiscover --kind=plugins|modules --query=…` — find exact bundled dependency IDs; `--kind=releases` — pick candidate target IDEs without downloading them.
4. `intellijPlatformSdk --owner=…` — locate the archives and sources of the selected IDE and inspect them with ordinary tools.

All commands are non-interactive and available on module projects. Use `./gradlew help --task <task>` for option help.

## Costs, safety, and failure semantics

Reports do not compile, prepare sandboxes, launch IDEs, sign, publish, or edit project sources. Doctor patches `plugin.xml` into `build/`, like the verifier does. Resolution (doctor, SDK, discovery of plugins/modules, `info --resolve`) can download the IDE and dependencies and write caches; Gradle's `--offline` is respected.

Caught operational failures write a `failed` report before failing the task with ordinary Gradle semantics. A report cannot be guaranteed after build-script errors, plugin-application failures, or an unwritable destination.

Only allowlisted settings and dependency identities are included. Environment variables, arbitrary Gradle properties, repository credentials, publishing tokens, and signing configuration are never dumped, and exception messages are not copied into JSON because resolver errors may contain repository URLs. Paths still identify your machine; review reports before sharing them.

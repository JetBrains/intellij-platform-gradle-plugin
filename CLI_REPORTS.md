# IntelliJ Platform CLI reports

The IntelliJ Platform reporting tasks expose plugin-development facts as JSON files for people, CI, and CLI-based coding agents. Parse the JSON **file**, not Gradle's console output. Existing print/dump/verification commands remain available.

| Task | Report | Default observation |
| --- | --- | --- |
| `intellijPlatformInfo` | `info.json` | Project context and available commands |
| `intellijPlatformProjectGraph` | `projectgraph.json` | Projects, source roots, and project dependencies |
| `intellijPlatformDistribution` | `distribution.json` | Descriptor and packaging configuration |
| `intellijPlatformDependencyGraph` | `dependencygraph.json` | Declared dependencies; resolution is opt-in |
| `intellijPlatformTestMatrix` | `testmatrix.json` | Configured test workflows |
| `intellijPlatformCompatibilityMatrix` | `compatibilitymatrix.json` | Declared compatibility and configured verification targets |
| `intellijPlatformDoctor` | `doctor.json` | Diagnostics against the resolved IDE |
| `intellijPlatformDiscover` | `discover.json` | SDK catalog or release discovery |
| `intellijPlatformSdk` | `sdk.json` | Resolved SDK files and classpath ownership |

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
- `issues` contain a stable `id`, `severity`, `message`, nullable `setting`, `suggestedFix`, `documentation`, and `suppressed`. Optional diagnostic fields add `observedValue`, `expectedValue`, `configurationLocation`, `category`, `verificationCommand`, and lists of `evidence`, `provenance`, and `applicableIdeVersions`.
- Additive fields are compatible within a schema version; consumers must ignore unknown fields. Breaking changes bump `schemaVersion`.

Declared configuration, resolved dependency facts, and verified artifact or execution evidence are different observations. Missing evidence is reported explicitly: a configured test or verifier target is never proof that verification succeeded. Metadata-only reports do not acquire an IDE or JDK merely to fill a missing field.

Reports are written atomically to the current project's `build/reports/intellij-platform/<type>.json`; `--output=<path>` overrides it (relative to that project; quote paths with spaces). The tasks always execute, are never restored from the build cache, and support the configuration cache. A short console message identifies the file.

## Project context

```bash
./gradlew :plugin:intellijPlatformInfo --console=plain
./gradlew :plugin:intellijPlatformInfo --resolve --output="build/reports/project context.json"
```

`info.json` does **not** resolve or download an IDE by default. `data` contains the project identity, module status, plugin identity, requested IDE metadata, declared Java/Kotlin settings, allowlisted switches, declared platform dependency identities, and the project-qualified commands that are available. Java `release`, source/target compatibility, Gradle JVM, and the requested toolchain are distinct observations; reading them does not provision a JDK. Null values mean no explicit value was configured.

`--resolve` adds `data.resolvedIde` (product code, version, build, root) and `data.resolvedPlugins` (artifact paths); it may download artifacts and respects `--offline`.

`data.capabilities` describes the available registered workflows, including custom tests and native-variant builds. Entries contain a qualified `task`, `purpose`, `prerequisites`, `executionCost`, and `optionalExecutionCost`; costs distinguish `metadata`, `resolution`, `compile`, `ide`, `test`, and `publish`. They describe potential execution costs, not evidence that a workflow ran. The existing `commands` list is retained. `compilerSettings` distinguishes safely observed declared constants from unresolved effective Java/Kotlin settings; lazy providers are not evaluated merely to populate a metadata report. Raw literal IDE selectors such as `latest` remain selectors rather than triggering a release lookup.

## Project graph

```bash
./gradlew intellijPlatformProjectGraph --offline
```

`projectgraph.json` describes the projects in the current build, their build files, applied plugins, source sets and source roots, and declared project-dependency edges. Configuration names distinguish ordinary library dependencies from plugin modules and composed modules; project roles are inferred, not authoritative declarations. Included-build metadata is reported where observable; the report does not recursively configure or analyze external builds. No dependencies are resolved and no source sets are compiled. Qualify a root invocation as `:intellijPlatformProjectGraph` to avoid Gradle's multi-project task selector executing the task in child projects too.

## Distribution contents

```bash
./gradlew :plugin:intellijPlatformDistribution --offline
./gradlew :plugin:intellijPlatformDistribution --inspect --offline
./gradlew :plugin:intellijPlatformDistribution --archive="build/distributions/plugin.zip" --offline
```

`distribution.json` separates declared packaging configuration from observed descriptor and archive contents. The default mode does not build a distribution or resolve its runtime dependencies. Descriptor compatibility bounds, plugin dependencies, module and optional descriptor relationships, planned outputs, and native overlays are reported where observable; missing effective metadata and unresolved files remain explicit rather than being inferred from the compile classpath. Inspecting an existing archive is opt-in and does not invoke `buildPlugin`.

`--inspect` reads the configured existing `buildPlugin` archive; `--archive` chooses an existing ZIP and implies inspection. Archive comparison covers observable planned paths, not arbitrary Gradle copy specifications or a claim of complete packaging validation. Reading an existing patched descriptor does not prove its freshness. Lazy file providers and unresolved dependencies remain unavailable; reports can therefore legitimately remain `partial` even after successful archive inspection.

## Dependency graph

```bash
./gradlew :plugin:intellijPlatformDependencyGraph --offline
./gradlew :plugin:intellijPlatformDependencyGraph --resolve --configuration=runtimeClasspath
./gradlew :plugin:intellijPlatformDependencyGraph --resolve --configuration=compileClasspath --dependency=example
```

`dependencygraph.json` reports declared dependencies separately from resolved components and edges. `--resolve` uses Gradle's resolution result, including transitive relationships, selected variants, selection reasons, and unresolved edges; it can fetch dependency metadata but does not require artifact downloads just to explain the graph. `--configuration` selects an exact name; `--dependency` selects a case-sensitive notation substring. Filtering resolved relationships retains matching edges, their endpoints, and the root, not necessarily every ancestor path. Compilation, test, runtime, packaging, and platform configurations retain their identities. Resolution failures are actionable without copying potentially sensitive resolver exception messages into JSON; filtering cannot hide a resolution failure.

## Test workflows

```bash
./gradlew :plugin:intellijPlatformTestMatrix --offline
```

`testmatrix.json` lists configured test tasks, qualified commands, framework information, source sets, prerequisites, and result locations where available. Ordinary JVM tests, IDE tests, and UI workflows are distinguished so callers can choose an appropriate verification cost. Reporting never executes tests, starts IDEs, prepares sandboxes, or resolves a runtime. Unobservable task-specific infrastructure is marked unavailable.

## Compatibility evidence

```bash
./gradlew :plugin:intellijPlatformCompatibilityMatrix --offline
```

`compatibilitymatrix.json` separates raw descriptor bounds from configured platform, test, and Plugin Verifier targets. It also exposes mapping-based Java/Kotlin requirements and native restrictions. Effective DSL/patch overrides are not inferred from raw descriptor bounds. The report does not ingest execution evidence: every target remains untested and `verifiedSuccess` is empty, even if a verifier report directory exists. Successful compilation against one IDE is not represented as support for an entire compatibility range. The report itself does not run Plugin Verifier or download IDEs.

## Configuration diagnosis

```bash
./gradlew :plugin:intellijPlatformDoctor --console=plain
./gradlew :plugin:intellijPlatformDoctor --fail-on=warning --output=build/doctor.json
```

`doctor.json` evaluates the plugin project configuration against the resolved IDE: descriptor `since-build`/`until-build`, Java and Kotlin compatibility with the platform and `since-build`, the minimal supported platform and Kotlin versions, the Kotlin stdlib/coroutines dependencies, and the VCS exclusion of the platform cache. Each finding has a stable `id`, the affected `setting`, and a `suggestedFix`. The runtime-vendor check is explicitly `notRequested`, as it would require acquiring a runtime; `verifyPluginProjectConfiguration` still performs it.

Doctor and `verifyPluginProjectConfiguration` share the same rule evaluator. Doctor adds observed/expected values, effective-setting or file locations (not invented source lines), evidence and provenance, applicable IDE-version descriptions, and a qualified rerun command. Diagnostic categories are `warning`, `incompatibility`, or `blockingConfigurationError`; category is separate from severity and does not alter the existing warning or suppression policy.

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

`--owner` filters presentation only: classpath entries and their ownership references always come from the complete SDK catalog. References can therefore identify owners omitted from a filtered `owners` list; obtain the unfiltered report when dereferencing the entire classpath catalog. An unknown owner produces an empty owner list without stripping classpath ownership information.

## Workflow recipe

1. `intellijPlatformInfo` — learn the project identity, requested IDE, settings, and available commands without resolving anything.
2. `intellijPlatformDoctor` — review unsuppressed `issues`, then apply the suggested fixes yourself; nothing is changed automatically.
3. `intellijPlatformDiscover --kind=plugins|modules --query=…` — find exact bundled dependency IDs; `--kind=releases` — pick candidate target IDEs without downloading them.
4. `intellijPlatformSdk --owner=…` — locate the archives and sources of the selected IDE and inspect them with ordinary tools.

All commands are non-interactive and available on module projects. Use `./gradlew help --task <task>` for option help.

## Costs, safety, and failure semantics

Reports do not compile, prepare sandboxes, launch IDEs, sign, publish, or edit project sources. Doctor patches `plugin.xml` into `build/`, like the verifier does. Resolution (doctor, SDK, discovery of plugins/modules, `info --resolve`, `dependencyGraph --resolve`) can download metadata, the IDE, or dependencies and write caches; Gradle's `--offline` is respected. Default project, distribution, dependency, and matrix reports do not resolve deferred dependencies; unavailable declarations are not silently forced into existence.

Caught operational failures write a `failed` report before failing the task with ordinary Gradle semantics. A report cannot be guaranteed after build-script errors, plugin-application failures, or an unwritable destination.

Only allowlisted settings and dependency identities are included. Environment variables, arbitrary Gradle properties, repository credentials, publishing tokens, and signing configuration are never dumped, and exception messages are not copied into JSON because resolver errors may contain repository URLs. Paths still identify your machine; review reports before sharing them.

# GitHub Workflows

## Matrix testing

`reusable-unitTests.yml`

Gradle versions:

- minimum supported version (`9.0.0`, see `IntelliJPluginConstants.Constraints.getMINIMAL_GRADLE_VERSION`)
- latest GA (e.g., `9.4.0`)
- RC of next GA (potentially)

When updating the _latest GA_ version, adjust `exclude` section and adjust `single-unitTest.yml`.

## IntelliJ Platform test cache

`build.yml` and `release-nightly.yml` prime `.gradle/testGradleHome/.intellijPlatform` once per OS before launching the unit and integration matrices. The reusable test workflows still support internal priming by default for standalone/manual use, but callers that already prime caches should pass `primeCache: false`. The IntelliJ Platform cache key is stable across normal source-only changes and rotates when Gradle or test fixtures that can change requested IDEs are modified.

Matrix test jobs restore `.gradle/testGradleHome/.testKit` in read-only mode. Cache writes are limited to cache-prime or explicitly writable single-test jobs to avoid long post-job uploads from every matrix cell.

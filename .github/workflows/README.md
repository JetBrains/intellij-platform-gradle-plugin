# GitHub Workflows

## Matrix testing

`reusable-unitTests.yml`

Gradle versions:

- minimum supported version (`8.2`, see `IntelliJPluginConstants.Constraints.getMINIMAL_GRADLE_VERSION`)
- latest GA (e.g., `8.7`)
- RC of next GA (potentially)

When updating _latest GA_ version, adjust `exclude` section 
and adjust `single-unitTest.yml`.

Operating Systems:

- `macos-latest` = Intel arch
- `macos-14` = Apple Silicon arch

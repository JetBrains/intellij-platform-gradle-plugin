# GitHub Workflows

## Matrix testing

Gradle versions:

- minimum supported version (`8.1`, see `IntelliJPluginConstants.Constraints.getMINIMAL_GRADLE_VERSION`)
- latest GA (e.g., `8.6`)
- RC of next GA (potentially)

When updating _latest GA_ version, adjust `exclude` section.

Operating Systems:

- `macos-latest` = Intel arch
- `macos-14` = Apple Silicon arch
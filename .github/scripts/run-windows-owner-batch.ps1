Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$evidence = Join-Path $PWD 'build/windows-owner-evidence'
New-Item -ItemType Directory -Force $evidence | Out-Null
$env:WINDOWS_DIAGNOSTIC_EVIDENCE = $evidence
$env:WINDOWS_DIAGNOSTIC_CAPTURE = Join-Path $PWD '.github/scripts/capture-windows-directory-owner.ps1'
$env:WINDOWS_DIAGNOSTIC_HANDLE = Join-Path $PWD 'build/windows-owner-tools/handle64.exe'
$testHome = Join-Path $PWD '.gradle/testGradleHome'
$selector = '*TestIdeRegressionsIntegrationTest.run tests on IntelliJ IDEA 2026_3 EAP resolved from the IntelliJ Maven Repository'
$command = ".\gradlew.bat integrationTest --tests `"$selector`" -PtestGradleVersion=9.8.1 `"-PtestGradleUserHome=$testHome`" -PtestMaxParallelForks=1 --no-daemon --no-scan --console=plain --no-build-cache --rerun"
[ordered]@{
    revision = (& git rev-parse HEAD)
    baselineRevision = 'bba16be887fa62263dad29f6a2f62700e9618e3e'
    run = $env:GITHUB_RUN_ID
    imageOS = $env:ImageOS
    imageVersion = $env:ImageVersion
    runnerOS = $env:RUNNER_OS
    runnerArch = $env:RUNNER_ARCH
    testKitCache = $env:WINDOWS_DIAGNOSTIC_TESTKIT_CACHE
    platformCache = $env:WINDOWS_DIAGNOSTIC_PLATFORM_CACHE
    command = $command
    startedAt = [DateTime]::UtcNow.ToString('o')
    baselineCapSeconds = 600
    candidateCapSeconds = 300
    handleVersion = (Get-Item $env:WINDOWS_DIAGNOSTIC_HANDLE).VersionInfo.FileVersion
    handleSHA256 = (Get-FileHash $env:WINDOWS_DIAGNOSTIC_HANDLE -Algorithm SHA256).Hash
    java = @(& java -version 2>&1 | ForEach-Object { "$_" })
} | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $evidence 'provenance.json')

function Invoke-Phase([string]$Phase, [int]$CapSeconds) {
    $env:WINDOWS_DIAGNOSTIC_PHASE = $Phase
    $destination = Join-Path $evidence $Phase
    New-Item -ItemType Directory -Force $destination | Out-Null
    $started = [DateTime]::UtcNow
    Write-Host "Running $Phase, cap $CapSeconds seconds: $command"
    $process = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d', '/s', '/c', $command) -PassThru -NoNewWindow `
        -RedirectStandardOutput (Join-Path $destination 'gradle-stdout.log') `
        -RedirectStandardError (Join-Path $destination 'gradle-stderr.log')
    $completed = $process.WaitForExit($CapSeconds * 1000)
    if (-not $completed) {
        $process.Kill($true)
        $process.WaitForExit()
    }
    $process.Refresh()
    $exitCode = $process.ExitCode
    foreach ($directory in @('test-results/integrationTest', 'reports/tests/integrationTest', 'diagnostics')) {
        $source = Join-Path $PWD "build/$directory"
        if (Test-Path $source) {
            $target = Join-Path $destination ('outer-' + $directory.Replace('/', '-'))
            Copy-Item -Recurse -Force $source $target
        }
    }
    [ordered]@{
        phase = $Phase
        exitCode = $exitCode
        completed = $completed
        seconds = ([DateTime]::UtcNow - $started).TotalSeconds
    } | ConvertTo-Json | Set-Content (Join-Path $destination 'execution.json')
    Get-Content (Join-Path $destination 'gradle-stdout.log') -Tail 30 | Write-Host
    Get-Content (Join-Path $destination 'gradle-stderr.log') -Tail 15 | Write-Host
    if (-not $completed) {
        throw "$Phase exceeded its runtime cap; inconclusive, no further execution"
    }
    return $exitCode
}

function Read-NestedResults([string]$Phase) {
    $directory = Join-Path $evidence "$Phase/nested-test-results/test"
    $expected = @('SmokeTest', 'HeavySmokeTest', 'FileComparisonTest', 'FileTemplateTest', 'OptionalDependencyTest')
    $results = @()
    foreach ($name in $expected) {
        $file = Join-Path $directory "TEST-$name.xml"
        if (-not (Test-Path $file)) { throw "Missing $Phase nested report for $name" }
        [xml]$xml = Get-Content $file -Raw
        $suite = $xml.testsuite
        if ([int]$suite.tests -lt 1 -or [int]$suite.skipped -ne 0) {
            throw "Missing or skipped $Phase assertions in $name"
        }
        $results += [ordered]@{
            name = $name
            tests = [int]$suite.tests
            failures = [int]$suite.failures
            errors = [int]$suite.errors
            seconds = [double]$suite.time
        }
    }
    $results | ConvertTo-Json | Set-Content (Join-Path $evidence "$Phase/test-verdicts.json")
    return $results
}

$verdict = 'INCONCLUSIVE'
try {
    $baselineExit = Invoke-Phase 'baseline' 600
    $baselineResults = @(Read-NestedResults 'baseline')
    if ($baselineExit -eq 0) {
        $verdict = 'INCONCLUSIVE_NOT_REPRODUCED'
        throw 'Baseline passed; no candidate will be run and no fix is validated'
    }
    if ($baselineExit -ne 1) {
        throw "Unexpected baseline exit $baselineExit; no candidate will be run"
    }
    $template = @($baselineResults | Where-Object { $_.name -eq 'FileTemplateTest' })[0]
    $otherFailures = @($baselineResults | Where-Object { $_.name -ne 'FileTemplateTest' -and ($_.failures -ne 0 -or $_.errors -ne 0) })
    $failureTrace = Join-Path $evidence 'baseline/FileTemplateTest-teardown.txt'
    $ownerFile = Join-Path $evidence 'baseline/directory-owner.json'
    if ($template.failures -ne 1 -or $template.errors -ne 0 -or $otherFailures.Count -ne 0 -or
        -not (Test-Path $failureTrace) -or -not (Test-Path $ownerFile)) {
        throw 'Baseline did not isolate the expected teardown sharing violation with captured ownership'
    }
    $trace = Get-Content $failureTrace -Raw
    $owner = Get-Content $ownerFile -Raw | ConvertFrom-Json
    if ($trace -notmatch 'FileSystemException:.*unitTest_internalTemplateOfTestedPlugin_.*being used by another process' -or
        $trace -notmatch 'UsefulTestCase.removeGlobalTempDirectory' -or -not $owner.candidateEligible) {
        throw 'Original sharing violation and chatter-only root ownership were not both established; candidate not run'
    }
    Write-Host 'BASELINE FAILED as expected, with chatter mapped to the locked root. Testing only the analytics startup gate.'
    $candidateExit = Invoke-Phase 'candidate' 300
    $candidateResults = @(Read-NestedResults 'candidate')
    if ($candidateExit -ne 0 -or @($candidateResults | Where-Object { $_.failures -ne 0 -or $_.errors -ne 0 }).Count -ne 0) {
        $verdict = 'CANDIDATE_FAILED'
        throw 'Candidate did not pass all unchanged EAP smoke assertions; no further execution'
    }
    $verdict = 'TARGETED_BEFORE_AFTER_CONFIRMED'
    Write-Host 'Candidate passed all five smoke classes with code provenance loaded and only chatter analytics disabled.'
} finally {
    [ordered]@{ verdict = $verdict; finishedAt = [DateTime]::UtcNow.ToString('o') } |
        ConvertTo-Json | Set-Content (Join-Path $evidence 'verdict.json')
    "### Windows directory-owner batch`n`nVerdict: **$verdict**. Baseline failures remain in the saved reports; no retry or macOS execution." |
        Add-Content $env:GITHUB_STEP_SUMMARY
}
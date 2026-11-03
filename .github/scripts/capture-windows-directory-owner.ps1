param(
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory)][string]$ProjectBase,
    [Parameter(Mandatory)][long]$TestJvmPid
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$destination = Join-Path $env:WINDOWS_DIAGNOSTIC_EVIDENCE $env:WINDOWS_DIAGNOSTIC_PHASE
New-Item -ItemType Directory -Force $destination | Out-Null
$processes = @(Get-CimInstance Win32_Process | Select-Object ProcessId, ParentProcessId, Name, ExecutablePath, CommandLine, CreationDate)
$processes | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $destination 'processes-at-failure.json')
$leaf = [IO.Path]::GetFileName($Root.TrimEnd('\', '/'))
$raw = @(& $env:WINDOWS_DIAGNOSTIC_HANDLE -accepteula -nobanner -a -v $leaf 2>&1 | ForEach-Object { "$_" })
$handleExit = $LASTEXITCODE
$raw | Set-Content (Join-Path $destination 'directory-handles.csv')

# Keep the raw output even if the utility's CSV format changes: no parsed owner means no candidate run.
$header = -1
for ($index = 0; $index -lt $raw.Count; $index++) {
    if ($raw[$index] -match '^\s*"?Process"?\s*,\s*"?PID"?\s*,') {
        $header = $index
        break
    }
}
$owners = @()
if ($header -ge 0 -and $raw.Count -gt $header + 1) {
    $rows = @($raw[$header..($raw.Count - 1)] | ConvertFrom-Csv)
    foreach ($row in $rows) {
        if ($row.Type -ne 'File' -or -not $row.Name.TrimEnd('\').EndsWith("\$leaf", [StringComparison]::OrdinalIgnoreCase)) {
            continue
        }
        $ownerProcess = @($processes | Where-Object { $_.ProcessId -eq [long]$row.PID })
        $owners += [ordered]@{
            handle = $row
            processes = $ownerProcess
            chatter = ($ownerProcess.Count -eq 1 -and $ownerProcess[0].Name -match '^chatter.*\.exe$' -and
                $ownerProcess[0].CommandLine -match 'code-provenance[\\/]chatter')
        }
    }
}
$eligible = $handleExit -eq 0 -and $owners.Count -gt 0 -and @($owners | Where-Object { -not $_.chatter }).Count -eq 0
[ordered]@{
    capturedAt = [DateTime]::UtcNow.ToString('o')
    root = $Root
    projectBase = $ProjectBase
    testJvmPid = $TestJvmPid
    handleExit = $handleExit
    owners = $owners
    candidateEligible = $eligible
} | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $destination 'directory-owner.json')
Write-Host "Directory owner captured; chatter-only candidate eligibility: $eligible"
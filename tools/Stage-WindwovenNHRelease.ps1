[CmdletBinding()]
param([string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$version = & (Join-Path $PSScriptRoot 'Get-WindwovenNHVersion.ps1')
if (!$OutputDirectory) { $OutputDirectory = Join-Path $root "build/release/windwovennh/$version" }
Push-Location -LiteralPath $root
try {
    $artifact = "build/libs/windwovennh-$version.jar"
    $source = "build/distributions/windwovennh-$version-source.zip"
    $fixture = "build/qa/windwovennh-fixture-$version.jar"
    $hash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
    $fixtureHash = (Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash.ToLowerInvariant()
    $evidence = "build/measurements/windwovennh/$version"
    function Read-Pass([string]$Name) {
        $path = Join-Path $evidence "$Name.json"
        if (!(Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Missing WindwovenNH $version gate: $Name. Do not use legacy measurements."
        }
        $result = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
        if ($result.modId -cne 'windwovennh' -or $result.version -cne $version -or $result.jarSha256 -cne $hash -or $result.pass -ne $true) {
            throw "Failed or mismatched WindwovenNH $version gate: $Name."
        }
        return $result
    }
    $perf = Read-Pass 'benchmark-summary'
    if ($perf.fixtureSha256 -cne $fixtureHash -or $null -eq $perf.emptyP95ChangePercent -or
        $null -eq $perf.mixedCapeTimeReductionPercent -or $perf.emptyP95ChangePercent -gt 3 -or $perf.mixedCapeTimeReductionPercent -lt 20) {
        throw 'Performance evidence does not meet the measured release thresholds.'
    }
    $compat = Read-Pass 'compatibility'
    if ($compat.fixtureSha256 -cne $fixtureHash -or !$compat.migrated -or !$compat.precedence -or !$compat.copyFailure -or
        !$compat.coexistence -or !$compat.legacyWorld -or !$compat.loopback) { throw 'Incomplete compatibility suite.' }
    $loopback = Read-Pass 'loopback'
    $lines = @(Get-Content -LiteralPath (Join-Path $evidence 'loopback.txt'))
    if ($lines.Count -ne 2 -or 'PASS: mod-free host accepted installed guest' -cnotin $lines -or
        'PASS: installed client spent 60 ticks in remote world' -cnotin $lines) {
        throw 'Both sides of the network fixture must report success.'
    }
    $visual = Read-Pass 'visual-checks'
    if (!$visual.shadersOff -or !$visual.euphoria -or !$visual.turn -or !$visual.shoulderLift) { throw 'Incomplete reviewed visual evidence.' }
    foreach ($name in 'visual-off-scene1.png','visual-off-scene6.png','visual-final-euphoria-scene1.png','visual-final-euphoria-scene6.png') {
        $picture = Join-Path $evidence $name
        if (!(Test-Path -LiteralPath $picture -PathType Leaf) -or $visual.captures.$name -cne (Get-FileHash -LiteralPath $picture -Algorithm SHA256).Hash.ToLowerInvariant()) {
            throw "Missing or changed visual evidence: $name"
        }
    }
    $build = Read-Pass 'build-verification'
    if (!$build.cleanPayload -or !$build.renameParity -or !$build.configTest -or
        $build.sourceSha256 -cne (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant() -or
        $build.reportSha256 -cne (Get-FileHash -LiteralPath 'RELEASE-QA.md' -Algorithm SHA256).Hash.ToLowerInvariant()) {
        throw 'Build, parity, report or source bundle check is missing or stale.'
    }
    if (Test-Path -LiteralPath $OutputDirectory) { throw 'Choose a new release output directory; no older package will be overwritten.' }
    $staged = New-Item -ItemType Directory -Path $OutputDirectory
    $prod = Join-Path $staged.FullName "windwovennh-$version.jar"
    Copy-Item -LiteralPath $artifact -Destination $prod
    if ((Get-FileHash -LiteralPath $prod -Algorithm SHA256).Hash -ine $hash) { throw 'Copied binary differs from the tested one.' }
    "$hash  $(Split-Path $prod -Leaf)" | Set-Content -LiteralPath ($prod + '.sha256') -Encoding ascii
    # No logs, captures, profiler inputs, QA fixture, game libraries or account data go in the public package.
    Copy-Item -LiteralPath $source,'LICENSE','README.md','PROVENANCE.md','RELEASE-NOTES.md','RELEASE-QA.md' -Destination $staged.FullName
    Get-ChildItem -LiteralPath $staged.FullName -File | Select-Object Name,Length
} finally { Pop-Location }

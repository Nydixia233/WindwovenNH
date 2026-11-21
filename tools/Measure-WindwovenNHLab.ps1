[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$InputDirectory,
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$version = & (Join-Path $PSScriptRoot 'Get-WindwovenNHVersion.ps1')
$artifact = Join-Path $PSScriptRoot "..\build\libs\windwovennh-$version.jar"
$fixture = Join-Path $PSScriptRoot "..\build\qa\windwovennh-fixture-$version.jar"
$hash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
$fixtureHash = (Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash.ToLowerInvariant()
$candidate = Get-Content -LiteralPath (Join-Path $InputDirectory 'candidate.json') -Raw | ConvertFrom-Json
if ($candidate.modId -cne 'windwovennh' -or $candidate.version -cne $version -or
    $candidate.jarSha256 -cne $hash -or $candidate.fixtureSha256 -cne $fixtureHash) {
    throw 'Benchmark candidate does not match the current production and fixture artifacts.'
}
$records = @()
$launches = @()
$orders = [System.Collections.Generic.HashSet[int]]::new()
foreach ($file in Get-ChildItem -LiteralPath $InputDirectory -File -Filter '*.csv') {
    if ($file.BaseName -notmatch '^(base|balanced|full)-[a-z][a-z0-9]*$') { continue }
    $mode = $Matches[1]
    $run = Get-Content -LiteralPath (Join-Path $InputDirectory ($file.BaseName + '.json')) -Raw | ConvertFrom-Json
    $quality = if ($mode -eq 'base') { 'baseline' } else { $mode }
    $csvHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($run.modId -cne 'windwovennh' -or $run.version -cne $version -or $run.quality -cne $quality -or
        $run.candidateSha256 -cne $hash -or $run.fixtureSha256 -cne $fixtureHash -or $run.csvSha256 -cne $csvHash -or
        ($mode -eq 'base' -and $null -ne $run.installedSha256) -or ($mode -ne 'base' -and $run.installedSha256 -cne $hash)) {
        throw "Mismatched benchmark identity: $($file.Name)"
    }
    if ([int]$run.order -le 0 -or !$orders.Add([int]$run.order)) { throw "Invalid repeated launch order: $($file.Name)" }
    $launches += [pscustomobject]@{Mode=$mode;Run=$file.BaseName;Order=[int]$run.order}
    $rows = @(Import-Csv -LiteralPath $file.FullName)
    if ($rows.Count -ne 3*[int]$run.rounds -or @($rows | Where-Object run -CNE $file.BaseName).Count) {
        throw "Run/round count mismatch: $($file.Name)"
    }
    if ($rows.Count -eq 0 -or ($rows.Count % 3) -ne 0) { throw "Incomplete benchmark: $($file.Name)" }
    foreach ($row in $rows) {
        $count = [int]$row.capes
        $samples = [int]$row.frames
        $work = [double]$row.tick_ns + [double]$row.draw_ns
        if ($count -notin 0,1,24 -or $samples -lt 100 -or [long]$row.sum_ns -lt 10e9) {
            throw "Invalid test row: $($file.Name) scene $($row.scene)"
        }
        $high = [long]$row.draw_high; $low = [long]$row.draw_low; $far = [long]$row.far_original
        if (($mode -eq 'base' -or $count -eq 0) -and ($work -ne 0 -or ($high + $low + $far) -ne 0)) {
            throw "Unexpected cape work in empty or uninstalled case"
        }
        if ($mode -eq 'balanced' -and $count -eq 24 -and ($high -lt 100 -or $low -lt 100 -or $far -lt 100)) {
            throw 'The mixed-distance scene did not hit all three BALANCED paths'
        }
        if ($mode -eq 'full' -and $count -eq 24 -and ($high -lt 100 -or $low -ne 0 -or $far -ne 0)) {
            throw 'The FULL comparison did not use high detail throughout'
        }
        $records += [pscustomobject]@{
            Mode=$mode; Capes=$count; Run=$file.BaseName; Scene=[int]$row.scene; FrameCount=$samples;
            Size="$($row.width)x$($row.height)"; P95=[double]$row.p95_ns / 1e6;
            CapeUs=$work / ($samples * 1000.0); ThreadKiB=[double]$row.allocated_bytes / ($samples * 1024.0)
        }
    }
}
$ordered = @($launches | Sort-Object Order)
for ($i = 1; $i -lt $ordered.Count; $i++) {
    if ($ordered[$i].Mode -eq $ordered[$i-1].Mode) { throw 'Independent launch order does not interleave the conditions.' }
}
if (@($records.Size | Sort-Object -Unique).Count -ne 1) { throw 'Resolution must be identical for all benchmark runs' }
function Median([double[]]$values) {
    $sorted = @($values | Sort-Object)
    if (!$sorted.Count) { throw 'Missing samples' }
    $half = [int][math]::Floor($sorted.Count / 2)
    if (($sorted.Count % 2) -eq 1) { return $sorted[$half] }
    return ($sorted[$half-1] + $sorted[$half]) / 2
}
$result = foreach ($mode in 'base','balanced','full') {
    foreach ($capes in 0,1,24) {
        $subset = @($records | Where-Object { $_.Mode -eq $mode -and $_.Capes -eq $capes })
        if ($subset.Count -lt 3 -or @($subset.Run | Sort-Object -Unique).Count -lt 2) {
            throw "Need at least three samples from at least two launches for $mode with $capes capes"
        }
        [pscustomobject]@{ Mode=$mode; Capes=$capes; Rounds=$subset.Count; P95=Median $subset.P95;
            CapeUs=if($mode -eq 'base'){ $null }else{ Median $subset.CapeUs };
            ThreadKiB=Median $subset.ThreadKiB }
    }
}
$base0=($result | Where-Object { $_.Mode -eq 'base' -and $_.Capes -eq 0 }).P95
$bal0=($result | Where-Object { $_.Mode -eq 'balanced' -and $_.Capes -eq 0 }).P95
$full24=($result | Where-Object { $_.Mode -eq 'full' -and $_.Capes -eq 24 }).CapeUs
$bal24=($result | Where-Object { $_.Mode -eq 'balanced' -and $_.Capes -eq 24 }).CapeUs
$zeroDelta=100*($bal0/$base0-1)
$clothReduction=100*(1-$bal24/$full24)
$passes = ($zeroDelta -le 3.0) -and ($clothReduction -ge 20.0)
$summary=[ordered]@{
    modId='windwovennh';version=$version;jarSha256=$hash;fixtureSha256=$fixtureHash;
    launchOrder=@($launches | Sort-Object Order | ForEach-Object Run);
    resolution=($records.Size | Select-Object -First 1); emptyP95ChangePercent=$zeroDelta;
    mixedCapeTimeReductionPercent=$clothReduction; pass=$passes; medianByScene=@($result)
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$summary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'benchmark-summary.json') -Encoding utf8NoBOM
$result | Format-Table Mode,Capes,Rounds,@{n='P95 ms';e={'{0:F3}' -f $_.P95}},`
    @{n='Cape us/frame';e={if($null -eq $_.CapeUs){'—'}else{'{0:F1}' -f $_.CapeUs}}},`
    @{n='Client KiB/frame';e={'{0:F1}' -f $_.ThreadKiB}}
Write-Output ('0-cape p95 change: {0:F2}%; 24-cape CPU-side reduction: {1:F2}%; pass={2}' -f $zeroDelta,$clothReduction,$passes)

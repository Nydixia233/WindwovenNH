[CmdletBinding()]
param(
    [ValidateSet('capture','showcase','startup','flight','flutter','review','vertical','collar','turn','host','guest')][string]$Mode = 'capture',
    [ValidateSet('baseline','balanced','full','disabled')][string]$Quality = 'balanced',
    [Parameter(Mandatory)][ValidatePattern('^[a-zA-Z0-9_-]{1,48}$')][string]$Run,
    [ValidatePattern('^(0|1|6|24)(,(0|1|6|24))*$')][string]$Scenes = '0,1,24',
    [ValidateRange(60,1800)][int]$Warmup = 240,
    [ValidateRange(120,3600)][int]$Sample = 360,
    [ValidateRange(1,6)][int]$Passes = 1,
    [ValidateRange(1,999)][int]$BenchmarkOrder,
    [ValidateRange(1024,65535)][int]$Port = 25584,
    [string]$Prism = $env:WINDWOVENNH_PRISM,
    [string]$LegacyJar,
    [switch]$CoexistLegacy,
    [switch]$PreserveConfig,
    [switch]$UpgradeWorld,
    [switch]$Shader,
    [switch]$Screenshots,
    [switch]$PrepareOnly,
    [switch]$DoNotWait
)
$ErrorActionPreference = 'Stop'
function Test-PlainPath([string]$Path) {
    $item = Get-Item -LiteralPath $Path
    if ($item.FullName -ine $Path -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Refusing linked or unexpected path: $Path"
    }
}
$project = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$prismData = (Resolve-Path -LiteralPath (Join-Path $env:APPDATA 'PrismLauncher')).Path
$role = if ($Mode -eq 'host') { 'host' } else { 'client' }
# These are the already-isolated Prism copies, not game or mod identities. Do not relocate them.
$id = "GTNH-290-Trailcloth-$role-test"
$instances = Join-Path $prismData 'instances'
$instance = Join-Path $instances $id
$game = Join-Path $instance '.minecraft'
foreach ($p in $prismData,$instances,$instance,$game,"$game\mods","$game\config","$instance\instance.cfg") { Test-PlainPath $p }
function FindClient {
    Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" |
        Where-Object { $_.CommandLine -like "*$id*natives*" -or ($role -eq 'client' -and $_.CommandLine -match '(trailcloth|windwovennh)-loopback-.*guest\.args') }
}
if (FindClient) { throw "Close $id before reconfiguring it" }
if (Select-String -LiteralPath "$instance\instance.cfg" -Pattern '(?i)^\s*Join(Server|World)OnLaunch\s*=\s*true\s*$' -Quiet) {
    throw 'Disable automatic world/server entry for this disposable copy.'
}
if (Test-Path -LiteralPath "$game\trailcloth-lab.properties") { throw 'The old fixture marker is still present; finish the old QA first.' }
if ($UpgradeWorld) {
    foreach ($p in "$game\saves","$game\saves\trailcloth-sandbox","$game\saves\trailcloth-sandbox\level.dat") { Test-PlainPath $p }
}
if (!$PrepareOnly -and !$Prism) {
    $Prism = (Get-Command prismlauncher.exe -ErrorAction SilentlyContinue).Source
    if (!$Prism) { throw 'Pass -Prism or set WINDWOVENNH_PRISM to your Prism Launcher executable.' }
}
if (!$PrepareOnly -and !(Test-Path -LiteralPath $Prism -PathType Leaf)) { throw 'Prism Launcher executable not found.' }
$out = Join-Path $game 'windwovennh-lab'
New-Item -ItemType Directory -Path $out -Force | Out-Null
Test-PlainPath $out
if ((Test-Path -LiteralPath "$out\$Run.csv") -or (Test-Path -LiteralPath "$out\$Run.done") -or (Test-Path -LiteralPath "$out\inactive-$Run")) {
    throw "Run name already used: $Run"
}
$version = & (Join-Path $PSScriptRoot 'Get-WindwovenNHVersion.ps1')
$newName = "windwovennh-$version.jar"
$fixture = "windwovennh-fixture-$version.jar"
$qaJar = Join-Path $project "build\qa\$fixture"
$withMod = $Quality -ne 'baseline' -and $Mode -ne 'host'
if ($BenchmarkOrder) {
    $prefix = if ($Quality -eq 'baseline') { 'base' } else { $Quality }
    if ($Mode -ne 'capture' -or $Scenes -ne '0,1,24' -or $Quality -eq 'disabled' -or $LegacyJar -or $Shader -or
        $Screenshots -or $PrepareOnly -or $DoNotWait -or $PreserveConfig -or $Run -cnotmatch "^$prefix-[a-z][a-z0-9]*$") {
        throw 'Benchmark recording requires a complete, shaders-off 0,1,24 capture and base-/balanced-/full- run label.'
    }
    $candidate = Join-Path $project "build\libs\$newName"
    $candidateSha = (Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash.ToLowerInvariant()
    $fixtureSha = (Get-FileHash -LiteralPath $qaJar -Algorithm SHA256).Hash.ToLowerInvariant()
    $measurements = Join-Path $project "build\measurements\windwovennh\$version"
    New-Item -ItemType Directory -Path $measurements -Force | Out-Null
    Test-PlainPath $measurements
    $record = Join-Path $measurements 'candidate.json'
    if (Test-Path -LiteralPath $record -PathType Leaf) {
        $expected = Get-Content -LiteralPath $record -Raw | ConvertFrom-Json
        if ($expected.modId -cne 'windwovennh' -or $expected.version -cne $version -or
            $expected.jarSha256 -cne $candidateSha -or $expected.fixtureSha256 -cne $fixtureSha) {
            throw 'The current binaries differ from the recorded benchmark candidate.'
        }
    } else {
        [ordered]@{modId='windwovennh';version=$version;jarSha256=$candidateSha;fixtureSha256=$fixtureSha} |
            ConvertTo-Json | Set-Content -LiteralPath $record -Encoding utf8NoBOM
    }
    if ((Test-Path -LiteralPath (Join-Path $measurements "$Run.csv")) -or
        (Test-Path -LiteralPath (Join-Path $measurements "$Run.json"))) { throw 'Benchmark run already recorded.' }
}
if ((!$withMod -and ($LegacyJar -or $CoexistLegacy)) -or ($CoexistLegacy -and (!$LegacyJar -or !$PreserveConfig -or $Mode -ne 'capture'))) {
    throw 'Coexistence requires an explicit legacy JAR, capture mode and -PreserveConfig; baseline/host must have neither mod.'
}
if ($LegacyJar) {
    $legacy = Get-Item -LiteralPath $LegacyJar
    if ($legacy.PSIsContainer -or $legacy.Name -cnotmatch '^trailcloth-\d+\.\d+\.\d+\.jar$' -or ($legacy.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'Specify a production Trailcloth JAR, never a fixture or dev build.'
    }
}
$install = [ordered]@{}
if ($withMod -and (!$LegacyJar -or $CoexistLegacy)) { $install[$newName] = Join-Path $project "build\libs\$newName" }
if ($LegacyJar) { $install[$legacy.Name] = $legacy.FullName }
$install[$fixture] = $qaJar
foreach ($p in $install.Values) {
    if (!(Test-Path -LiteralPath $p -PathType Leaf)) { throw "Build the requested production and fixture JARs first: $p" }
    Test-PlainPath $p
}
$settings = if ($LegacyJar -and !$CoexistLegacy) { 'trailcloth.cfg' } else { 'windwovennh.cfg' }
$shaderConfig = Join-Path "$game\config" 'shaders.properties'
Test-PlainPath $shaderConfig
$shaderName = 'ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5'
if ($Shader -and !(Test-Path -LiteralPath "$game\shaderpacks\$shaderName")) { throw "Cannot find $shaderName" }
$marker = Join-Path $game 'windwovennh-lab.properties'
foreach ($p in $marker,"$game\config\$settings") { if (Test-Path -LiteralPath $p) { Test-PlainPath $p } }
$modPattern = '^(trailcloth|windwovennh)-(fixture-)?[0-9]+\.[0-9]+\.[0-9]+\.jar$'
$active = @(Get-ChildItem -LiteralPath "$game\mods" -File | Where-Object Name -Match $modPattern)
foreach ($file in $active) { Test-PlainPath $file.FullName }
foreach ($name in $install.Keys) {
    $p = Join-Path "$game\mods" $name
    if (Test-Path -LiteralPath $p) { Test-PlainPath $p; if (!(Test-Path -LiteralPath $p -PathType Leaf)) { throw "Expected a mod file: $p" } }
}
$inactive = Join-Path $out "inactive-$Run"
New-Item -ItemType Directory -Path $inactive | Out-Null
Test-PlainPath $inactive
foreach ($file in $active) {
    if (!$install.Contains($file.Name)) { Move-Item -LiteralPath $file.FullName -Destination (Join-Path $inactive $file.Name) }
}
foreach ($name in $install.Keys) { Copy-Item -LiteralPath $install[$name] -Destination (Join-Path "$game\mods" $name) -Force }
if ($withMod -and !$PreserveConfig) {
    $enabled = if ($Quality -eq 'disabled') { 'false' } else { 'true' }
    $density = if ($Quality -eq 'full') { 'FULL' } else { 'BALANCED' }
    @"
cloth {
    B:enabled=$enabled
    S:quality=$density
}
"@ | Set-Content -LiteralPath "$game\config\$settings" -Encoding utf8NoBOM
}
$shaderSwitch = $Shader.IsPresent.ToString().ToLowerInvariant()
(Get-Content -LiteralPath $shaderConfig) -replace '^enableShaders=.*$', "enableShaders=$shaderSwitch" `
    -replace '^shaderPack=.*$', "shaderPack=$shaderName" | Set-Content -LiteralPath $shaderConfig -Encoding utf8NoBOM
$show = $Screenshots.IsPresent.ToString().ToLowerInvariant()
$coexist = $CoexistLegacy.IsPresent.ToString().ToLowerInvariant()
$upgrade = $UpgradeWorld.IsPresent.ToString().ToLowerInvariant()
@"
disposableInstance=true
mode=$Mode
run=$Run
scenes=$Scenes
warmupTicks=$Warmup
sampleTicks=$Sample
passes=$Passes
port=$Port
screenshots=$show
requireLegacyOnly=$coexist
upgradeWorld=$upgrade
"@ | Set-Content -LiteralPath $marker -Encoding utf8NoBOM
if ($PrepareOnly) { return }
$name = switch ($Mode) { 'host' { 'WindwovenHost' } 'guest' { 'WindwovenGuest' } default { 'WindwovenQA' } }
$started = Get-Date
Start-Process -FilePath $Prism -ArgumentList @('--launch',$id,'--offline',$name) -WindowStyle Hidden | Out-Null
if ($DoNotWait) { return }
$limit = $started.AddMinutes(16)
$seen = $false
do {
    Start-Sleep -Seconds 4
    $running = [bool](FindClient)
    $seen = $seen -or $running
    $finished = (Test-Path -LiteralPath "$out\$Run.done" -PathType Leaf) -and !$running
    $newCrash = Get-ChildItem -LiteralPath "$game\crash-reports" -Filter '*-client.txt' -File -ErrorAction SilentlyContinue |
        Where-Object LastWriteTime -gt $started | Select-Object -First 1
    if ($newCrash) { throw "Client crashed during $Run. See $($newCrash.FullName)" }
    if ($seen -and !$running -and !$finished) { throw "Client exited before completion: $Run" }
} until ($finished -or (Get-Date) -ge $limit)
if (!$finished) { throw "Run $Run did not finish; see the test instance's logs" }
Get-Content -LiteralPath "$out\$Run.done"
if (Test-Path -LiteralPath "$out\$Run.csv" -PathType Leaf) { Get-Content -LiteralPath "$out\$Run.csv" }

if ($BenchmarkOrder) {
    $log = Join-Path $game 'logs\fml-client-latest.log'
    $expectedPresence = if ($withMod) { 'windwovennh=true, legacy=false' } else { 'windwovennh=false, legacy=false' }
    if (!(Select-String -LiteralPath $log -SimpleMatch "capture $Run armed; $expectedPresence" -Quiet)) {
        throw 'The client did not load the expected benchmark mod list.'
    }
    $installedSha = $null
    if ($withMod) {
        $installedSha = (Get-FileHash -LiteralPath "$game\mods\$newName" -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($installedSha -cne $candidateSha -or !(Select-String -LiteralPath $log -SimpleMatch ('WindwovenNH: enabled=true, quality=' + $Quality.ToUpperInvariant()) -Quiet)) {
            throw 'The installed benchmark binary or quality is not the candidate.'
        }
    }
    if ((Get-FileHash -LiteralPath "$game\mods\$fixture" -Algorithm SHA256).Hash -ine $fixtureSha) { throw 'Fixture hash changed.' }
    $data = Join-Path $measurements "$Run.csv"
    Copy-Item -LiteralPath "$out\$Run.csv" -Destination $data
    [ordered]@{modId='windwovennh';version=$version;candidateSha256=$candidateSha;installedSha256=$installedSha;fixtureSha256=$fixtureSha;
        quality=$Quality;order=$BenchmarkOrder;csvSha256=(Get-FileHash -LiteralPath $data -Algorithm SHA256).Hash.ToLowerInvariant();rounds=$Passes} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $measurements "$Run.json") -Encoding utf8NoBOM
}

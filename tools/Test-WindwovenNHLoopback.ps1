[CmdletBinding()]
param(
    [ValidateRange(1024,65535)][int]$Port = 25584,
    [string]$Prism = $env:WINDWOVENNH_PRISM
)
$ErrorActionPreference = 'Stop'
$root = Join-Path $env:APPDATA 'PrismLauncher'
$clientId = 'GTNH-290-Trailcloth-client-test'
$hostId = 'GTNH-290-Trailcloth-host-test'
$client = Join-Path $root "instances\$clientId"
$hostCopy = Join-Path $root "instances\$hostId"
$clientGame = Join-Path $client '.minecraft'
$hostGame = Join-Path $hostCopy '.minecraft'
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "QA port $Port is already in use" }
$stamp = Get-Date -Format yyyyMMdd-HHmmssfff
$sessionDir = Join-Path $env:TEMP "windwovennh-loopback-$stamp"
New-Item -ItemType Directory -Path $sessionDir | Out-Null
$prefix = "$stamp-tcp"
$lab = Join-Path $PSScriptRoot 'Invoke-WindwovenNHLab.ps1'

# The two instances share existing game libraries but not saves, config, mods, or launcher credentials.
# Reuse just the classpath of an earlier disposable Prism client launch. All session arguments below are synthetic.
$launch = Get-ChildItem -LiteralPath (Join-Path $root 'logs') -Filter 'PrismLauncher-*.log' -File |
    Sort-Object LastWriteTime -Descending | ForEach-Object {
        Get-Content -LiteralPath $_.FullName | Where-Object { $_ -match $clientId -and $_ -match ' -cp .+ org\.prismlauncher\.EntryPoint' } |
            Select-Object -Last 1
    } | Select-Object -First 1
$match = [regex]::Match([string]$launch, ' -cp (?<classpath>.+?) org\.prismlauncher\.EntryPoint')
if (!$match.Success) { throw 'Launch the disposable client from Prism once first so its classpath is available' }
$classpath = ($match.Groups['classpath'].Value.Split(';') | Where-Object { $_ -notmatch '[/\\]NewLaunch\.jar$' }) -join ';'
$jvm = (Get-Content -LiteralPath "$client\patches\me.eigenraven.lwjgl3ify.forgepatches.json" -Raw | ConvertFrom-Json).'+jvmArgs'
$entry = (Get-Content -LiteralPath "$client\patches\me.eigenraven.lwjgl3ify.launchargs.json" -Raw | ConvertFrom-Json).mainClass
$launcher = (Get-Content -LiteralPath "$client\instance.cfg" | Where-Object { $_ -match '^JavaPath=' } | Select-Object -First 1) -replace '^JavaPath=', ''
$java = Join-Path (Split-Path $launcher -Parent) 'java.exe'
if (!(Test-Path -LiteralPath $java) -or !$entry -or @($jvm).Count -lt 20) { throw 'Incomplete Java 25 pack launch metadata' }
function Q([string]$value) { '"' + $value.Replace('\','/') + '"' }
$arguments = @('-Xms512m','-Xmx6144m','-Duser.language=en',(Q "-Djava.library.path=$client\natives")) + @($jvm) + @(
    '-cp',(Q $classpath),$entry,'--username','WindwovenGuest','--version','1.7.10','--gameDir',(Q $clientGame),
    '--assetsDir',(Q (Join-Path $root 'assets')),'--assetIndex','1.7.10','--uuid','262bd9b050064759b917766d46548fb8',
    '--accessToken','0','--userProperties','{}','--userType','legacy','--width','854','--height','480',
    '--tweakClass','cpw.mods.fml.common.launcher.FMLTweaker')
$argFile = Join-Path $sessionDir 'guest.args'
$arguments | Set-Content -LiteralPath $argFile -Encoding utf8NoBOM
& $lab -Mode guest -Quality balanced -Run "$prefix-guest" -Port $Port -PrepareOnly
& $lab -Mode host -Quality baseline -Run "$prefix-host" -Port $Port -Prism $Prism -DoNotWait
$hostWait = (Get-Date).AddMinutes(6)
do {
    Start-Sleep -Seconds 3
    $listener = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
} until ($listener -or (Get-Date) -gt $hostWait)
if (!$listener) { throw 'Disposable host did not become ready' }
$hostProc = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
if (!$hostProc -or $hostProc.CommandLine -notlike "*$hostId*natives*") { throw 'Unexpected loopback listener owner' }
$guest = Start-Process -FilePath $java -ArgumentList ('"@' + $argFile + '"') -WorkingDirectory $clientGame `
    -RedirectStandardOutput (Join-Path $sessionDir 'guest.out.txt') -RedirectStandardError (Join-Path $sessionDir 'guest.err.txt') `
    -WindowStyle Hidden -PassThru
Write-Output "Running private loopback test on 127.0.0.1:$Port"
$deadline = (Get-Date).AddMinutes(9)
$guestResult = Join-Path $clientGame "windwovennh-lab\$prefix-guest.done"
$hostResult = Join-Path $hostGame "windwovennh-lab\$prefix-host.done"
do {
    Start-Sleep -Seconds 4
    $pass = (Test-Path -LiteralPath $guestResult) -and (Test-Path -LiteralPath $hostResult)
} until ($pass -or (Get-Date) -gt $deadline)
if (!$pass) { throw "Loopback not confirmed; test logs: $sessionDir" }
$lines = @(Get-Content -LiteralPath $hostResult,$guestResult)
if ($lines.Count -ne 2 -or $lines[0] -cne 'PASS: mod-free host accepted installed guest' -or
    $lines[1] -cne 'PASS: installed client spent 60 ticks in remote world') { throw 'Invalid loopback completion markers' }
$version = & (Join-Path $PSScriptRoot 'Get-WindwovenNHVersion.ps1')
$evidence = Join-Path $PSScriptRoot "..\build\measurements\windwovennh\$version"
$build = Join-Path $PSScriptRoot "..\build\libs\windwovennh-$version.jar"
$installed = Join-Path $clientGame "mods\windwovennh-$version.jar"
$sha = (Get-FileHash -LiteralPath $installed -Algorithm SHA256).Hash.ToLowerInvariant()
if ((Get-FileHash -LiteralPath $build -Algorithm SHA256).Hash -ine $sha) { throw 'Loopback binary changed' }
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$lines | Set-Content -LiteralPath (Join-Path $evidence 'loopback.txt') -Encoding utf8NoBOM
[ordered]@{modId='windwovennh';version=$version;jarSha256=$sha;pass=$true} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'loopback.json') -Encoding utf8NoBOM
$lines

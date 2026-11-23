[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$version = & (Join-Path $PSScriptRoot 'Get-WindwovenNHVersion.ps1')
$jar = Join-Path $root "build/libs/windwovennh-$version.jar"
$source = Join-Path $root "build/distributions/windwovennh-$version-source.zip"
function Read-TextEntry($Zip, [string]$Name) {
    $entry = $Zip.GetEntry($Name)
    if (!$entry) { throw "Missing archive member: $Name" }
    $reader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::UTF8, $true)
    try { $reader.ReadToEnd() } finally { $reader.Dispose() }
}
$z = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    $classes = '^io/github/windwovennh/(WindwovenNH|LegacyConfig|client/(BendsAttachment(\$Links)?|ModelSpace|PlayerCloths(\$Garment)?)|cloth/(AeroPreset|BodyContact|CapeFrame|ClothSurface(\$VertexOut)?|Density|Flow|WovenCloth(\$(Seam|Weave))?)|mixin/(CapeCall|BatchStatus|EvenMoBendsCapeCall))\.class$'
    $resources = @('META-INF/MANIFEST.MF','META-INF/LICENSE','mcmod.info','mixins.windwovennh.json','mixins.windwovennh.refmap.json')
    $files = @($z.Entries | Where-Object Name | ForEach-Object FullName)
    $other = @($files | Where-Object { $_ -cnotmatch $classes -and $_ -cnotin $resources })
    if ($other.Count -or 'io/github/windwovennh/WindwovenNH.class' -cnotin $files -or
        'io/github/windwovennh/LegacyConfig.class' -cnotin $files -or 'io/github/windwovennh/mixin/CapeCall.class' -cnotin $files -or
        'io/github/windwovennh/mixin/EvenMoBendsCapeCall.class' -cnotin $files -or
        'io/github/windwovennh/client/BendsAttachment.class' -cnotin $files) {
        throw "Missing production classes or extra JAR payload: $other"
    }
    $info = (Read-TextEntry $z 'mcmod.info' | ConvertFrom-Json)[0]
    $name = 'WindwovenNH'
    if ($info.modid -cne 'windwovennh' -or $info.name -cne $name -or $info.version -cne $version -or $info.description -notmatch 'unofficial') {
        throw 'Incorrect public mod metadata.'
    }
    if ((Read-TextEntry $z 'META-INF/MANIFEST.MF') -notmatch '(?m)^MixinConfigs: mixins\.windwovennh\.json\r?$') { throw 'Wrong Mixin manifest.' }
    $mixin = Read-TextEntry $z 'mixins.windwovennh.json' | ConvertFrom-Json
    if ('EvenMoBendsCapeCall' -cnotin $mixin.client -or $mixin.package -cne 'io.github.windwovennh.mixin' -or $mixin.refmap -cne 'mixins.windwovennh.refmap.json' -or
        (Read-TextEntry $z 'mixins.windwovennh.refmap.json') -match 'io[/\.]github[/\.]trailcloth') { throw 'Wrong Mixin namespace/refmap.' }
    $license = Get-Content -LiteralPath (Join-Path $root 'LICENSE') -Raw
    if ((Read-TextEntry $z 'META-INF/LICENSE').Trim() -cne $license.Trim()) { throw 'Incorrect copyright/license payload' }
} finally { $z.Dispose() }

$z = [IO.Compression.ZipFile]::OpenRead($source)
try {
    $top = "WindwovenNH-$version/"
    foreach ($rel in 'README.md','RELEASE-QA.md','PROVENANCE.md','LICENSE','licenses/GTNH-ExampleMod-MIT.txt','licenses/Gradle-Wrapper-Apache-2.0.txt',
        'gradle/wrapper/gradle-wrapper.jar','src/main/java/io/github/windwovennh/WindwovenNH.java') {
        if (!$z.GetEntry($top + $rel)) { throw "Missing source entry: $rel" }
    }
    $report = Get-Content -LiteralPath (Join-Path $root 'RELEASE-QA.md') -Raw
    if ((Read-TextEntry $z ($top + 'RELEASE-QA.md')).Trim() -cne $report.Trim()) { throw 'The source ZIP has an obsolete report.' }
    $private = '(?i)([a-z]:[/\\]users[/\\]|/home/[a-z0-9_.-]+/|/mnt[/]data/|ghp_[a-z0-9]{32,}|github_pat_[a-z0-9_]{32,})'
    foreach ($entry in $z.Entries | Where-Object Name) {
        $n = $entry.FullName
        if (!$n.StartsWith($top,[StringComparison]::Ordinal)) { throw "Unexpected archive root: $n" }
        $rel = $n.Substring($top.Length)
        if ($rel -ceq 'gradle/wrapper/gradle-wrapper.jar') { continue }
        if ($rel -match '(?i)(^|/)(assets|build|\.minecraft|instances|saves|logs|crash-reports|screenshots)/|\.(jar|zip|png|gif|mp4|args|log)$') {
            throw "Unpublishable archive member: $n"
        }
        if ((Read-TextEntry $z $n) -match $private) { throw "Personal path or possible credential in $n" }
    }
} finally { $z.Dispose() }
'PASS: production metadata, clean JAR, and public-source payload'

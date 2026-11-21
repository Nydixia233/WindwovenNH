[CmdletBinding()]
param([Parameter(Mandatory)][string]$Directory)
$ErrorActionPreference='Stop'
function Median([double[]]$Data) {
    $s=@($Data | Sort-Object)
    if (!$s.Count) { throw 'Empty measurement series' }
    $m=[int][math]::Floor($s.Count / 2)
    if ($s.Count % 2) { return $s[$m] }
    ($s[$m-1]+$s[$m])/2
}
$all=@(foreach($f in Get-ChildItem -LiteralPath $Directory -File -Filter 'ref-*.csv') {
    if ($f.BaseName -notmatch '^ref-(base|old|new|full)-[a-z]$') { continue }
    $mode=$Matches[1]
    foreach($r in Import-Csv -LiteralPath $f.FullName) {
        $n=[int]$r.capes; $frames=[long]$r.frames
        $high=[long]$r.step_high; $low=[long]$r.step_low; $far=[long]$r.far_original
        if ($n -notin 0,1,24 -or $frames -lt 100 -or [long]$r.sum_ns -lt 12e9) { throw "Invalid run $($f.Name)" }
        if (($mode -eq 'base' -or $n -eq 0) -and ($high+$low+$far -ne 0 -or [long]$r.tick_ns+[long]$r.draw_ns -ne 0)) {
            throw 'Unexpected cloth work without capes or without Trailcloth'
        }
        if ($n -eq 24 -and $mode -in 'new','old' -and ($high -le 0 -or $low -le 0 -or $far -le 0)) { throw 'The mixed-distance paths did not all run' }
        if ($mode -eq 'full' -and ($n -ne 24 -or $high -le 0 -or $low -ne 0 -or $far -ne 0)) { throw 'FULL did not use only high detail in the mixed-distance scene' }
        [pscustomobject]@{Mode=$mode;Run=$f.BaseName;Scene=$n;Size="$($r.width)x$($r.height)";
            P95=[double]$r.p95_ns/1e6;CapeUs=([double]$r.tick_ns+[double]$r.draw_ns)/($frames*1000);
            ThreadKiB=[double]$r.allocated_bytes/($frames*1024)}
    }
})
if (@($all.Size | Sort-Object -Unique).Count -ne 1) { throw 'All measurement resolutions must match' }
$stats=@(foreach($mode in 'base','old','new','full') {
    $scenes=if ($mode -eq 'full') { @(24) } else { @(0,1,24) }
    foreach($n in $scenes) {
        $r=@($all | Where-Object {$_.Mode -eq $mode -and $_.Scene -eq $n})
        if ($r.Count -lt 4 -or @($r.Run | Sort-Object -Unique).Count -lt 2) { throw "Need four $mode / $n rounds from two separate launches" }
        [pscustomobject]@{Mode=$mode;Capes=$n;Rounds=$r.Count;P95ms=Median $r.P95;
            CapeUsPerFrame=if($mode -eq 'base'){$null}else{Median $r.CapeUs};
            ClientThreadKiBPerFrame=Median $r.ThreadKiB}
    }
})
$base=($stats|Where-Object {$_.Mode -eq 'base' -and $_.Capes -eq 0}).P95ms
$new0=($stats|Where-Object {$_.Mode -eq 'new' -and $_.Capes -eq 0}).P95ms
$old24=($stats|Where-Object {$_.Mode -eq 'old' -and $_.Capes -eq 24}).CapeUsPerFrame
$new24=($stats|Where-Object {$_.Mode -eq 'new' -and $_.Capes -eq 24}).CapeUsPerFrame
$full24=($stats|Where-Object {$_.Mode -eq 'full' -and $_.Capes -eq 24}).CapeUsPerFrame
$empty=100*($new0/$base-1); $change=100*($new24/$old24-1); $save=100*(1-$new24/$full24)
$summary=[ordered]@{resolution=$all[0].Size;emptyP95Percent=$empty;capeCpuChangeVs014Percent=$change;
    capeCpuReductionVsFullPercent=$save;emptyGate=($empty -le 3);lodGate=($save -ge 20);medianByScene=$stats}
$summary|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $Directory 'calibrated-benchmark-summary.json') -Encoding utf8NoBOM
$stats|Format-Table -AutoSize
'0 cape whole-frame P95 vs absent: {0:+0.0;-0.0;0.0}%; 24 cape CPU vs 0.1.4: {1:+0.0;-0.0;0.0}%; 24 cape CPU saved vs FULL: {2:F1}%' -f $empty,$change,$save

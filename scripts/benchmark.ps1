param(
    [string]$BaseUrl = "http://localhost:8080",
    [long]$CategoryId = 1001,
    [int]$Page = 3000,
    [int]$Size = 10,
    [int]$WarmupIterations = 5,
    [int]$Iterations = 30
)

$ErrorActionPreference = "Stop"

if ($WarmupIterations -lt 0 -or $Iterations -lt 1) {
    throw "WarmupIterations must be non-negative and Iterations must be positive"
}

function Measure-Strategy([string]$Strategy) {
    $uri = "$BaseUrl/api/v1/categories/$CategoryId/media?strategy=$Strategy&page=$Page&size=$Size"
    for ($i = 0; $i -lt $WarmupIterations; $i++) {
        Invoke-RestMethod -Uri $uri | Out-Null
    }

    $durations = for ($i = 0; $i -lt $Iterations; $i++) {
        $watch = [Diagnostics.Stopwatch]::StartNew()
        Invoke-RestMethod -Uri $uri | Out-Null
        $watch.Stop()
        $watch.Elapsed.TotalMilliseconds
    }

    $sorted = @($durations | Sort-Object)
    $middle = [Math]::Floor($sorted.Count / 2)
    $median = if ($sorted.Count % 2 -eq 0) {
        ($sorted[$middle - 1] + $sorted[$middle]) / 2
    } else {
        $sorted[$middle]
    }
    $p95Index = [Math]::Min(
        $sorted.Count - 1,
        [Math]::Ceiling($sorted.Count * 0.95) - 1)

    [pscustomobject]@{
        Strategy = $Strategy
        MinMs = [Math]::Round($sorted[0], 3)
        MedianMs = [Math]::Round($median, 3)
        P95Ms = [Math]::Round($sorted[$p95Index], 3)
        Iterations = $Iterations
    }
}

Write-Host "Client-observed timings; results depend on this machine, data distribution, network, and cache warmth."
@("offset", "zset") | ForEach-Object { Measure-Strategy $_ } | Format-Table -AutoSize

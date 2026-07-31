# Forge Status: genera to-do.md desde docs/forge-todo.json.
#
# Para cada item deriva su estado real:
#   - status "auto" + test:   corre los tests y marca done si todos pasan.
#   - status "auto" + commit: busca el ID en git log (convencion "TD-XXX").
#   - status "done":           respeta (marcado manualmente).
#   - status "pending":        respeta (pendiente).
#
# Uso:
#   .\forge-status.ps1              # corre tests si hay items auto+test, genera to-do.md
#   .\forge-status.ps1 -SkipTests   # no corre tests, usa resultados existentes

param(
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

$repoRoot = $PSScriptRoot
$jsonFile = Join-Path $repoRoot 'docs\forge-todo.json'
$todoFile = Join-Path $repoRoot 'to-do.md'
$testResultsDir = Join-Path $repoRoot 'app\build\test-results\testDebugUnitTest'

$data = Get-Content $jsonFile -Raw | ConvertFrom-Json
$items = $data.items

# --- Fase 1: correr tests si hay items auto+test y no se skipeo ---

$autoTestItems = $items | Where-Object { $_.status -eq 'auto' -and $_.test }
$needTests = $autoTestItems -and -not $SkipTests

if ($needTests) {
    Write-Host "Running tests for forge-status..."
    & "$repoRoot\gradlew.bat" test --no-daemon --console=plain 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "WARN -> tests failed; items auto+test se marcaran segun resultados" -ForegroundColor Yellow
    }
}

# --- Fase 2: derivar estado de cada item ---

function Test-Class-Passes($className) {
    $xmlFile = Join-Path $testResultsDir "TEST-$className.xml"
    if (-not (Test-Path $xmlFile)) { return $false }
    [xml]$xml = Get-Content $xmlFile -Raw
    $failures = [int]$xml.testsuite.failures
    $errors = [int]$xml.testsuite.errors
    return ($failures + $errors) -eq 0
}

function Find-Commit-With-Id($id) {
    $log = & git -C $repoRoot log --oneline --all 2>$null
    return ($log | Where-Object { $_ -match $id }).Count -gt 0
}

$derived = @()
foreach ($item in $items) {
    $state = $item.status
    $verifiedBy = ""

    if ($item.status -eq 'auto') {
        if ($item.test) {
            $allPass = $true
            foreach ($tc in $item.test) {
                if (-not (Test-Class-Passes $tc)) { $allPass = $false; break }
            }
            $state = if ($allPass) { 'done' } else { 'pending' }
            $verifiedBy = "tests"
        } elseif ($item.commit) {
            $found = Find-Commit-With-Id $item.id
            $state = if ($found) { 'done' } else { 'pending' }
            $verifiedBy = "commit"
        }
    }

    $derived += [PSCustomObject]@{
        id = $item.id
        title = $item.title
        category = $item.category
        state = $state
        verifiedBy = $verifiedBy
        manual = $item.manual
    }
}

# --- Fase 3: generar to-do.md ---

$done = $derived | Where-Object { $_.state -eq 'done' }
$pending = $derived | Where-Object { $_.state -eq 'pending' }

$categories = ($derived | Select-Object -ExpandProperty category -Unique) | Sort-Object

$lines = @()
$lines += "# To-Do - Athletic"
$lines += ""
$lines += "> Generado automaticamente por ``forge-status.ps1`` desde ``docs/forge-todo.json``."
$lines += "> No editar directamente; actualizar el JSON y regenerar con ``.\forge-status.ps1``."
$lines += "> Convencion de commits: ``feat: TD-XXX ...`` / ``fix: TD-XXX ...``."
$lines += ""

$doneCount = $done.Count
$pendingCount = $pending.Count
$total = $derived.Count
$lines += "Progreso: **$doneCount / $total** hechos, $pendingCount pendientes."
$lines += ""

if ($pending.Count -gt 0) {
    $lines += "## Pendientes"
    $lines += ""
    foreach ($cat in $categories) {
        $catItems = $pending | Where-Object { $_.category -eq $cat }
        if ($catItems.Count -eq 0) { continue }
        $lines += "### $cat"
        $lines += ""
        foreach ($item in $catItems) {
            $lines += "- [ ] **$($item.id)** $($item.title)"
            if ($item.manual) {
                $lines += "  - $($item.manual)"
            }
        }
        $lines += ""
    }
}

if ($done.Count -gt 0) {
    $lines += "## Hechos"
    $lines += ""
    foreach ($cat in $categories) {
        $catItems = $done | Where-Object { $_.category -eq $cat }
        if ($catItems.Count -eq 0) { continue }
        $lines += "### $cat"
        $lines += ""
        foreach ($item in $catItems) {
            $suffix = if ($item.verifiedBy) { " - verificado por $($item.verifiedBy)" } else { "" }
            $lines += "- [x] **$($item.id)** $($item.title)$suffix"
        }
        $lines += ""
    }
}

Set-Content $todoFile ($lines -join "`n") -NoNewline
Write-Host "OK -> to-do.md generado ($doneCount done, $pendingCount pending, $total total)"

# --- Resumen en consola ---
Write-Host ""
foreach ($item in $derived) {
    $mark = if ($item.state -eq 'done') { '[x]' } else { '[ ]' }
    $ver = if ($item.verifiedBy) { " ($($item.verifiedBy))" } elseif ($item.state -eq 'done') { " (manual)" } else { "" }
    Write-Host "  $mark $($item.id) $($item.title)$ver"
}

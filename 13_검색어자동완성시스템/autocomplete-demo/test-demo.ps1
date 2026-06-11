$ErrorActionPreference = "Stop"

$source = $PSScriptRoot
$target = Join-Path $env:TEMP "system-design-autocomplete-demo"

if (Test-Path -LiteralPath $target) {
    Remove-Item -LiteralPath $target -Recurse -Force
}

New-Item -ItemType Directory -Path $target | Out-Null
robocopy $source $target /E /XD build .gradle | Out-Host
if ($LASTEXITCODE -ge 8) {
    throw "robocopy failed with exit code $LASTEXITCODE"
}

Push-Location $target
try {
    & .\gradlew.bat clean test
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}

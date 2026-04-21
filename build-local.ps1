param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs = @(':app:assembleDebug')
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleBat = 'D:\IDE-Tools\gradle-9.3.1\bin\gradle.bat'

if (-not (Test-Path $gradleBat)) {
    Write-Error "Gradle executable not found: $gradleBat"
}

$env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user-home'

Write-Host "Using GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
Write-Host "Using Gradle=$gradleBat"

Push-Location $projectRoot
try {
    & $gradleBat @GradleArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}

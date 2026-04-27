param(
  [string]$DbHost = "localhost",
  [int]$DbPort = 5432,
  [string]$DbName = "prestockdb",
  [string]$DbUser = "postgres",
  [string]$DbPassword = "postgres",
  [switch]$PromptPassword,
  [switch]$SkipFlyway,
  [switch]$Seed
)

$ErrorActionPreference = "Stop"

function Convert-SecureToPlain([System.Security.SecureString]$secure) {
  $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
  try {
    return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
  }
  finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
  }
}

if ($PromptPassword -or [string]::IsNullOrWhiteSpace($DbPassword)) {
  Write-Host "Ingresa la clave de PostgreSQL para el usuario '$DbUser'" -ForegroundColor Yellow
  $securePwd = Read-Host -AsSecureString "DB password"
  $DbPassword = Convert-SecureToPlain $securePwd
}

$env:SPRING_PROFILES_ACTIVE = "local"
$env:DB_URL = "jdbc:postgresql://$DbHost`:$DbPort/$DbName"
$env:DB_USERNAME = $DbUser
$env:DB_PASSWORD = $DbPassword

if ($SkipFlyway) {
  $env:SPRING_FLYWAY_ENABLED = "false"
  Write-Host "Flyway deshabilitado para este arranque." -ForegroundColor Yellow
}

if ($Seed) {
  $seedFile = Join-Path $PSScriptRoot "scripts/seed-test-data.sql"
  $psqlPath = "C:/Program Files/PostgreSQL/18/bin/psql.exe"

  if (-not (Test-Path $seedFile)) {
    throw "No se encontro el archivo de seed: $seedFile"
  }
  if (-not (Test-Path $psqlPath)) {
    throw "No se encontro psql en: $psqlPath"
  }

  Write-Host "Aplicando datos de prueba desde: $seedFile" -ForegroundColor Cyan
  $env:PGPASSWORD = $DbPassword
  & $psqlPath -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $seedFile
}

Write-Host ""
Write-Host "Iniciando proStock backend..." -ForegroundColor Cyan
Write-Host "SPRING_PROFILES_ACTIVE=$env:SPRING_PROFILES_ACTIVE"
Write-Host "DB_URL=$env:DB_URL"
Write-Host "DB_USERNAME=$env:DB_USERNAME"
Write-Host ""

if (-not (Test-Path ".\gradlew.bat")) {
  throw "No se encontro gradlew.bat en la raiz del proyecto."
}

.\gradlew.bat bootRun

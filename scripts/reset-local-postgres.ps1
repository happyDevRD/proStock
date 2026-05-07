<#
  Borra y recrea la base de datos local (Flyway volvera a migrar al siguiente bootRun).
  Requiere psql en PATH o en la ruta tipica de PostgreSQL para Windows.

  Ejemplo (valores por defecto como start-dev.ps1):
    .\scripts\reset-local-postgres.ps1

  Otro puerto/usuario:
    .\scripts\reset-local-postgres.ps1 -DbPort 5433 -DbUser prestock -DbPassword 'tu-clave'
#>
param(
  [string]$DbHost = "localhost",
  [int]$DbPort = 5432,
  [string]$DbName = "prestockdb",
  [string]$DbUser = "postgres",
  [string]$DbPassword = "postgres",
  [switch]$PromptPassword
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

function Resolve-PsqlPath {
  $cmd = Get-Command psql -ErrorAction SilentlyContinue
  if ($cmd) {
    return $cmd.Source
  }
  $candidates = @(
    "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    "C:\Program Files\PostgreSQL\17\bin\psql.exe",
    "C:\Program Files\PostgreSQL\16\bin\psql.exe",
    "C:\Program Files\PostgreSQL\15\bin\psql.exe"
  )
  foreach ($p in $candidates) {
    if (Test-Path $p) {
      return $p
    }
  }
  throw "No se encontro psql. Instale PostgreSQL o agregue psql al PATH."
}

if ($PromptPassword -or [string]::IsNullOrWhiteSpace($DbPassword)) {
  Write-Host "Clave de PostgreSQL para el usuario '$DbUser'" -ForegroundColor Yellow
  $securePwd = Read-Host -AsSecureString "DB password"
  $DbPassword = Convert-SecureToPlain $securePwd
}

$psql = Resolve-PsqlPath
$env:PGPASSWORD = $DbPassword

Write-Host "Usando: $psql" -ForegroundColor DarkGray
Write-Host "Reiniciando base '$DbName' en ${DbHost}:$DbPort ..." -ForegroundColor Cyan

$sqlTerminate = @"
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '$DbName' AND pid <> pg_backend_pid();
"@

& $psql -h $DbHost -p $DbPort -U $DbUser -d postgres -v ON_ERROR_STOP=1 -c $sqlTerminate
& $psql -h $DbHost -p $DbPort -U $DbUser -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS $DbName;"
& $psql -h $DbHost -p $DbPort -U $DbUser -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $DbName;"

Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue

Write-Host "Listo. Ejecute start-dev.ps1 o bootRun para que Flyway cree el esquema." -ForegroundColor Green

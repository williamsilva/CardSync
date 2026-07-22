<#
.SYNOPSIS
  Faz um backup (pg_dump, formato custom) do banco do CardSync.

.DESCRIPTION
  Por padrao aponta para o banco local de dev (mesmos defaults de application-dev.yml:
  DB_HOST/DB_PORT/DB_DATABASE/DB_USERNAME/DB_PASSWORD). Para outro ambiente, passe os
  parametros correspondentes ou defina as variaveis de ambiente antes de rodar o script.

.EXAMPLE
  .\backup-db.ps1

.EXAMPLE
  .\backup-db.ps1 -DbHost "meu-host" -DbPort 5432 -DbName cardsync -DbUser postgres -DbPassword "senha"
#>

param(
  [string]$DbHost     = $(if ($env:DB_HOST)     { $env:DB_HOST }     else { "127.0.0.1" }),
  [string]$DbPort     = $(if ($env:DB_PORT)     { $env:DB_PORT }     else { "5432" }),
  [string]$DbName     = $(if ($env:DB_DATABASE) { $env:DB_DATABASE } else { "cardsync" }),
  [string]$DbUser     = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "postgres" }),
  [string]$DbPassword = $(if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "postgres" }),
  [string]$OutputDir  = (Join-Path $PSScriptRoot "backups")
)

$ErrorActionPreference = "Stop"

function Resolve-PgDump {
  $cmd = Get-Command pg_dump -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }

  # pg_dump nem sempre fica no PATH no Windows — procura nas instalacoes padrao do
  # PostgreSQL em Program Files, pegando a versao mais recente encontrada.
  $roots = @("C:\Program Files\PostgreSQL", "C:\Program Files (x86)\PostgreSQL")
  foreach ($root in $roots) {
    if (-not (Test-Path $root)) { continue }
    $versionDirs = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | Sort-Object { [int]$_.Name } -Descending
    foreach ($dir in $versionDirs) {
      $candidate = Join-Path $dir.FullName "bin\pg_dump.exe"
      if (Test-Path $candidate) { return $candidate }
    }
  }

  throw "pg_dump nao encontrado no PATH nem nas instalacoes padrao do PostgreSQL. Instale o PostgreSQL client tools ou informe o caminho manualmente."
}

$pgDump = Resolve-PgDump
Write-Host "Usando pg_dump em: $pgDump"

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outFile = Join-Path $OutputDir "cardsync_${timestamp}.dump"

$env:PGPASSWORD = $DbPassword
try {
  & $pgDump --host=$DbHost --port=$DbPort --username=$DbUser --dbname=$DbName --format=custom --file="$outFile" --verbose

  if ($LASTEXITCODE -ne 0) {
    throw "pg_dump terminou com codigo de saida $LASTEXITCODE"
  }

  $sizeMb = [math]::Round((Get-Item $outFile).Length / 1MB, 2)
  Write-Host ""
  Write-Host "Backup criado com sucesso: $outFile ($sizeMb MB)"
  Write-Host "Para restaurar: pg_restore --host=<host> --port=<port> --username=<user> --dbname=<db> --clean --if-exists --no-owner `"$outFile`""
}
finally {
  Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}

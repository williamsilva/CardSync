<#
.SYNOPSIS
  Cria os 5 "Dia sem Arquivo" (Banco/Sem Movimento) para o domicilio Santander da Acquamania,
  um para cada dia util em que os lancamentos do extrato foram incluidos manualmente.

.DESCRIPTION
  Complementa insert-santander-acquamania-releases-2026-03.ps1: registra formalmente que o
  domicilio Santander Ag 3346/Cc 13005859 (Acquamania) nao teve arquivo automatico em
  25, 26, 27, 30 e 31/03/2026 (os lancamentos desses dias foram incluidos manualmente via
  script). Idempotente — rodar de novo nao duplica.

  Por padrao aponta para o banco local de dev (mesmos defaults de application-dev.yml:
  DB_HOST/DB_PORT/DB_DATABASE/DB_USERNAME/DB_PASSWORD). Para outro ambiente, passe os
  parametros correspondentes ou defina as variaveis de ambiente antes de rodar o script.

.EXAMPLE
  .\insert-santander-acquamania-no-file-days-2026-03.ps1
#>

param(
  [string]$DbHost     = $(if ($env:DB_HOST)     { $env:DB_HOST }     else { "127.0.0.1" }),
  [string]$DbPort     = $(if ($env:DB_PORT)     { $env:DB_PORT }     else { "5432" }),
  [string]$DbName     = $(if ($env:DB_DATABASE) { $env:DB_DATABASE } else { "cardsync" }),
  [string]$DbUser     = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "postgres" }),
  [string]$DbPassword = $(if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "postgres" })
)

$ErrorActionPreference = "Stop"

function Resolve-Psql {
  $cmd = Get-Command psql -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }

  # psql nem sempre fica no PATH no Windows — procura nas instalacoes padrao do
  # PostgreSQL em Program Files, pegando a versao mais recente encontrada.
  $roots = @("C:\Program Files\PostgreSQL", "C:\Program Files (x86)\PostgreSQL")
  foreach ($root in $roots) {
    if (-not (Test-Path $root)) { continue }
    $versionDirs = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | Sort-Object { [int]$_.Name } -Descending
    foreach ($dir in $versionDirs) {
      $candidate = Join-Path $dir.FullName "bin\psql.exe"
      if (Test-Path $candidate) { return $candidate }
    }
  }

  throw "psql nao encontrado no PATH nem nas instalacoes padrao do PostgreSQL. Instale o PostgreSQL client tools ou informe o caminho manualmente."
}

$psql = Resolve-Psql
Write-Host "Usando psql em: $psql"

$sqlFile = Join-Path $PSScriptRoot "insert-santander-acquamania-no-file-days-2026-03.sql"

$env:PGPASSWORD = $DbPassword
try {
  & $psql --host=$DbHost --port=$DbPort --username=$DbUser --dbname=$DbName --file="$sqlFile"

  if ($LASTEXITCODE -ne 0) {
    throw "psql terminou com codigo de saida $LASTEXITCODE"
  }

  Write-Host ""
  Write-Host "Script aplicado. Confira acima 'total_dias_no_lote' (deveria ser 5) e 'ja_existentes_pulados' (5 se ja tinha rodado antes, 0 na primeira vez)."
}
finally {
  Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}

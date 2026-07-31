<#
.SYNOPSIS
  Reaplica a exclusao em lote "Nao localizada na adquirente" (motivo UNDONE) no banco de dev.

.DESCRIPTION
  Depois de resetar/reimportar o banco de dev, esse script restaura o mesmo estado das 391
  vendas ERP que foram marcadas manualmente como excluidas por ausencia na adquirente em
  2026-07-31, sem precisar refazer a acao na tela "Nao localizada na adquirente".

  Por padrao aponta para o banco local de dev (mesmos defaults de application-dev.yml:
  DB_HOST/DB_PORT/DB_DATABASE/DB_USERNAME/DB_PASSWORD). Para outro ambiente, passe os
  parametros correspondentes ou defina as variaveis de ambiente antes de rodar o script.

.EXAMPLE
  .\reapply-erp-missing-acquirer-exclusions.ps1

.EXAMPLE
  .\reapply-erp-missing-acquirer-exclusions.ps1 -DbHost "meu-host" -DbPort 5432 -DbName cardsync -DbUser postgres -DbPassword "senha"
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

$sqlFile = Join-Path $PSScriptRoot "reapply-erp-missing-acquirer-exclusions.sql"

$env:PGPASSWORD = $DbPassword
try {
  & $psql --host=$DbHost --port=$DbPort --username=$DbUser --dbname=$DbName --file="$sqlFile"

  if ($LASTEXITCODE -ne 0) {
    throw "psql terminou com codigo de saida $LASTEXITCODE"
  }

  Write-Host ""
  Write-Host "Script aplicado. Confira acima 'chaves_encontradas_no_banco' (deveria ser 391) e a lista de chaves sem match (deveria vir vazia)."
}
finally {
  Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}

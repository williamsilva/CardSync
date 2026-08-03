<#
.SYNOPSIS
  Inclui os lancamentos de cartao (REDE-*) do extrato Santander da Acquamania (25-31/03/2026).

.DESCRIPTION
  Baseado no extrato "santander acqua 05-31.pdf" (ACQUAMANIA MULTIPLO LAZER S/A, Ag 3346 /
  Cc 130058595) — so as linhas "Pagamento Cartao De Credito/Debito REDE-*", sem PV (o Santander
  nao coloca o PV colado ao lancamento). Depois de resetar/reimportar o banco de dev, roda de
  novo pra recriar esses 33 lancamentos manuais; idempotente (nao duplica se rodar mais de uma
  vez), mesma chave de duplicidade do ReleasesBankService#createManual.

  Por padrao aponta para o banco local de dev (mesmos defaults de application-dev.yml:
  DB_HOST/DB_PORT/DB_DATABASE/DB_USERNAME/DB_PASSWORD). Para outro ambiente, passe os
  parametros correspondentes ou defina as variaveis de ambiente antes de rodar o script.

.EXAMPLE
  .\insert-santander-acquamania-releases-2026-03.ps1

.EXAMPLE
  .\insert-santander-acquamania-releases-2026-03.ps1 -DbHost "meu-host" -DbPort 5432 -DbName cardsync -DbUser postgres -DbPassword "senha"
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

$sqlFile = Join-Path $PSScriptRoot "insert-santander-acquamania-releases-2026-03.sql"

$env:PGPASSWORD = $DbPassword
try {
  & $psql --host=$DbHost --port=$DbPort --username=$DbUser --dbname=$DbName --file="$sqlFile"

  if ($LASTEXITCODE -ne 0) {
    throw "psql terminou com codigo de saida $LASTEXITCODE"
  }

  Write-Host ""
  Write-Host "Script aplicado. Confira acima 'total_linhas_no_lote' (deveria ser 33) e 'ja_existentes_puladas' (33 se ja tinha rodado antes, 0 na primeira vez)."
}
finally {
  Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}

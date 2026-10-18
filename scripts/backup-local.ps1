$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker não está no PATH. Este backup usa o Postgres do docker compose."
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dest = Join-Path $root (Join-Path 'backups' $stamp)
New-Item -ItemType Directory -Path $dest -Force | Out-Null

Push-Location $root
try {
    & docker compose exec -T postgres pg_dump -U anjo -d anjo_guarda --clean --if-exists --no-owner --no-acl -f /tmp/anjo_guarda.sql
    if ($LASTEXITCODE -ne 0) {
        throw "pg_dump falhou."
    }
    & docker compose cp "postgres:/tmp/anjo_guarda.sql" (Join-Path $dest 'anjo_guarda.sql')
    if ($LASTEXITCODE -ne 0) {
        throw "Não foi possível copiar o dump."
    }
    & docker compose exec -T postgres rm -f /tmp/anjo_guarda.sql
}
finally {
    Pop-Location
}

foreach ($dir in @('documentos', 'imports-originais')) {
    $source = Join-Path $root $dir
    if (Test-Path $source) {
        Copy-Item -Path $source -Destination (Join-Path $dest $dir) -Recurse
    }
}

@(
    'Cópia local. Fica nesta máquina.'
    'Nada é apagado daqui.'
    'Periodicidade e prazo de retenção continuam em aberto.'
) | Set-Content -Path (Join-Path $dest 'LEIA.txt') -Encoding utf8

Write-Output $dest

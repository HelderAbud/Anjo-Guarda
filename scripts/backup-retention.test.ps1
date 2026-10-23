$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'backup-retention.ps1')

function Assert-Same($actual, $expected, $label) {
    $left = @($actual) -join ','
    $right = @($expected) -join ','
    if ($left -ne $right) {
        throw "$label. Esperado [$right]. Veio [$left]."
    }
}

$daily = 31..1 | ForEach-Object { '202610{0:D2}-020000' -f $_ }
$expiredDaily = @(Get-ExpiredBackupNames -Names $daily -Keep 30 -Pattern '^\d{8}-\d{6}$')
Assert-Same $expiredDaily @('20261001-020000') 'o dia mais antigo sai quando passam de 30'

$kept = @(Get-ExpiredBackupNames -Names ($daily | Select-Object -Skip 1) -Keep 30 -Pattern '^\d{8}-\d{6}$')
Assert-Same $kept @() '30 diários ficam'

$monthly = 202501..202513 | ForEach-Object { '{0}' -f $_ }
$expiredMonthly = @(Get-ExpiredBackupNames -Names ($monthly + @('documentos', '../imports-originais')) -Keep 12 -Pattern '^\d{6}$')
Assert-Same $expiredMonthly @('202501') 'só o mês antigo sai; nome fora do padrão não entra'

$parsed = @(Get-S3BackupNames -Lines @(
    '                           PRE 20261023-020000/'
    '                           PRE documentos/'
    'not-a-prefix'
))
Assert-Same $parsed @('20261023-020000', 'documentos') 'a listagem do S3 vira nome de pasta'

Write-Output 'ok'

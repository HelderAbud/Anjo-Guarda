$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'backup-retention.ps1')

function Import-AnjoS3Env {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        return
    }
    $allowed = @('S3_ENDPOINT', 'S3_BUCKET', 'S3_REGION', 'S3_ACCESS_KEY', 'S3_SECRET_KEY')
    foreach ($line in Get-Content -Path $Path) {
        if ($line -match '^\s*#' -or $line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            continue
        }
        $name = $Matches[1]
        if ($allowed -notcontains $name) {
            continue
        }
        if (-not [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            continue
        }
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        Set-Item -Path "Env:$name" -Value $value
    }
}

function Invoke-Aws {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AwsArgs)
    & aws @AwsArgs --endpoint-url $env:S3_ENDPOINT
    if ($LASTEXITCODE -ne 0) {
        throw 'O envio para o storage falhou.'
    }
}

Import-AnjoS3Env (Join-Path $root '.env')

foreach ($name in @('S3_ENDPOINT', 'S3_BUCKET', 'S3_REGION', 'S3_ACCESS_KEY', 'S3_SECRET_KEY')) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Falta $name. O backup remoto não sobe sem o storage."
    }
}
if ($env:S3_ENDPOINT -notmatch '^https://') {
    throw 'S3_ENDPOINT precisa ser https.'
}
if ($env:S3_BUCKET -match '/' -or $env:S3_BUCKET -match '\.\.' -or $env:S3_BUCKET -notmatch '^[A-Za-z0-9][A-Za-z0-9.\-]{1,61}[A-Za-z0-9]$') {
    throw 'S3_BUCKET inválido.'
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker não está no PATH. Este backup usa o Postgres do docker compose.'
}
if (-not (Get-Command aws -ErrorAction SilentlyContinue)) {
    throw 'AWS CLI não está no PATH. O backup remoto envia a cópia por ela.'
}

$tz = $null
foreach ($id in @('E. South America Standard Time', 'America/Sao_Paulo')) {
    try {
        $tz = [TimeZoneInfo]::FindSystemTimeZoneById($id)
        break
    } catch {
        $tz = $null
    }
}
if ($null -eq $tz) {
    throw 'Fuso America/Sao_Paulo não encontrado.'
}

$now = [TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow, $tz)
$stamp = $now.ToString('yyyyMMdd-HHmmss')
$month = $now.ToString('yyyyMM')
$bucket = $env:S3_BUCKET
$dest = Join-Path ([System.IO.Path]::GetTempPath()) ("anjo-backup-" + $stamp)
$config = Join-Path ([System.IO.Path]::GetTempPath()) ("anjo-aws-config-" + [guid]::NewGuid().ToString('N'))
$finished = $false

$env:AWS_ACCESS_KEY_ID = $env:S3_ACCESS_KEY
$env:AWS_SECRET_ACCESS_KEY = $env:S3_SECRET_KEY
$env:AWS_DEFAULT_REGION = $env:S3_REGION
$env:AWS_EC2_METADATA_DISABLED = 'true'
$env:AWS_CONFIG_FILE = $config
@(
    '[default]'
    's3 ='
    '    addressing_style = path'
) | Set-Content -Path $config -Encoding ascii

New-Item -ItemType Directory -Path $dest -Force | Out-Null
try {
    Push-Location $root
    try {
        & docker compose exec -T postgres pg_dump -U anjo -d anjo_guarda --clean --if-exists --no-owner --no-acl -f /tmp/anjo_guarda.sql
        if ($LASTEXITCODE -ne 0) {
            throw 'pg_dump falhou.'
        }
        & docker compose cp "postgres:/tmp/anjo_guarda.sql" (Join-Path $dest 'anjo_guarda.sql')
        if ($LASTEXITCODE -ne 0) {
            throw 'Não foi possível copiar o dump.'
        }
        & docker compose exec -T postgres rm -f /tmp/anjo_guarda.sql
    } finally {
        Pop-Location
    }

    foreach ($dir in @('documentos', 'imports-originais')) {
        $source = Join-Path $root $dir
        if (Test-Path $source) {
            Copy-Item -Path $source -Destination (Join-Path $dest $dir) -Recurse
        }
    }

    Invoke-Aws s3 sync $dest "s3://$bucket/backups/daily/$stamp/" --only-show-errors

    $monthOutput = & aws s3 ls "s3://$bucket/backups/monthly/$month/" --endpoint-url $env:S3_ENDPOINT 2>&1
    $monthExists = $LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace("$monthOutput")
    if (-not $monthExists) {
        Invoke-Aws s3 sync $dest "s3://$bucket/backups/monthly/$month/" --only-show-errors
    }

    $dailyLines = & aws s3 ls "s3://$bucket/backups/daily/" --endpoint-url $env:S3_ENDPOINT
    if ($LASTEXITCODE -ne 0) {
        throw 'O envio para o storage falhou.'
    }
    foreach ($name in @(Get-ExpiredBackupNames -Names @(Get-S3BackupNames -Lines @($dailyLines)) -Keep 30 -Pattern '^\d{8}-\d{6}$')) {
        Invoke-Aws s3 rm "s3://$bucket/backups/daily/$name/" --recursive --only-show-errors
    }

    $monthlyLines = & aws s3 ls "s3://$bucket/backups/monthly/" --endpoint-url $env:S3_ENDPOINT
    if ($LASTEXITCODE -ne 0) {
        throw 'O envio para o storage falhou.'
    }
    foreach ($name in @(Get-ExpiredBackupNames -Names @(Get-S3BackupNames -Lines @($monthlyLines)) -Keep 12 -Pattern '^\d{6}$')) {
        Invoke-Aws s3 rm "s3://$bucket/backups/monthly/$name/" --recursive --only-show-errors
    }

    $finished = $true
} finally {
    Remove-Item -Path $config -Force -ErrorAction SilentlyContinue
    Remove-Item Env:AWS_ACCESS_KEY_ID, Env:AWS_SECRET_ACCESS_KEY, Env:AWS_DEFAULT_REGION, Env:AWS_EC2_METADATA_DISABLED, Env:AWS_CONFIG_FILE -ErrorAction SilentlyContinue
    if ($finished) {
        Remove-Item -Path $dest -Recurse -Force -ErrorAction SilentlyContinue
    } else {
        Write-Output $dest
    }
}

Write-Output "backups/daily/$stamp"

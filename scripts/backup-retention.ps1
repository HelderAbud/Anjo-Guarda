function Get-ExpiredBackupNames {
    param(
        [string[]]$Names = @(),
        [int]$Keep,
        [Parameter(Mandatory = $true)]
        [string]$Pattern
    )

    $valid = @($Names | Where-Object { $_ -match $Pattern } | Sort-Object -Descending)
    if ($valid.Count -le $Keep) {
        return @()
    }
    return @($valid | Select-Object -Skip $Keep)
}

function Get-S3BackupNames {
    param([string[]]$Lines = @())

    $names = @()
    foreach ($line in $Lines) {
        if ($line -match 'PRE\s+(\S+)/?\s*$') {
            $names += $Matches[1].TrimEnd('/')
        }
    }
    return @($names)
}

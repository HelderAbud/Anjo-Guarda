param(
    [Parameter(Mandatory = $true)][string]$Email
)

if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET) -or $env:JWT_SECRET.Length -lt 32) {
    throw "Defina JWT_SECRET com pelo menos 32 caracteres antes de rodar o reset."
}

if ([string]::IsNullOrWhiteSpace($env:ANJO_NEW_PASSWORD)) {
    $secure = Read-Host -AsSecureString "Nova senha"
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $env:ANJO_NEW_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

mvn -q spring-boot:run "-Dspring-boot.run.arguments=--reset-password --email=$Email"

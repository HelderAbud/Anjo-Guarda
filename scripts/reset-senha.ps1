param(
    [Parameter(Mandatory = $true)][string]$Email,
    [Parameter(Mandatory = $true)][string]$NewPassword
)

if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET) -or $env:JWT_SECRET.Length -lt 32) {
    throw "Defina JWT_SECRET com pelo menos 32 caracteres antes de rodar o reset."
}

mvn -q spring-boot:run "-Dspring-boot.run.arguments=--reset-password --email=$Email --new-password=$NewPassword"

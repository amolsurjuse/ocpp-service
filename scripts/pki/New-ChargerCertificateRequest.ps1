[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._-]{1,255}$')]
    [string]$ChargePointId,

    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null

$keyPath = Join-Path $resolvedOutput ($ChargePointId + '.key')
$csrPath = Join-Path $resolvedOutput ($ChargePointId + '.csr')
if ((Test-Path -LiteralPath $keyPath) -or (Test-Path -LiteralPath $csrPath)) {
    throw 'Refusing to overwrite existing charger key or CSR material.'
}

& openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:prime256v1 -out $keyPath
if ($LASTEXITCODE -ne 0) { throw 'OpenSSL failed to generate the charger private key.' }

$subject = '/O=ElectraHub/OU=OCPP Charging Stations/CN=' + $ChargePointId
$san = 'subjectAltName=URI:urn:electrahub:charge-point:' + $ChargePointId
& openssl req -new -sha256 -key $keyPath -out $csrPath -subj $subject -addext $san
if ($LASTEXITCODE -ne 0) { throw 'OpenSSL failed to generate the charger CSR.' }

if ($env:OS -eq 'Windows_NT') {
    & icacls.exe $keyPath /inheritance:r /grant:r ($env:USERNAME + ':(R,W)') | Out-Null
}

Write-Output ('CSR created: ' + $csrPath)
Write-Output 'The private key remains local and must be installed through the charger secure-provisioning channel.'

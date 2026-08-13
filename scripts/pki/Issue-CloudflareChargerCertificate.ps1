[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._-]{1,255}$')]
    [string]$ChargePointId,

    [Parameter(Mandatory = $true)]
    [string]$MaterialDirectory,

    [ValidateRange(1, 3650)]
    [int]$ValidityDays = 365,

    [string]$CsmsInternalUrl = 'http://127.0.0.1:8082',

    [ValidateRange(0, 86400)]
    [int]$RotationOverlapSeconds = 3600
)

$ErrorActionPreference = 'Stop'
$apiToken = [Environment]::GetEnvironmentVariable('CLOUDFLARE_API_TOKEN')
$zoneId = [Environment]::GetEnvironmentVariable('CLOUDFLARE_ZONE_ID')
$internalToken = [Environment]::GetEnvironmentVariable('APP_SECURITY_INTERNAL_TOKEN')
if ([string]::IsNullOrWhiteSpace($apiToken)) { throw 'CLOUDFLARE_API_TOKEN is required.' }
if ([string]::IsNullOrWhiteSpace($zoneId)) { throw 'CLOUDFLARE_ZONE_ID is required.' }
if ([string]::IsNullOrWhiteSpace($internalToken)) { throw 'APP_SECURITY_INTERNAL_TOKEN is required.' }

$resolvedDirectory = [System.IO.Path]::GetFullPath($MaterialDirectory)
$csrPath = Join-Path $resolvedDirectory ($ChargePointId + '.csr')
$certificatePath = Join-Path $resolvedDirectory ($ChargePointId + '.crt')
$metadataPath = Join-Path $resolvedDirectory ($ChargePointId + '.metadata.json')
if (-not (Test-Path -LiteralPath $csrPath)) { throw 'The charger CSR does not exist.' }
if ((Test-Path -LiteralPath $certificatePath) -or (Test-Path -LiteralPath $metadataPath)) {
    throw 'Refusing to overwrite existing certificate or issuance metadata.'
}

$requestBody = @{
    csr = Get-Content -LiteralPath $csrPath -Raw
    validity_days = $ValidityDays
} | ConvertTo-Json -Depth 4
$cloudflareHeaders = @{ Authorization = 'Bearer ' + $apiToken }
$issueUrl = 'https://api.cloudflare.com/client/v4/zones/' + $zoneId + '/client_certificates'
$issued = Invoke-RestMethod -Method Post -Uri $issueUrl -Headers $cloudflareHeaders `
    -ContentType 'application/json' -Body $requestBody
if (-not $issued.success -or [string]::IsNullOrWhiteSpace($issued.result.certificate)) {
    throw 'Cloudflare did not issue the charger certificate.'
}

[System.IO.File]::WriteAllText($certificatePath, $issued.result.certificate)
$registerBody = @{
    certificatePem = $issued.result.certificate
    rotationOverlapSeconds = $RotationOverlapSeconds
} | ConvertTo-Json -Depth 4
$csmsHeaders = @{
    'X-ElectraHub-Internal-Token' = $internalToken
    'X-ElectraHub-Actor' = 'fleet-pki-operator'
}
$registerUrl = $CsmsInternalUrl.TrimEnd('/') + '/api/v1/ocpp/internal/charger-certificates/' + $ChargePointId
$registered = Invoke-RestMethod -Method Put -Uri $registerUrl -Headers $csmsHeaders `
    -ContentType 'application/json' -Body $registerBody

@{
    chargePointId = $ChargePointId
    cloudflareCertificateId = $issued.result.id
    fingerprintSha256 = $registered.fingerprintSha256
    issuedOn = $issued.result.issued_on
    expiresOn = $issued.result.expires_on
    registeredAt = [DateTimeOffset]::UtcNow.ToString('O')
} | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding utf8

Write-Output ('Certificate issued and registered for ' + $ChargePointId + '.')
Write-Output ('Certificate: ' + $certificatePath)
Write-Output ('Metadata: ' + $metadataPath)

[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._-]{1,255}$')]
    [string]$ChargePointId,

    [Parameter(Mandatory = $true)]
    [string]$MetadataPath,

    [string]$CsmsInternalUrl = 'http://127.0.0.1:8082'
)

$ErrorActionPreference = 'Stop'
$metadata = Get-Content -LiteralPath $MetadataPath -Raw | ConvertFrom-Json
if ($metadata.chargePointId -ne $ChargePointId) { throw 'Metadata charge-point identity does not match.' }
$apiToken = [Environment]::GetEnvironmentVariable('CLOUDFLARE_API_TOKEN')
$zoneId = [Environment]::GetEnvironmentVariable('CLOUDFLARE_ZONE_ID')
$internalToken = [Environment]::GetEnvironmentVariable('APP_SECURITY_INTERNAL_TOKEN')
if ([string]::IsNullOrWhiteSpace($apiToken) -or [string]::IsNullOrWhiteSpace($zoneId) `
        -or [string]::IsNullOrWhiteSpace($internalToken)) {
    throw 'Cloudflare zone/token and CSMS internal token environment variables are required.'
}

if (-not $PSCmdlet.ShouldProcess($ChargePointId, 'Revoke the Cloudflare and CSMS charger certificate')) { return }
$cloudflareHeaders = @{ Authorization = 'Bearer ' + $apiToken }
$revokeUrl = 'https://api.cloudflare.com/client/v4/zones/' + $zoneId + '/client_certificates/' `
    + $metadata.cloudflareCertificateId
$cloudflareResult = Invoke-RestMethod -Method Delete -Uri $revokeUrl -Headers $cloudflareHeaders
if (-not $cloudflareResult.success) { throw 'Cloudflare certificate revocation failed.' }

$csmsHeaders = @{
    'X-ElectraHub-Internal-Token' = $internalToken
    'X-ElectraHub-Actor' = 'fleet-pki-operator'
}
$csmsUrl = $CsmsInternalUrl.TrimEnd('/') + '/api/v1/ocpp/internal/charger-certificates/' `
    + $ChargePointId + '/' + $metadata.fingerprintSha256 + '/revoke'
Invoke-RestMethod -Method Post -Uri $csmsUrl -Headers $csmsHeaders | Out-Null
Write-Output ('Certificate revoked for ' + $ChargePointId + '.')

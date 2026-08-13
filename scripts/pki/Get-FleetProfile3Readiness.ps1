[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$CsmsInternalUrl,
    [Parameter(Mandatory = $true)][string]$InternalServiceToken,
    [switch]$RequireReady
)

$ErrorActionPreference = 'Stop'
$headers = @{ 'X-ElectraHub-Internal-Token' = $InternalServiceToken }
$url = $CsmsInternalUrl.TrimEnd('/') + '/api/v1/ocpp/internal/charger-certificates/fleet/readiness'
$readiness = Invoke-RestMethod -Method Get -Uri $url -Headers $headers

[pscustomobject]@{
    evaluatedAt = $readiness.evaluatedAt
    knownFleet = $readiness.knownFleet
    enrolledChargePoints = $readiness.enrolledChargePoints
    missingCertificates = $readiness.missingCertificates
    activeCertificates = $readiness.activeCertificates
    retiringCertificates = $readiness.retiringCertificates
    revokedCertificates = $readiness.revokedCertificates
    certificatesExpiringWithinWarningWindow = $readiness.certificatesExpiringWithinWarningWindow
    enforcementReady = $readiness.enforcementReady
} | ConvertTo-Json -Depth 3

if ($RequireReady -and -not $readiness.enforcementReady) {
    throw 'Profile 3 fleet enforcement gate is not ready.'
}

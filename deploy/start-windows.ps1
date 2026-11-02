$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:ROOM_ADMIN_PASSWORD -or -not $env:ROOM_ENROLLMENT_KEY) {
    throw 'Set ROOM_ADMIN_PASSWORD and ROOM_ENROLLMENT_KEY environment variables first. See README.'
}
& java -jar (Join-Path $PSScriptRoot 'room-server.jar')
exit $LASTEXITCODE

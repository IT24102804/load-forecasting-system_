$ErrorActionPreference = 'Stop'

Set-Location -LiteralPath $PSScriptRoot

Write-Host "Starting Cost Prediction service with Python 3.11 on port 5004..."
py -3.11 cost_service_app.py

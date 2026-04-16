$ErrorActionPreference = 'Stop'

Set-Location -LiteralPath $PSScriptRoot

Write-Host "Starting Generation Mix service with Python 3.11 on port 5003..."
py -3.11 app.py

Param(
  [string]$ModelDir = "ml/model",
  [string]$AssetsDir = "app/src/main/assets/model"
)

New-Item -ItemType Directory -Force -Path $AssetsDir | Out-Null
Copy-Item -Path (Join-Path $ModelDir "*") -Destination $AssetsDir -Recurse -Force
Write-Host "Copied model artifacts from $ModelDir to $AssetsDir"


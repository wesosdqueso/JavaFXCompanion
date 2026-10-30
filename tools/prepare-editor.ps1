$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$sdk = 'D:\eclipse\tools\javafx-26\javafx-sdk-26'
$sceneBuilder = 'C:\Users\User\AppData\Local\SceneBuilder\app\scenebuilder-26.0.0-all.jar'
if (-not (Test-Path $sceneBuilder)) { throw 'Scene Builder 26 is required to prepare this build.' }
New-Item -ItemType Directory -Force "$root\lib", "$root\native", "$root\third-party\javafx" | Out-Null
Copy-Item "$sdk\lib\*.jar" "$root\lib" -Force
Copy-Item $sceneBuilder "$root\lib\scenebuilder.jar" -Force
Copy-Item "$sdk\bin\*.dll" "$root\native" -Force
Copy-Item "$sdk\legal\*" "$root\third-party\javafx" -Recurse -Force
& python "$PSScriptRoot\configure-editor.py"
if ($LASTEXITCODE -ne 0) { throw 'Editor dependency configuration failed' }

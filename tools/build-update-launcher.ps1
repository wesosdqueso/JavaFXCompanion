param([string]$Workspace = 'D:\eclipse\workspaces\2025',
      [string]$EclipseHome = 'D:\eclipse\installations\eclipse-2026-12',
      [string]$JavaHome = 'C:\Program Files\Java\jdk-25')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$build = Join-Path $root 'build'
New-Item -ItemType Directory -Path $build -Force | Out-Null
$settings = Join-Path $build 'update-launcher-settings.txt'
[IO.File]::WriteAllLines($settings, @($Workspace, $EclipseHome, $JavaHome), [Text.UTF8Encoding]::new($false))
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (!(Test-Path -LiteralPath $compiler)) { throw 'The .NET Framework 4 C# compiler was not found.' }
$executable = Join-Path $build 'Actualizar-JavaFXCompanion.exe'
& $compiler /nologo /target:exe /optimize+ ('/out:' + $executable) ('/resource:' + $settings + ',settings') (Join-Path $PSScriptRoot 'UpdateLauncher.cs')
if ($LASTEXITCODE -ne 0) { throw 'Unable to compile the update launcher.' }
Write-Host "Created: $executable"

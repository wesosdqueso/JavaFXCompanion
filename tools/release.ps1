$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\build.ps1"
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
$site = & python "$PSScriptRoot\package-release.py"
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
$site = $site.Trim()
$siteUri = ([uri]($site + '\')).AbsoluteUri
$launcher = 'D:\eclipse\installations\eclipse-2026-12\plugins\org.eclipse.equinox.launcher_1.8.0.v20260804-1928.jar'
& 'C:\Program Files\Java\jdk-25\bin\java.exe' -jar $launcher -nosplash -application org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher -metadataRepository $siteUri -artifactRepository $siteUri -source $site -publishArtifacts
if ($LASTEXITCODE -ne 0) { throw 'p2 publisher failed' }
if (-not ((Test-Path "$site\content.xml") -or (Test-Path "$site\content.jar"))) { throw 'Missing p2 metadata' }
Compress-Archive -Path "$site\*" -DestinationPath "$site.zip"
Write-Output "Installable archive: $site.zip"

$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent
if (-not (Test-Path "$projectDir\lib\scenebuilder.jar")) { & "$PSScriptRoot\prepare-editor.ps1" }
$bundleList = 'D:\eclipse\installations\eclipse-2026-12\configuration\org.eclipse.equinox.simpleconfigurator\bundles.info'
$jars = Get-Content $bundleList | Where-Object { $_ -and -not $_.StartsWith('#') } | ForEach-Object {
    $location = ($_ -split ',')[2]
    if ($location.StartsWith('file:')) {
        $localPath = ([uri]$location).LocalPath
        if ($localPath.EndsWith('.jar') -and (Test-Path -LiteralPath $localPath)) { $localPath }
    }
}
$buildDir = Join-Path $projectDir 'build'
$classesDir = Join-Path $buildDir 'classes'
New-Item -ItemType Directory -Force $classesDir | Out-Null
$sources = Get-ChildItem (Join-Path $projectDir 'src') -Filter '*.java' -Recurse
$argsFile = Join-Path $buildDir 'javac.args'
$jars += Get-ChildItem "$projectDir\lib" -Filter '*.jar' | ForEach-Object FullName
$compilerArgs = @('--release', '24', '-encoding', 'UTF-8', '-classpath', ('"' + (($jars -join ';') -replace '\\','/') + '"'), '-d', ('"' + ($classesDir -replace '\\','/') + '"'))
$compilerArgs += $sources | ForEach-Object { '"' + ($_.FullName -replace '\\','/') + '"' }
[IO.File]::WriteAllLines($argsFile, $compilerArgs)
& 'C:\Program Files\Java\jdk-25\bin\javac.exe' "@$argsFile"
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
$jarPath = Join-Path $buildDir 'org.javafxcompanion-dev.jar'
& 'C:\Program Files\Java\jdk-25\bin\jar.exe' --create --file $jarPath --manifest (Join-Path $projectDir 'META-INF\MANIFEST.MF') -C $classesDir . -C $projectDir plugin.xml -C $projectDir icons -C $projectDir lib -C $projectDir native -C $projectDir third-party
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
Write-Output $jarPath

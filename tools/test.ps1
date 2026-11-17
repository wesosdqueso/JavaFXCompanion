param([switch]$Visual, [switch]$Editor)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
& "$PSScriptRoot\build.ps1"
$build = Join-Path $root 'build'
$testClasses = Join-Path $build 'test-classes'
New-Item -ItemType Directory -Force $testClasses | Out-Null
$baseArgs = [IO.File]::ReadAllLines("$build\javac.args")
$testArgs = $baseArgs[0..5] + @('-d', ('"' + ($testClasses -replace '\\','/') + '"'), ('"' + (($root + '\tests\SmokeApplication.java') -replace '\\','/') + '"'))
$testArgs += '"' + (($root + '\tests\VisualSmokeApplication.java') -replace '\\','/') + '"'
$testArgs += '"' + (($root + '\tests\EditorSmokeApplication.java') -replace '\\','/') + '"'
[IO.File]::WriteAllLines("$build\test.args", $testArgs)
& 'C:\Program Files\Java\jdk-25\bin\javac.exe' "@$build\test.args"
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& 'C:\Program Files\Java\jdk-25\bin\jar.exe' --create --file "$build\tests.jar" --manifest "$root\tests\MANIFEST.MF" -C $testClasses . -C "$root\tests" plugin.xml
if ($LASTEXITCODE -ne 0) { throw 'Test packaging failed' }
$config = Join-Path $build 'test-configuration'
New-Item -ItemType Directory -Force "$config\org.eclipse.equinox.simpleconfigurator" | Out-Null
$installation = 'D:\eclipse\installations\eclipse-2026-12'
Copy-Item "$installation\configuration\config.ini" "$config\config.ini" -Force
$testConfigText = [IO.File]::ReadAllText("$config\config.ini")
$testConfigText = [regex]::Replace($testConfigText, '(?m)^org\.eclipse\.equinox\.simpleconfigurator\.configUrl=.*$', 'org.eclipse.equinox.simpleconfigurator.configUrl=' + ([uri]"$config\org.eclipse.equinox.simpleconfigurator\bundles.info").AbsoluteUri)
$testConfigText = [regex]::Replace($testConfigText, '(?m)^eclipse\.p2\.data\.area=.*$', 'eclipse.p2.data.area=' + ([uri]"$build\isolated-p2\").AbsoluteUri)
$testConfigText = [regex]::Replace($testConfigText, '(?m)^eclipse\.p2\.profile=.*$', 'eclipse.p2.profile=JavaFXCompanionIsolatedTests')
[IO.File]::WriteAllText("$config\config.ini", $testConfigText)
$bundles = (Get-Content "$installation\configuration\org.eclipse.equinox.simpleconfigurator\bundles.info" | Where-Object { $_ -notmatch '^org\.javafxcompanion(?:\.tests)?,' }) -join "`n"
$manifest = Get-Content -Raw "$root\META-INF\MANIFEST.MF"
$bundleVersion = [regex]::Match($manifest, 'Bundle-Version: ([^\r\n]+)').Groups[1].Value
$bundles += "`norg.javafxcompanion,$bundleVersion,$(([uri]"$build\org.javafxcompanion-dev.jar").AbsoluteUri),4,false`n"
$bundles += "org.javafxcompanion.tests,1.0.0,$(([uri]"$build\tests.jar").AbsoluteUri),4,false`n"
[IO.File]::WriteAllText("$config\org.eclipse.equinox.simpleconfigurator\bundles.info", $bundles)
$application = if ($Editor) { 'org.javafxcompanion.tests.editor' } elseif ($Visual) { 'org.javafxcompanion.tests.visual' } else { 'org.javafxcompanion.tests.smoke' }
& 'C:\Program Files\Java\jdk-25\bin\java.exe' --enable-native-access=ALL-UNNAMED -jar "$installation\plugins\org.eclipse.equinox.launcher_1.8.0.v20260804-1928.jar" -clean -nosplash -configuration $config -data "$build\test-workspace" -application $application -consoleLog
if ($LASTEXITCODE -ne 0) { throw 'Smoke test failed' }

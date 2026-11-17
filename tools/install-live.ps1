param([string]$EclipseHome = 'D:\eclipse\installations\eclipse-2026-12',
      [string]$JavaHome = 'C:\Program Files\Java\jdk-25', [switch]$PrepareOnly,
      [string]$ResultFile, [switch]$WaitForExit, [switch]$Restart, [string]$Workspace,
      [switch]$VerifyOnly)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$build = Join-Path $root 'build'
if ($PrepareOnly -and ($WaitForExit -or $Restart)) { throw '-PrepareOnly cannot be combined with -WaitForExit or -Restart.' }
if ($VerifyOnly -and ($PrepareOnly -or $WaitForExit -or $Restart)) {
    throw '-VerifyOnly cannot be combined with -PrepareOnly, -WaitForExit or -Restart.'
}
if ($Restart -and (!$Workspace -or !(Test-Path -LiteralPath $Workspace -PathType Container))) {
    throw 'Provide an existing -Workspace directory when using -Restart.'
}
if (!$PrepareOnly -and !$WaitForExit) {
    $running = Get-Process eclipse -ErrorAction SilentlyContinue
    if ($running) { throw 'Close Eclipse before installing. Then run this command again.' }
}
$buildLock = $null
New-Item -ItemType Directory -Path $build -Force | Out-Null
try {
    $buildLock = [IO.File]::Open((Join-Path $build 'deploy.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    & (Join-Path $PSScriptRoot 'build.ps1') -EclipseHome $EclipseHome -JavaHome $JavaHome
    # Publishing must never reuse the development IDE's p2 profile or configuration.
    $publisherConfig = Join-Path $build 'update-configuration'
    $configurator = Join-Path $publisherConfig 'org.eclipse.equinox.simpleconfigurator'
    New-Item -ItemType Directory -Path $configurator -Force | Out-Null
    $bundleLines = Get-Content -LiteralPath (Join-Path $EclipseHome 'configuration\org.eclipse.equinox.simpleconfigurator\bundles.info')
    $bundlePaths = @{}
    foreach ($line in $bundleLines) {
        if ($line.StartsWith('#') -or !$line.Contains(',')) { continue }
        $parts = $line.Split(',')
        $uri = [Uri]$parts[2]
        if (!$uri.IsAbsoluteUri -or !$uri.IsFile) { throw "Unsupported bundle location: $line" }
        $bundlePaths[$parts[0]] = $uri.AbsoluteUri
    }
    foreach ($required in @('org.eclipse.osgi', 'org.eclipse.equinox.simpleconfigurator')) {
        if (!$bundlePaths.ContainsKey($required)) { throw "Missing Eclipse bundle: $required" }
    }
    $bundleFile = Join-Path $configurator 'bundles.info'
    [IO.File]::WriteAllLines($bundleFile, $bundleLines)
    $publisherSettings = @(
        'osgi.bundles.defaultStartLevel=4',
        ('osgi.framework=' + $bundlePaths['org.eclipse.osgi']),
        ('osgi.bundles=reference:' + $bundlePaths['org.eclipse.equinox.simpleconfigurator'] + '@1:start'),
        ('org.eclipse.equinox.simpleconfigurator.configUrl=' + ([Uri]$bundleFile).AbsoluteUri),
        ('eclipse.p2.data.area=' + ([Uri](Join-Path $build 'update-p2')).AbsoluteUri),
        'eclipse.p2.profile=JavaFXCompanionPublisher'
    )
    # Escape URI colons for Java .properties parsing.
    $publisherSettings = $publisherSettings | ForEach-Object {
        $index = $_.IndexOf('=')
        $_.Substring(0, $index + 1) + $_.Substring($index + 1).Replace(':', '\:')
    }
    [IO.File]::WriteAllLines((Join-Path $publisherConfig 'config.ini'), $publisherSettings)
    $manifestText = Get-Content (Join-Path $root 'META-INF\MANIFEST.MF') -Raw
    $baseVersion = [regex]::Match($manifestText, '(?m)^Bundle-Version: (\d+\.\d+\.\d+)(?:\.[^\r\n]+)?\r?$').Groups[1].Value
    if (!$baseVersion) { throw 'Bundle-Version is missing or invalid.' }
    $version = $baseVersion + '.' + (Get-Date -Format 'yyyyMMddHHmmssfff')
    $staging = Join-Path $build ('release-' + $version)
    $plugins = Join-Path $staging 'plugins'
    $site = Join-Path $build ('update-site-' + $version)
    $unpack = Join-Path $staging 'content'
    New-Item -ItemType Directory $plugins,$unpack -Force | Out-Null
    Push-Location $unpack
    try {
        & "$JavaHome\bin\jar.exe" xf (Join-Path $build 'org.javafxcompanion-dev.jar')
        if ($LASTEXITCODE -ne 0) { throw 'Unable to extract plugin.' }
        $manifest = Join-Path $unpack 'META-INF\MANIFEST.MF'
        $text = (Get-Content $manifest -Raw) -replace '(?m)^Bundle-Version: [^\r\n]+', "Bundle-Version: $version"
        [IO.File]::WriteAllText($manifest, $text, [Text.UTF8Encoding]::new($false))
        & "$JavaHome\bin\jar.exe" ("-J-Djava.io.tmpdir=$build") --create --file (Join-Path $plugins "org.javafxcompanion_$version.jar") --manifest $manifest -C $unpack org -C $unpack plugin.xml -C $unpack icons -C $unpack lib -C $unpack native -C $unpack third-party
        if ($LASTEXITCODE -ne 0) { throw 'Unable to package release.' }
    } finally { Pop-Location }
    $featureContent = Join-Path $staging 'feature-content'
    $features = Join-Path $staging 'features'
    New-Item -ItemType Directory -Path $featureContent,$features -Force | Out-Null
    $feature = Get-Content (Join-Path $root 'packaging\org.javafxcompanion.feature\feature.xml') -Raw
    $feature = $feature -replace 'version="\d+\.\d+\.\d+\.[^"]+"', ('version="' + $version + '"')
    $feature = $feature.Replace('version="0.0.0"', ('version="' + $version + '"'))
    [IO.File]::WriteAllText((Join-Path $featureContent 'feature.xml'), $feature, [Text.UTF8Encoding]::new($false))
    & "$JavaHome\bin\jar.exe" --create --file (Join-Path $features "org.javafxcompanion.feature_$version.jar") -C $featureContent feature.xml
    if ($LASTEXITCODE -ne 0) { throw 'Unable to package feature.' }
    $publisherLog = Join-Path $staging 'publisher.log'
    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & "$EclipseHome\eclipsec.exe" -nosplash -configuration (Join-Path $build 'update-configuration') -data (Join-Path $build 'publisher-workspace') -application org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher -source $staging -metadataRepository ([Uri]$site).AbsoluteUri -artifactRepository ([Uri]$site).AbsoluteUri -publishArtifacts *> $publisherLog
    } finally { $ErrorActionPreference = $previousErrorAction }
    if ($LASTEXITCODE -ne 0) { throw "Update site publishing failed. See $publisherLog" }
    Write-Host "Update site ready: $site"
    if ($ResultFile) {
        $result = @{ site = $site; version = $version } | ConvertTo-Json
        [IO.File]::WriteAllText($ResultFile, $result, [Text.UTF8Encoding]::new($false))
    }
    if (!$PrepareOnly) {
        if ($WaitForExit) {
            Write-Host 'Update prepared. Close Eclipse normally to install it. This script will wait.'
            while (Get-Process eclipse -ErrorAction SilentlyContinue) { Start-Sleep -Seconds 2 }
        }
        if (Get-Process eclipse -ErrorAction SilentlyContinue) { throw 'Close Eclipse before installing.' }
        $bundleList = Join-Path $EclipseHome 'configuration\org.eclipse.equinox.simpleconfigurator\bundles.info'
        Copy-Item -LiteralPath $bundleList -Destination (Join-Path $staging 'bundles.info.before')
        $director = Join-Path $EclipseHome 'eclipsec.exe'
        $directorArgs = @('-nosplash', '-application', 'org.eclipse.equinox.p2.director')
        $rootsLog = Join-Path $staging 'installed-roots.log'
        $featureId = 'org.javafxcompanion.feature.feature.group'
        $installLog = Join-Path $staging 'install.log'
        try {
            $ErrorActionPreference = 'Continue'
            & $director @directorArgs -listInstalledRoots *> $rootsLog
            if ($LASTEXITCODE -ne 0) { throw "Unable to read installed Eclipse features. See $rootsLog" }
            $previousRoots = @(Get-Content -LiteralPath $rootsLog | Where-Object {
                $_ -match '^org\.javafxcompanion(?:\.feature\.feature\.group)?/[0-9][A-Za-z0-9._-]*$'
            })
            if (Get-Process eclipse -ErrorAction SilentlyContinue) { throw 'Close Eclipse before installing.' }
            $updateArgs = @('-repository', ([Uri]$site).AbsoluteUri, '-installIU', "$featureId/$version")
            if ($previousRoots.Count) { $updateArgs += @('-uninstallIU', ($previousRoots -join ',')) }
            if ($VerifyOnly) { $updateArgs += '-verifyOnly' }
            else { $updateArgs += @('-tag', "JavaFXCompanion-local-$version") }
            & $director @directorArgs @updateArgs *> $installLog
        } finally { $ErrorActionPreference = $previousErrorAction }
        if ($LASTEXITCODE -ne 0) { throw "Installation failed. See $installLog" }
        if ($VerifyOnly) {
            Write-Host "Eclipse accepted the update plan for $version. See $installLog"
            return
        }
        $installed = @(Get-Content -LiteralPath $bundleList | Where-Object { $_.StartsWith('org.javafxcompanion,') })
        if ($installed.Count -ne 1 -or ($installed[0].Split(',')[1] -ne $version)) {
            throw "Eclipse did not register the expected JavaFXCompanion version $version. See $installLog"
        }
        Write-Host 'Installed. JavaFX Companion installed.'
        Write-Host 'Rollback: Help > About Eclipse IDE > Installation Details > Installation History > Revert.'
        if ($Restart) {
            $workspacePath = (Resolve-Path -LiteralPath $Workspace).Path
            Start-Process -WindowStyle Hidden -FilePath (Join-Path $EclipseHome 'eclipse.exe') -ArgumentList @('-data', ('"' + $workspacePath + '"'))
            Write-Host "Eclipse reopened with workspace: $workspacePath"
        }
    }
} finally { if ($buildLock) { $buildLock.Dispose() } }

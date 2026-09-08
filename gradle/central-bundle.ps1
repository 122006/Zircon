#requires -Version 7.3
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$SigningKey,
    [string]$Version,
    [string]$GpgExecutable = 'gpg',
    [string]$GpgHome = $env:GNUPGHOME,
    [string]$PassphraseFile
)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent
if (-not $Version) {
    $Version = [regex]::Match((Get-Content "$projectDir/build.gradle" -Raw), 'version = "([^"]+)"').Groups[1].Value
}
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw 'Expected a stable semantic version, for example 3.3.3.' }
$repository = Join-Path $projectDir 'build/central/repository'
$bundleDir = Join-Path $projectDir "build/central/bundle-$Version"
$bundleZip = Join-Path $projectDir "build/central/zircon-$Version-central.zip"
if ((Test-Path -LiteralPath $bundleDir) -or (Test-Path -LiteralPath $bundleZip)) {
    throw 'Bundle output already exists. Move it aside before creating a new signed bundle.'
}

$gpgArgs = @('--batch', '--yes')
if ($GpgHome) {
    # Git for Windows GPG uses MSYS sockets and rejects drive colons in homedir.
    if ($GpgExecutable -match '[\\/]Git[\\/]usr[\\/]bin[\\/]' -and $GpgHome -match '^([A-Za-z]):') {
        $GpgHome = '/' + $GpgHome.Substring(0, 1).ToLower() + $GpgHome.Substring(2).Replace('\', '/')
    }
    $gpgArgs += @('--homedir', $GpgHome)
}
$passphrase = $null
if ($PassphraseFile) {
    $secure = (Get-Content -LiteralPath $PassphraseFile -Raw).Trim() | ConvertTo-SecureString
    $passphrase = [System.Net.NetworkCredential]::new('', $secure).Password
}

try {
    $artifacts = @()
    foreach ($module in @('base', 'zircon', 'javac', 'gradle')) {
        $relative = "io/github/122006/Zircon/$module/$Version"
        $sourceDir = Join-Path $repository $relative
        $targetDir = Join-Path $bundleDir $relative
        $stem = "$module-$Version"
        $names = @("$stem.jar", "$stem-sources.jar", "$stem-javadoc.jar", "$stem.pom", "$stem.module")
        foreach ($name in $names) {
            if (-not (Test-Path -LiteralPath (Join-Path $sourceDir $name))) { throw "Missing artifact: $relative/$name" }
        }
        [xml]$pom = Get-Content -LiteralPath (Join-Path $sourceDir "$stem.pom") -Raw
        if ($pom.project.groupId -ne 'io.github.122006.Zircon' -or $pom.project.artifactId -ne $module -or $pom.project.version -ne $Version) {
            throw "Unexpected POM coordinates: $module"
        }
        foreach ($value in @($pom.project.name, $pom.project.description, $pom.project.url,
                            $pom.project.licenses.license.name, $pom.project.licenses.license.url,
                            $pom.project.developers.developer.id, $pom.project.scm.connection, $pom.project.scm.url)) {
            if ([string]::IsNullOrWhiteSpace($value)) { throw "Incomplete Central metadata: $module" }
        }
        foreach ($dependency in $pom.project.dependencies.dependency) {
            if ($dependency.groupId -ne 'io.github.122006.Zircon' -or $dependency.version -ne $Version) {
                throw "Unexpected published dependency in $module"
            }
        }
        foreach ($jarName in @("$stem.jar", "$stem-sources.jar", "$stem-javadoc.jar")) {
            $jar = [IO.Compression.ZipFile]::OpenRead((Join-Path $sourceDir $jarName))
            try {
                if ($jarName.EndsWith('-sources.jar') -and -not ($jar.Entries.FullName -match '\.(java|groovy)$')) { throw "No sources in $jarName" }
                if ($jarName.EndsWith('-javadoc.jar') -and -not ($jar.Entries.FullName -match '(^|/)index.html$')) { throw "No documentation in $jarName" }
                foreach ($entry in $jar.Entries | Where-Object FullName -Match '\.(class|clazz)$') {
                    $stream = $entry.Open()
                    try {
                        $header = [byte[]]::new(8)
                        $stream.ReadExactly($header, 0, 8)
                        if (($header[6] * 256 + $header[7]) -ne 52) { throw "Not Java 8 bytecode: $jarName/$($entry.FullName)" }
                    } finally { $stream.Dispose() }
                }
            } finally { $jar.Dispose() }
        }
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
        foreach ($name in $names) {
            $target = Join-Path $targetDir $name
            Copy-Item -LiteralPath (Join-Path $sourceDir $name) -Destination $target
            $artifacts += $target
        }
    }
    foreach ($artifact in $artifacts) {
        $signArgs = $gpgArgs + @('--local-user', $SigningKey, '--armor', '--digest-algo', 'SHA256', '--output', "$artifact.asc", '--detach-sign', $artifact)
        if ($passphrase) {
            $passphrase | & $GpgExecutable @gpgArgs --pinentry-mode loopback --passphrase-fd 0 --local-user $SigningKey --armor --digest-algo SHA256 --output "$artifact.asc" --detach-sign $artifact
        } else {
            & $GpgExecutable @signArgs
        }
        if ($LASTEXITCODE -ne 0) { throw "Signing failed: $artifact" }
        & $GpgExecutable @gpgArgs --verify "$artifact.asc" $artifact 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Signature verification failed: $artifact" }
        foreach ($algorithm in @('MD5', 'SHA1', 'SHA256', 'SHA512')) {
            $hash = (Get-FileHash -LiteralPath $artifact -Algorithm $algorithm).Hash.ToLower()
            [IO.File]::WriteAllText("$artifact.$($algorithm.ToLower())", $hash, [Text.Encoding]::ASCII)
        }
    }
    $zip = [IO.Compression.ZipFile]::Open($bundleZip, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($file in Get-ChildItem -LiteralPath $bundleDir -Recurse -File | Sort-Object FullName) {
            $relative = [IO.Path]::GetRelativePath($bundleDir, $file.FullName).Replace('\', '/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName, $relative) | Out-Null
        }
    } finally { $zip.Dispose() }
    Write-Output "Verified and signed $($artifacts.Count) artifacts in $bundleZip"
    Get-FileHash -LiteralPath $bundleZip -Algorithm SHA256
} finally {
    $passphrase = $null
}

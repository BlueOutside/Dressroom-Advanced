param(
    [Parameter(Mandatory)][string[]]$Apk,
    [Parameter(Mandatory)][string]$JadxJar,
    [string]$AndroidSdk = "$env:USERPROFILE/AppData/Local/Android/Sdk",
    [string]$OutputDir = ''
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$module = Join-Path $project '.build/module.jar'
if (!(Test-Path -LiteralPath $module)) { throw 'Build the module once to produce .build/module.jar.' }
$sdk = (Join-Path $AndroidSdk 'platforms/android-36/android.jar')
$aapt = (Join-Path $AndroidSdk 'build-tools/36.0.0/aapt.exe')
$work = (Join-Path $project '.build/apk-audit-run')
if (!$OutputDir) { $OutputDir = Join-Path $work 'report' }
$classes = Join-Path $work 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$classpath = "$JadxJar;$sdk;$module"
& javac -J-Xms16m -J-Xmx128m -cp $classpath -d $classes (Join-Path $PSScriptRoot 'Scan.java')
if ($LASTEXITCODE -ne 0) { throw 'Scanner compilation failed.' }

$scopes = @{
    'com.android.systemui' = 'systemui'
    'com.samsung.android.app.dressroom' = 'editor'
    'com.samsung.android.app.aodservice' = 'provider'
    'com.samsung.android.app.clockpack' = 'provider'
    'com.samsung.android.wallpaper.live' = 'wallpaper'
}
$inputs = foreach ($path in $Apk) {
    $resolved = (Resolve-Path -LiteralPath $path).Path
    $packageLine = & $aapt dump badging $resolved | Select-String '^package:' | Select-Object -First 1
    $identity = [regex]::Match([string]$packageLine, "package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'")
    if (!$identity.Success) { throw "Cannot read package identity: $resolved" }
    $package = $identity.Groups[1].Value
    if (!$scopes.ContainsKey($package)) { throw "APK is outside the Samsung audit scope: $package" }
    [pscustomobject]@{
        package = $package
        code = $identity.Groups[2].Value
        version = $identity.Groups[3].Value
        path = $resolved
        sha256 = (Get-FileHash -LiteralPath $resolved).Hash
    }
}

$manifest = Join-Path $work 'inputs.json'
ConvertTo-Json -InputObject @($inputs) -Depth 4 | Set-Content -Encoding utf8 $manifest
foreach ($group in ($inputs | Group-Object sha256)) {
    $item = $group.Group[0]
    $id = $item.sha256.Substring(0, 12).ToLowerInvariant()
    $scope = $scopes[$item.package]
    & java -Xint -Xms16m -Xmx192m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=8m `
        -cp "$classes;$classpath" dev.denver.dressroomadvanced.Scan `
        (Join-Path $PSScriptRoot 'rules.tsv') $scope $item.path (Join-Path $work "$id.tsv")
    if ($LASTEXITCODE -ne 0) { throw "Scan failed: $($item.package) $($item.version) $id" }
    Write-Output "$scope $($item.version) $id"
}
& python (Join-Path $PSScriptRoot 'report.py') $manifest $work $OutputDir
if ($LASTEXITCODE -ne 0) { throw 'Audit report generation failed.' }

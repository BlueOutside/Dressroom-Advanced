param(
    [string]$Sdk = "$env:USERPROFILE\AppData\Local\Android\Sdk",
    [string]$ApiAar = '',
    [string]$BuildDir = '',
    [string]$Keystore = '',
    [string]$VersionName = '',
    [int]$VersionCode = 0,
    [string]$KeyAlias = 'alpha',
    [string]$StorePass = 'android',
    [string]$KeyPass = 'android',
    [switch]$Unsigned
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$project = $PSScriptRoot
. (Join-Path $project 'scripts/tooling.ps1')
$config = Get-ProjectConfig $project
if ($VersionName) { $config.versionName = $VersionName }
if ($VersionCode -gt 0) { $config.versionCode = $VersionCode }
if (-not $BuildDir) { $BuildDir = Join-Path $project '.build' }
$buildRoot = [IO.Path]::GetFullPath($BuildDir)
if (-not $Keystore) { $Keystore = Join-Path $project '.signing/alpha-debug.jks' }
if (-not $Unsigned -and -not (Test-Path -LiteralPath $Keystore)) { throw 'Signing key missing. Use -Unsigned for a contributor build, or restore .signing/alpha-debug.jks / pass -Keystore for an update build.' }
$tools = Join-Path $Sdk 'build-tools/36.0.0'
$androidJar = Join-Path $Sdk "platforms/android-$($config.compileSdk)/android.jar"
if (-not (Test-Path -LiteralPath $androidJar)) { throw "Install Android platform $($config.compileSdk) and build-tools 36.0.0, or pass -Sdk." }
New-Item -ItemType Directory -Force -Path $buildRoot | Out-Null
if (-not $ApiAar) {
    $cache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/io.github.libxposed/api/102.0.0'
    $cached = @(Get-ChildItem -LiteralPath $cache -Recurse -Filter 'api-102.0.0.aar' -ErrorAction SilentlyContinue)
    if ($cached.Count) { $ApiAar = $cached[0].FullName }
    else {
        $ApiAar = Join-Path $buildRoot 'api-102.0.0.aar'
        if (-not (Test-Path -LiteralPath $ApiAar)) { Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar' -OutFile $ApiAar }
    }
}
foreach ($name in @('classes','dex','gen')) {
    $target = [IO.Path]::GetFullPath((Join-Path $buildRoot $name))
    if (-not $target.StartsWith($buildRoot.TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Generated output escaped the build directory' }
    if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $target | Out-Null
}
$apiDir = Join-Path $buildRoot 'api'
New-Item -ItemType Directory -Force -Path $apiDir | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($ApiAar)
try {
    $entry = $archive.GetEntry('classes.jar')
    if ($null -eq $entry) { throw 'API AAR is missing classes.jar' }
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $apiDir 'classes.jar'),$true)
} finally { $archive.Dispose() }
$apiJar = Join-Path $apiDir 'classes.jar'
$yamlJar = Get-ProjectYamlJar $project $buildRoot
$classes = Join-Path $buildRoot 'classes'
$dex = Join-Path $buildRoot 'dex'
$genDir = Join-Path $buildRoot 'gen'
# Gradle owns version/SDK values; the CLI generates its manifest from the same values.
$manifest = [xml](Get-Content -LiteralPath (Join-Path $project 'app/src/main/AndroidManifest.xml') -Raw)
$androidNamespace = 'http://schemas.android.com/apk/res/android'
foreach ($pair in @(@('versionCode',"$($config.versionCode)"),@('versionName',$config.versionName))) {
    $attribute = $manifest.CreateAttribute('android',$pair[0],$androidNamespace)
    $attribute.Value = $pair[1]; $null = $manifest.manifest.Attributes.Append($attribute)
}
$sdkElement = $manifest.CreateElement('uses-sdk')
foreach ($name in @('minSdk','targetSdk')) {
    $attribute = $manifest.CreateAttribute('android',"${name}Version",$androidNamespace)
    $attribute.Value = "$($config.$name)"; $null = $sdkElement.Attributes.Append($attribute)
}
$null = $manifest.manifest.PrependChild($sdkElement)
$generatedManifest = Join-Path $buildRoot 'AndroidManifest.xml'; $manifest.Save($generatedManifest)
$resZip = Join-Path $buildRoot 'res.zip'
$baseApk = Join-Path $buildRoot 'unsigned.apk'
Invoke-ProjectTool (Join-Path $tools 'aapt2.exe') @('compile','--dir',(Join-Path $project 'app/src/main/res'),'-o',$resZip)
Invoke-ProjectTool (Join-Path $tools 'aapt2.exe') @('link','-o',$baseApk,'-I',$androidJar,'--manifest',$generatedManifest,'--java',$genDir,'--auto-add-overlay',$resZip)
$sourceList = Join-Path $buildRoot 'javac-sources.txt'
$sources = @(Get-ChildItem (Join-Path $project 'app/src/main/java') -Recurse -Filter '*.java') +
    @(Get-ChildItem $genDir -Recurse -Filter '*.java') |
    ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }
# Keep long checkout paths and the growing source list off the Windows command line.
[IO.File]::WriteAllLines($sourceList, [string[]]$sources, [Text.UTF8Encoding]::new($false))
Invoke-ProjectTool 'javac' @('--release','17','-encoding','UTF-8','-classpath',"$androidJar;$apiJar;$yamlJar",'-d',$classes,"@$sourceList")
$moduleJar = Join-Path $buildRoot 'module.jar'
Invoke-ProjectTool 'jar' @('--create','--file',$moduleJar,'-C',$classes,'.')
Invoke-ProjectTool (Join-Path $tools 'd8.bat') @('--lib',$androidJar,'--classpath',$apiJar,'--min-api',"$($config.minSdk)",'--output',$dex,$moduleJar,$yamlJar)
Invoke-ProjectTool 'jar' @('--update','--file',$baseApk,'-C',$dex,'classes.dex','-C',(Join-Path $project 'app/src/main/resources'),'META-INF')
$aligned = Join-Path $buildRoot 'aligned.apk'
Invoke-ProjectTool (Join-Path $tools 'zipalign.exe') @('-f','4',$baseApk,$aligned)
$outputDir = Join-Path $project 'artifacts/current'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$suffix = if ($Unsigned) { '-unsigned' } else { '' }
$output = Join-Path $outputDir "dressroom-advanced-$($config.versionName)$suffix.apk"
if ($Unsigned) {
    Copy-Item -LiteralPath $aligned -Destination $output -Force
    Write-Output "Built unsigned APK (sign before installing): $output"
    return
}
$prevKsPass = $env:APKSIGNER_KS_PASS
$prevKeyPass = $env:APKSIGNER_KEY_PASS
$env:APKSIGNER_KS_PASS = $StorePass
$env:APKSIGNER_KEY_PASS = $KeyPass
try {
    Invoke-ProjectTool (Join-Path $tools 'apksigner.bat') @('sign','--ks',$Keystore,'--ks-key-alias',$KeyAlias,'--ks-pass','env:APKSIGNER_KS_PASS','--key-pass','env:APKSIGNER_KEY_PASS','--out',$output,$aligned)
} finally {
    $env:APKSIGNER_KS_PASS = $prevKsPass
    $env:APKSIGNER_KEY_PASS = $prevKeyPass
}
Invoke-ProjectTool (Join-Path $tools 'apksigner.bat') @('verify','--verbose',$output)
Write-Output "Built: $output"

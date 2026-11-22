param(
    [ValidateSet('Jvm','Android')][string]$Suite = 'Jvm',
    [string]$Sdk = "$env:USERPROFILE\AppData\Local\Android\Sdk",
    [string]$BuildDir = '',
    [string]$Adb = 'adb',
    [switch]$SkipBuild
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$project=$PSScriptRoot
. (Join-Path $project 'scripts/tooling.ps1')
$config=Get-ProjectConfig $project
if(-not $BuildDir){$BuildDir=Join-Path $project '.build'}
$buildRoot=[IO.Path]::GetFullPath($BuildDir)
if(-not $SkipBuild){& (Join-Path $project 'build-local.ps1') -Sdk $Sdk -BuildDir $buildRoot}
$moduleJar=Join-Path $buildRoot 'module.jar'
if(-not(Test-Path -LiteralPath $moduleJar)){throw 'Build the module first or omit -SkipBuild'}
$jsonJar=Join-Path $buildRoot 'test-libs/org.json-20240303.jar'
if(-not(Test-Path -LiteralPath $jsonJar)){
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $jsonJar) | Out-Null
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar' -OutFile $jsonJar
}
$suites=Get-Content (Join-Path $project 'tests/suites.json') -Raw | ConvertFrom-Json
$names=@($suites.$Suite)
$sources=@(foreach($name in $names){
    $matches=@(Get-ChildItem (Join-Path $project 'tests') -Recurse -Filter "$name.java")
    if($matches.Count -ne 1){throw "Expected one source for $name"}
    $matches[0].FullName
})
$androidJar=Join-Path $Sdk "platforms/android-$($config.compileSdk)/android.jar"
$apiJar=Join-Path $buildRoot 'api/classes.jar'
$testRoot=Join-Path $buildRoot "tests/$Suite"
$classes=Join-Path $testRoot 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
Invoke-ProjectTool 'javac' (@('--release','17','-encoding','UTF-8','-classpath',"$jsonJar;$androidJar;$moduleJar;$apiJar",'-d',$classes)+$sources)
$results=[Collections.Generic.List[string]]::new()
if($Suite -eq 'Jvm'){
    foreach($name in $names){
        $output=@(Invoke-ProjectTool 'java' @('-classpath',"$classes;$jsonJar;$moduleJar;$androidJar", "dev.denver.dressroomadvanced.$name"))
        foreach($line in $output){$results.Add("$line");Write-Output $line}
    }
}else{
    $tools=Join-Path $Sdk 'build-tools/36.0.0'
    $probe=Join-Path $testRoot 'probe.jar';$dex=Join-Path $testRoot 'dex'
    New-Item -ItemType Directory -Force -Path $dex | Out-Null
    Invoke-ProjectTool 'jar' @('--create','--file',$probe,'-C',$classes,'.')
    Invoke-ProjectTool (Join-Path $tools 'd8.bat') @('--lib',$androidJar,'--min-api',"$($config.minSdk)",'--output',$dex,$probe,$moduleJar,$apiJar)
    $deviceJar=Join-Path $testRoot 'device.jar'
    Invoke-ProjectTool 'jar' @('--create','--file',$deviceJar,'-C',$dex,'classes.dex')
    $remote='/data/local/tmp/dressroom-advanced-contracts.jar'
    Invoke-ProjectTool $Adb @('push',$deviceJar,$remote)
    foreach($name in $names){
        $output=@(Invoke-ProjectTool $Adb @('shell',"su 1000 -c 'CLASSPATH=$remote app_process / dev.denver.dressroomadvanced.$name'"))
        foreach($line in $output){$results.Add("$line");Write-Output $line}
    }
}
$results | Set-Content -LiteralPath (Join-Path $testRoot 'results.txt') -Encoding utf8
Write-Output "Passed $($names.Count) $Suite suites. Results: $testRoot/results.txt"

param(
    [string]$Sdk = "$env:USERPROFILE\AppData\Local\Android\Sdk",
    [string]$Adb = 'adb',
    [string]$BuildDir = ''
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$project=$PSScriptRoot
. (Join-Path $project 'scripts/tooling.ps1')
$config=Get-ProjectConfig $project
if(-not $BuildDir){$BuildDir=Join-Path $project '.build'}
$testRoot=Join-Path ([IO.Path]::GetFullPath($BuildDir)) 'tests/native-glyph'
$classes=Join-Path $testRoot 'classes';$dex=Join-Path $testRoot 'dex'
New-Item -ItemType Directory -Force -Path $classes,$dex | Out-Null
$androidJar=Join-Path $Sdk "platforms/android-$($config.compileSdk)/android.jar"
$tools=Join-Path $Sdk 'build-tools/36.0.0'
$nativeSource=Join-Path $project 'tests/native-glyph'
$runtimeSource=Join-Path $project 'app/src/main/java/dev/denver/dressroomadvanced'
$sources=@((Join-Path $nativeSource 'NativeGlyphCache.java'),(Join-Path $nativeSource 'GlyphInstrumentation.java'))
$sources+=@('GlyphBlurReadiness.java','GlyphOutlineMemo.java','Reflect.java' | ForEach-Object {Join-Path $runtimeSource $_})
Invoke-ProjectTool 'javac' (@('--release','17','-encoding','UTF-8','-classpath',$androidJar,'-d',$classes)+$sources)
$jar=Join-Path $testRoot 'probe.jar'
Invoke-ProjectTool 'jar' @('--create','--file',$jar,'-C',$classes,'dev')
Invoke-ProjectTool (Join-Path $tools 'd8.bat') @('--lib',$androidJar,'--min-api','29','--output',$dex,$jar)
$unsigned=Join-Path $testRoot 'unsigned.apk';$aligned=Join-Path $testRoot 'aligned.apk';$apk=Join-Path $testRoot 'probe.apk'
Invoke-ProjectTool (Join-Path $tools 'aapt.exe') @('package','-f','-M',(Join-Path $nativeSource 'AndroidManifest.xml'),'-I',$androidJar,'-F',$unsigned)
Invoke-ProjectTool 'jar' @('--update','--file',$unsigned,'-C',$dex,'classes.dex')
Invoke-ProjectTool (Join-Path $tools 'zipalign.exe') @('-f','4',$unsigned,$aligned)
Invoke-ProjectTool (Join-Path $tools 'apksigner.bat') @('sign','--ks',(Join-Path $project '.signing/alpha-debug.jks'),'--ks-key-alias','alpha','--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,$aligned)
$package='dev.denver.nativeglyphprobe'
$existing=@(& $Adb shell pm path $package)
if($existing -match '^package:'){throw 'Native test package already exists; inspect/remove the old probe before running this suite.'}
$installed=$false
try{
    Invoke-ProjectTool $Adb @('install','-r',$apk);$installed=$true
    $result=@(Invoke-ProjectTool $Adb @('shell','am','instrument','-w',"$package/$package.GlyphInstrumentation"))
    $result | Set-Content -LiteralPath (Join-Path $testRoot 'results.txt') -Encoding utf8
    $result | Write-Output
    if(-not($result -match 'NATIVE_GLYPH_PASS=8')){throw 'Native glyph assertions failed; inspect the instrumentation output'}
}finally{
    if($installed){Invoke-ProjectTool $Adb @('uninstall',$package)}
}

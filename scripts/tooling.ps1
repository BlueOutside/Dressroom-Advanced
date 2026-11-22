function Get-ProjectConfig([string]$Project) {
    $source = Get-Content -LiteralPath (Join-Path $Project 'app/build.gradle.kts') -Raw
    $config = @{}
    foreach ($key in @('compileSdk','minSdk','targetSdk','versionCode')) {
        $match = [regex]::Match($source,"\b$key\s*=\s*(\d+)")
        if (-not $match.Success) { throw "Missing $key in app/build.gradle.kts" }
        $config[$key] = [int]$match.Groups[1].Value
    }
    $match = [regex]::Match($source,'versionName\s*=\s*"([^"]+)"')
    if (-not $match.Success) { throw 'Missing versionName in app/build.gradle.kts' }
    $config.versionName = $match.Groups[1].Value
    return [pscustomobject]$config
}
function Invoke-ProjectTool([string]$Program,[string[]]$Arguments) {
    $previous = $env:JAVA_TOOL_OPTIONS
    try {
        $env:JAVA_TOOL_OPTIONS = ($previous + ' -Xint -Xms16m -Xmx256m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=8m -XX:MaxMetaspaceSize=96m -XX:ActiveProcessorCount=2').Trim()
        & $Program @Arguments
        if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE" }
    } finally { $env:JAVA_TOOL_OPTIONS = $previous }
}

function Get-ProjectYamlJar([string]$Project,[string]$BuildRoot) {
    $source = Get-Content -LiteralPath (Join-Path $Project 'app/build.gradle.kts') -Raw
    $match = [regex]::Match($source, 'org\.yaml:snakeyaml:([^"\s]+)')
    if (-not $match.Success) { throw 'Missing SnakeYAML dependency in app/build.gradle.kts' }
    $version = $match.Groups[1].Value
    $directory = Join-Path $BuildRoot 'libs'
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $jar = Join-Path $directory "snakeyaml-$version.jar"
    if (-not (Test-Path -LiteralPath $jar)) {
        Invoke-WebRequest -Uri "https://repo.maven.apache.org/maven2/org/yaml/snakeyaml/$version/snakeyaml-$version.jar" -OutFile $jar
    }
    return $jar
}

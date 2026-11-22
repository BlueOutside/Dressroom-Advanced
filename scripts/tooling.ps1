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
    $name = [IO.Path]::GetFileNameWithoutExtension($Program)
    if ($name -eq 'javac') {
        $Arguments = @('-J-Xms128m','-J-Xmx1024m') + $Arguments
    } elseif ($name -eq 'java') {
        $Arguments = @('-Xms128m','-Xmx1024m') + $Arguments
    } elseif ($name -eq 'jar') {
        $Arguments = @('-J-Xms128m','-J-Xmx1024m') + $Arguments
    }
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE" }
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

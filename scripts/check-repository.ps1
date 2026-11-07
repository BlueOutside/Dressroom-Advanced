# Check both the index and untracked publishable files; ignored local evidence is not input.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$root = & git -C $project rev-parse --show-toplevel
if ($LASTEXITCODE -ne 0) { throw 'Run git init in the project root first.' }
if ([IO.Path]::GetFullPath($root) -ne $project) { throw 'The Git root must be the module project, not its workspace parent.' }
$files = @(& git -C $project -c core.quotepath=false ls-files --cached --others --exclude-standard | Sort-Object -Unique)
if ($LASTEXITCODE -ne 0) { throw 'Could not list repository files' }
$issues = [Collections.Generic.List[string]]::new()
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$privatePath = '(^|/)(\.signing|\.build|\.gradle|artifacts|captures|private|analysis-inputs|archive)(/|$)|^docs/validation/|(^|/)\.env($|\.)|\.(jks|keystore|p12|pfx|pem|apk|apks|aab|dex|class|hprof|heapdump|mp4|zip|log)$'
$secret = '-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----|\bgh[pousr]_[A-Za-z0-9]{30,}|\bgithub_pat_[A-Za-z0-9_]{40,}'
foreach ($file in $files) {
    if ($file -match $privatePath) { $issues.Add("Private/generated path: $file"); continue }
    $path = Join-Path $project $file
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
    $item = Get-Item -LiteralPath $path
    if ($item.Length -gt 1MB) { $issues.Add("Unexpected large source file: $file") }
    if ($item.Extension -in @('.java','.xml','.json','.md','.ps1','.py','.kts','.yml','.yaml','.properties')) {
        try { $source = [IO.File]::ReadAllText($path, $utf8) }
        catch [Text.DecoderFallbackException] { $issues.Add("Invalid UTF-8: $file"); continue }
        if ($source.Contains([char]0xFFFD)) { $issues.Add("Replacement character in $file") }
        if ($source.Contains([char]0x2014)) { $issues.Add("Em dash in $file; use plain punctuation") }
        if ($source -match '\u00C3[\u0080-\u00BF]|\u00E2[\u0080-\u00BF\u20AC\u2020]') {
            $issues.Add("Possible double-encoded punctuation in $file")
        }
        if ($source -match $secret) { $issues.Add("Possible private credential in $file (value suppressed)") }
        if ($source -match '(?i)C:[/\\]Users[/\\][^/\\\s]+') { $issues.Add("Personal absolute path in $file") }
    }
}
# Ignore rules are tested even in a fresh clone without any private files present.
foreach ($example in @('.signing/example.jks','.build/module.jar','artifacts/current/example.apk','docs/validation/device.txt','.env','private/settings.xml')) {
    & git -C $project check-ignore -q -- $example
    if ($LASTEXITCODE -ne 0) { $issues.Add("Missing ignore rule: $example") }
}
if ($issues.Count) { throw ($issues -join "`n") }
Write-Output "Repository checks pass for $($files.Count) source/documentation files. Review staged changes before publishing; this is not a complete secret scanner."

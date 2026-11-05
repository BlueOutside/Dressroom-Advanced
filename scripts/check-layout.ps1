$ErrorActionPreference='Stop'
$project=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
foreach($script in Get-ChildItem -LiteralPath $project -Recurse -Filter '*.ps1'){
    if($script.FullName.Contains([IO.Path]::DirectorySeparatorChar+'.build'+[IO.Path]::DirectorySeparatorChar)){continue}
    $tokens=$null;$errors=$null
    $null=[Management.Automation.Language.Parser]::ParseFile($script.FullName,[ref]$tokens,[ref]$errors)
    if($errors.Count){throw "PowerShell parse errors in $($script.FullName): $errors"}
}
if(@(Get-ChildItem -LiteralPath $project -File -Filter '*.apk').Count){throw 'Loose APKs remain in the project root'}
if(@(Get-ChildItem (Join-Path $project 'tests') -File -Filter '*.java').Count){throw 'Uncategorized test sources remain'}
$runtime=Join-Path $project 'app/src/main/java'
if(@(Get-ChildItem $runtime -Recurse -File | Where-Object {$_.Name -match '(Probe|Trace)\.java$'}).Count){throw 'Temporary probe/trace source remains in the production tree'}
$broken=[Collections.Generic.List[string]]::new()
$documents=@(Get-ChildItem (Join-Path $project 'docs') -Recurse -Filter '*.md')+@(Get-Item (Join-Path $project 'README.md'))+@(Get-Item (Join-Path $project 'tests/README.md'))
foreach($document in $documents){
    foreach($match in [regex]::Matches((Get-Content $document.FullName -Raw),'\]\(([^)\r\n]+)\)')){
        $link=$match.Groups[1].Value.Trim('<','>')
        if($link -match '^([a-zA-Z][a-zA-Z0-9+.-]*:|#|/)'){continue}
        $link=[Uri]::UnescapeDataString(($link -split '#',2)[0])
        if($link -and -not(Test-Path -LiteralPath (Join-Path $document.DirectoryName $link))){$broken.Add("$($document.Name): $link")}
    }
}
if($broken.Count){throw "Broken local Markdown links:`n$($broken -join "`n")"}
Write-Output 'Layout, PowerShell syntax and local Markdown links pass.'

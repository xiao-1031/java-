param([Parameter(Mandatory=$true)][string]$LogisimJar)
$ErrorActionPreference = 'Stop'
$dependency = (Resolve-Path -LiteralPath $LogisimJar).Path
$buildRoot = Join-Path $PSScriptRoot ('build/' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $buildRoot 'classes'
New-Item -ItemType Directory -Force $classes | Out-Null
$sources = Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
& javac --release 8 -Xlint:-options -encoding UTF-8 -cp $dependency -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
Copy-Item (Join-Path $PSScriptRoot 'src/resources/*') $classes -Recurse -Force
$dist = Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Force $dist | Out-Null
& jar cfm (Join-Path $dist 'logisim-learner.jar') (Join-Path $PSScriptRoot 'manifest.mf') -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
Copy-Item -LiteralPath $dependency -Destination (Join-Path $dist 'logisim.jar') -Force
Write-Host "Built: $dist/logisim-learner.jar"
Write-Host 'Run: java -jar dist/logisim-learner.jar'

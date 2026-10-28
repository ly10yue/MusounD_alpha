param([string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot)
$jdk='C:\Program Files\Android\Android Studio\jbr'
if (Test-Path "$jdk\bin\javac.exe") { $env:JAVA_HOME=$jdk; $env:PATH="$jdk\bin;$env:PATH" }
function Run([string]$exe,[string[]]$arguments){& $exe @arguments;if($LASTEXITCODE -ne 0){throw "$exe failed"}}
$bt=(Get-ChildItem "$Sdk\build-tools" -Directory|Sort-Object Name -Descending|Select-Object -First 1).FullName
$platform=(Get-ChildItem "$Sdk\platforms" -Directory|Sort-Object Name -Descending|Select-Object -First 1).FullName
$out=Join-Path (Get-Location) 'build'
New-Item -ItemType Directory -Force "$out\android-tests","$out\android-test-dex"|Out-Null
Run 'javac' (@('--release','8','-encoding','UTF-8','-cp',"$platform\android.jar;$out\classes",'-d',"$out\android-tests") + @(Get-ChildItem tests\android -Filter '*.java' | ForEach-Object FullName))
Run 'jar' @('cf',"$out\tests.jar",'-C',"$out\android-tests",'.')
$assetArgs=@()
if(Test-Path "$out\editor-tests\roundtrip.interval") { $assetArgs=@('-A',"$out\editor-tests") }
Run "$bt\aapt2.exe" (@('link','-o',"$out\test-base.apk",'-I',"$platform\android.jar",'--manifest','tests\android\AndroidManifest.xml')+$assetArgs)
Run "$bt\d8.bat" @('--lib',"$platform\android.jar",'--classpath',"$out\code.jar",'--min-api','26','--output',"$out\android-test-dex", "$out\tests.jar")
Run 'jar' @('uf',"$out\test-base.apk",'-C',"$out\android-test-dex",'classes.dex')
Run "$bt\zipalign.exe" @('-f','4',"$out\test-base.apk", "$out\test-aligned.apk")
Run "$bt\apksigner.bat" @('sign','--ks',"$out\debug.jks",'--ks-pass','pass:android','--out',"$out\Interval-tests.apk", "$out\test-aligned.apk")

param([string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$jdkBin = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\jar.exe")) { "$env:JAVA_HOME\bin" } elseif (Test-Path 'C:\Program Files\Android\Android Studio\jbr\bin\jar.exe') { 'C:\Program Files\Android\Android Studio\jbr\bin' } else { Split-Path (Get-Command javac).Source }
$env:JAVA_HOME = Split-Path $jdkBin
$env:PATH = "$jdkBin;$env:PATH"
function Invoke-Checked([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed ($LASTEXITCODE)" }
}
$bt = (Get-ChildItem -LiteralPath "$Sdk\build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$platform = (Get-ChildItem -LiteralPath "$Sdk\platforms" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
if (!$bt -or !$platform) { throw 'Install an Android SDK platform and build-tools first.' }
$out = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force "$out\classes","$out\dex","$out\tests" | Out-Null
$javaSources = @(Get-ChildItem 'app\src\main\java' -Filter '*.java' -Recurse | ForEach-Object FullName)
Invoke-Checked 'javac' (@('-encoding','UTF-8','--release','8','-classpath',"$platform\android.jar",'-d',"$out\classes") + $javaSources)
Invoke-Checked "$bt\aapt2.exe" @('compile','--dir','app\src\main\res','-o',"$out\resources.zip")
Invoke-Checked "$bt\aapt2.exe" @('link','-o',"$out\base.apk",'-I',"$platform\android.jar",'--manifest','app\src\main\AndroidManifest.xml','-A','app\src\main\assets','-0','ogg',"$out\resources.zip")
Invoke-Checked 'jar' @('cf',"$out\code.jar",'-C',"$out\classes",'.')
Invoke-Checked "$bt\d8.bat" @('--lib',"$platform\android.jar",'--min-api','26','--output',"$out\dex", "$out\code.jar")
Copy-Item "$out\base.apk" "$out\unsigned.apk" -Force
Invoke-Checked 'jar' @('uf',"$out\unsigned.apk",'-C',"$out\dex",'classes.dex')
Invoke-Checked "$bt\zipalign.exe" @('-f','4',"$out\unsigned.apk", "$out\aligned.apk")
if (!(Test-Path "$out\debug.jks")) {
    Invoke-Checked 'keytool' @('-genkeypair','-keystore',"$out\debug.jks",'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Android Debug,O=Android,C=US','-keyalg','RSA','-keysize','2048','-validity','10000')
}
Invoke-Checked "$bt\apksigner.bat" @('sign','--ks',"$out\debug.jks",'--ks-pass','pass:android','--out',"$out\Interval-debug.apk", "$out\aligned.apk")
Invoke-Checked "$bt\apksigner.bat" @('verify','--verbose',"$out\Interval-debug.apk")
Write-Host "Built: $out\Interval-debug.apk"

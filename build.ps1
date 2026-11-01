param([switch]$Legacy,[ValidateSet('Windows','Android','all')][string]$Target='all',[string]$GodotPath='', [string]$Sdk="$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
if ($Legacy) {
    & "$PSScriptRoot\tools\build_android_legacy.ps1" -Sdk $Sdk
} else {
    $buildArgs=@('tools/build_godot.py','--target',$Target)
    if ($GodotPath) { $buildArgs+=@('--godot',$GodotPath) }
    python @buildArgs
    if ($LASTEXITCODE -ne 0) { throw 'Godot client build failed' }
}

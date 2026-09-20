$ErrorActionPreference = 'Stop'

$javaHome = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$adb = Join-Path $PSScriptRoot '..\platform-tools\adb.exe'
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

& (Join-Path $PSScriptRoot 'gradlew.bat') :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Android 构建失败' }

& $adb wait-for-device
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) {
    Write-Host '检测到旧版签名不一致，保留应用数据后重新安装。'
    & $adb uninstall -k com.bianqujian.app
    if ($LASTEXITCODE -ne 0) { throw '旧版应用移除失败' }
    & $adb install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK 安装失败' }
}

& $adb shell am force-stop com.bianqujian.app
& $adb shell monkey -p com.bianqujian.app 1
Write-Host '便取件 MVP 已安装并重启。'

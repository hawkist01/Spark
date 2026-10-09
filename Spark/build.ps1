$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path   # Spark dir
$bt   = Join-Path (Split-Path -Parent $root) 'buildtools' # APP/buildtools

# ---- Paths ----
$api   = '36'        # Android 16, newest published platform (Android 17 SDK not out yet)
$btVer = '36.1.0'
$jdkDir = (Get-ChildItem (Join-Path $bt 'jdk') -Directory | Select-Object -First 1 -ExpandProperty FullName)
$java   = Join-Path $jdkDir 'bin\java.exe'
$javac  = Join-Path $jdkDir 'bin\javac.exe'
$sdk    = Join-Path $bt 'sdk'
$platDir= Join-Path $sdk "platforms\android-$api"
$btDir  = Join-Path $sdk "build-tools\$btVer"
$androidJar = Join-Path $platDir 'android.jar'
$aapt2  = Join-Path $btDir 'aapt2.exe'
$zipalign = Join-Path $btDir 'zipalign.exe'
$apksignerJar = Join-Path $btDir 'lib\apksigner.jar'

$srcDir   = Join-Path $root 'app\src\main'
$resDir   = Join-Path $srcDir 'res'
$manifest = Join-Path $srcDir 'AndroidManifest.xml'
$javaSrc  = Join-Path $srcDir 'java'
$ks       = Join-Path $root 'keystore\spark.jks'
$out      = Join-Path $root 'build'
$gen      = Join-Path $out 'gen'
$compiled = Join-Path $out 'res.zip'
$classes  = Join-Path $out 'classes'
$dexOut   = Join-Path $out 'dex'
$baseApk  = Join-Path $out 'base.apk'
$aligned  = Join-Path $out 'aligned.apk'
$dist     = Join-Path $root 'dist'
$finalApk = Join-Path $dist 'Spark.apk'

$env:JAVA_HOME = $jdkDir
$env:Path      = "$jdkDir\bin;" + $env:Path

function Step($m){ Write-Host "`n========== $m ==========" -ForegroundColor Cyan }
function Run($cmd, [string[]]$argsArr){
  # Native tools (javac especially) write warnings to stderr, and with
  # $ErrorActionPreference='Stop' PowerShell promotes any stderr output to a
  # terminating error -- which aborted the whole build on a harmless warning.
  # Run with Continue and judge success by the exit code instead.
  $prev = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try { & $cmd @argsArr } finally { $ErrorActionPreference = $prev }
  if ($LASTEXITCODE -ne 0) { Write-Host "FAILED: $cmd" -ForegroundColor Red; exit $LASTEXITCODE }
}

# ---- 1. Prepare SDK layout (idempotent) ----
Step "Preparing SDK"
$dl = Join-Path $env:TEMP 'sparkbuild\sdkdl'
if (-not (Test-Path $androidJar)) {
  $zip = Get-ChildItem (Join-Path $dl "platform-$api*.zip") -File | Select-Object -First 1 -ExpandProperty FullName
  if (-not $zip) { throw "platform $api zip missing in $dl (run buildtools/download-sdk.js)" }
  $t = Join-Path $env:TEMP 'sparkbuild\plat'; New-Item -ItemType Directory -Force $t | Out-Null
  tar -xf $zip -C $t
  $found = Get-ChildItem $t -Filter android.jar -Recurse | Select-Object -First 1
  if (-not $found) { throw 'android.jar not found in platform zip' }
  $srcPlat = Split-Path -Parent $found.FullName
  New-Item -ItemType Directory -Force $platDir | Out-Null
  Copy-Item (Join-Path $srcPlat '*') $platDir -Recurse -Force
  Write-Host "Installed platform -> $platDir"
}
if (-not (Test-Path $aapt2)) {
  $zip = Get-ChildItem (Join-Path $dl 'build-tools*.zip') -File | Sort-Object Length -Descending | Select-Object -First 1 -ExpandProperty FullName
  if (-not $zip) { throw "build-tools zip missing in $dl (run buildtools/download-sdk.js)" }
  $t = Join-Path $env:TEMP 'sparkbuild\bt'; New-Item -ItemType Directory -Force $t | Out-Null
  tar -xf $zip -C $t
  $found = Get-ChildItem $t -Filter aapt2.exe -Recurse | Select-Object -First 1
  if (-not $found) { throw 'aapt2.exe not found in build-tools zip' }
  $srcBt = Split-Path -Parent $found.FullName
  New-Item -ItemType Directory -Force $btDir | Out-Null
  Copy-Item (Join-Path $srcBt '*') $btDir -Recurse -Force
  Write-Host "Installed build-tools -> $btDir"
}

# ---- 2. aapt2 compile + link ----
Step "aapt2 compile"
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $out,$gen,$classes,$dexOut,$dist | Out-Null
Run $aapt2 @('compile','--dir',$resDir,'-o',$compiled)

Step "aapt2 link"
Run $aapt2 @('link','-o',$baseApk,'-I',$androidJar,'--manifest',$manifest,'--java',$gen,
             '--min-sdk-version','26','--target-sdk-version',$api,
             '--auto-add-overlay',$compiled)

# ---- 3. javac ----
Step "javac"
$rJava = Join-Path $gen 'com\spark\app\R.java'
$files = @(Get-ChildItem -Path $javaSrc -Recurse -Filter *.java | ForEach-Object FullName)
if (Test-Path $rJava) { $files += $rJava }
# `--release 11` rather than -source/-target 11: on JDK 17 the split form warns
# about the system-modules path on every build.
Run $javac (@('--release','11','-nowarn','-cp',$androidJar,'-d',$classes) + @($files))

# ---- 4. d8 -> classes.dex ----
Step "d8"
$d8Jar = Join-Path $btDir 'lib\d8.jar'
$classesJar = Join-Path $out 'classes.jar'
Run (Join-Path $jdkDir 'bin\jar.exe') @('-cf',$classesJar,'-C',$classes,'.')
Run $java @('-cp',$d8Jar,'com.android.tools.r8.D8','--release','--lib',$androidJar,'--output',$dexOut,$classesJar)

# ---- 5. add classes.dex into apk (jar update, no manifest) ----
Step "add dex"
Push-Location $dexOut
try { Run (Join-Path $jdkDir 'bin\jar.exe') @('-ufM',$baseApk,'classes.dex') } finally { Pop-Location }

# ---- 6. zipalign ----
Step "zipalign"
Run $zipalign @('-f','-p','4',$baseApk,$aligned)

# ---- 7. sign ----
Step "apksigner"
# Keystore password is never hardcoded (repo is public): env var SPARK_KS_PASS,
# or a line in the gitignored Spark\keystore\ks-pass.txt. Generate your own keystore.
$ksPassFile = Join-Path (Split-Path -Parent $ks) 'ks-pass.txt'
if ($env:SPARK_KS_PASS) { $ksPass = $env:SPARK_KS_PASS }
elseif (Test-Path $ksPassFile) { $ksPass = (Get-Content $ksPassFile -Raw).Trim() }
else { throw "No keystore password. Set SPARK_KS_PASS or create $ksPassFile (gitignored)." }
Run $java @('-jar',$apksignerJar,'sign','--ks',$ks,'--ks-pass',"pass:$ksPass",
            '--key-pass',"pass:$ksPass",'--out',$finalApk,$aligned)

Write-Host "`nBUILD OK -> $finalApk" -ForegroundColor Green
Get-Item $finalApk | Select-Object FullName, @{n='MB';e={[math]::Round($_.Length/1MB,1)}} | Format-List

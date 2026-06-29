param([switch]$NoBuild)

$RootDir   = $PSScriptRoot
$WebDir    = Join-Path $RootDir "skillbridge-web"
$ExportDir = Join-Path $RootDir "skillbridge-export"
$JarFile   = Join-Path $WebDir "target\skillbridge-web-0.0.1-SNAPSHOT.jar"
$LogDir    = Join-Path $RootDir "logs"

$JavaPort  = 8080
$FlaskPort = 5000

if (!(Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir -Force | Out-Null }

function Get-PidByPort($port) {
    $conn = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($conn) { return $conn.OwningProcess }
    return $null
}

function Wait-ForPort($port, $timeoutSeconds = 30) {
    $end = [DateTime]::Now.AddSeconds($timeoutSeconds)
    while ([DateTime]::Now -lt $end) {
        $procId = Get-PidByPort $port
        if ($procId) { return $procId }
        Start-Sleep -Milliseconds 500
    }
    return $null
}

function Stop-Services {
    $JavaPort, $FlaskPort | ForEach-Object {
        $procId = Get-PidByPort $_
        if ($procId) { Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue }
    }
}

function Show-Status {
    $javaPid  = Get-PidByPort $JavaPort
    $flaskPid = Get-PidByPort $FlaskPort
    Write-Host "========================================"
    Write-Host " SkillBridge Dev Status"
    Write-Host "========================================"
    if ($javaPid)  { Write-Host "  Java  [:${JavaPort}] RUNNING  PID=${javaPid}"  -ForegroundColor Green }
    else           { Write-Host "  Java  [:${JavaPort}] STOPPED"                 -ForegroundColor Red }
    if ($flaskPid) { Write-Host "  Flask [:${FlaskPort}] RUNNING  PID=${flaskPid}" -ForegroundColor Green }
    else           { Write-Host "  Flask [:${FlaskPort}] STOPPED"                 -ForegroundColor Red }
    Write-Host "========================================"
}

switch ($args[0]) {
    "stop"    { Stop-Services; Write-Host "Services stopped" -ForegroundColor Green; return }
    "status"  { Show-Status;   return }
    "restart" { Stop-Services; Start-Sleep 3 }
}

$existingJava  = Get-PidByPort $JavaPort
$existingFlask = Get-PidByPort $FlaskPort
if ($existingJava -or $existingFlask) {
    Write-Host "Existing services detected, stopping..." -ForegroundColor Yellow
    Stop-Services
    Start-Sleep 3
}

Write-Host "Starting services..." -ForegroundColor Cyan

Write-Host "============================================" -ForegroundColor Cyan
Write-Host " SkillBridge Dev Starter"                      -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Project: $RootDir"
Write-Host "============================================" -ForegroundColor Cyan

# ---- [1/3] Python dependencies ----
Write-Host "`n[1/3] Checking Python dependencies..." -ForegroundColor Cyan
$reqFile = Join-Path $ExportDir "requirements.txt"
if (Test-Path $reqFile) {
    $result = pip install -r $reqFile 2>&1
    if ($LASTEXITCODE -ne 0) {
        if ($result -match "ERROR") {
            Write-Host "  pip install failed. Run: pip install -r $reqFile" -ForegroundColor Red
            exit 1
        }
    }
}
Write-Host "  Python deps ready" -ForegroundColor Green

# ---- [2/3] Build Java ----
Write-Host "`n[2/3] Building Java project..." -ForegroundColor Cyan
$jarExists = Test-Path $JarFile
if ($NoBuild -and $jarExists) {
    Write-Host "  Skipping build (-NoBuild)" -ForegroundColor Green
} elseif (!$NoBuild -or !$jarExists) {
    Write-Host "  Running mvn clean package -DskipTests ..." -ForegroundColor Gray
    $buildResult = & mvn clean package -DskipTests -f "$WebDir\pom.xml" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  Maven build failed" -ForegroundColor Red
        Write-Host $buildResult
        exit 1
    }
    Write-Host "  Build success" -ForegroundColor Green
} else {
    Write-Host "  JAR exists, skipping build" -ForegroundColor Green
}

# ---- [3/3] Start services ----
Write-Host "`n[3/3] Starting services..." -ForegroundColor Cyan

# Flask
Write-Host "  Starting Flask (port $FlaskPort)..." -ForegroundColor Gray
$env:FLASK_PORT = "$FlaskPort"
$env:FLASK_HOST = "127.0.0.1"
$env:FLASK_DEBUG = "0"
$flaskOut = Join-Path $LogDir "flask-stdout.log"
$flaskErr = Join-Path $LogDir "flask-stderr.log"
$flaskArgs = @{
    FilePath  = "python"
    ArgumentList = "-B", "app.py"
    WorkingDirectory = $ExportDir
    WindowStyle = "Hidden"
    PassThru = $true
    RedirectStandardOutput = $flaskOut
    RedirectStandardError = $flaskErr
}
Start-Process @flaskArgs | Out-Null

$flaskPid = Wait-ForPort $FlaskPort 15
if ($flaskPid) {
    Write-Host "  Flask started (PID $flaskPid)" -ForegroundColor Green
} else {
    Write-Host "  Flask failed to start, check logs in $LogDir" -ForegroundColor Red
    Get-Content (Join-Path $LogDir "flask-stderr.log") -Tail 10
    exit 1
}

# Java
Write-Host "  Starting Java (port $JavaPort)..." -ForegroundColor Gray
$javaOut = Join-Path $LogDir "java-stdout.log"
$javaErr = Join-Path $LogDir "java-stderr.log"
$javaArgs = @{
    FilePath  = "java"
    ArgumentList = "-jar", $JarFile, "--server.port=$JavaPort"
    WorkingDirectory = $WebDir
    WindowStyle = "Hidden"
    PassThru = $true
    RedirectStandardOutput = $javaOut
    RedirectStandardError = $javaErr
}
Start-Process @javaArgs | Out-Null

$javaPid = Wait-ForPort $JavaPort 60
if ($javaPid) {
    Write-Host "  Java started (PID $javaPid)" -ForegroundColor Green
} else {
    Write-Host "  Java failed to start, check logs in $LogDir" -ForegroundColor Red
    Get-Content (Join-Path $LogDir "java-stderr.log") -Tail 15
    exit 1
}

# Health check
Write-Host "`nVerifying services..." -ForegroundColor Cyan
try {
    $flaskCheck = Invoke-WebRequest -Uri "http://127.0.0.1:$FlaskPort/api/health" -UseBasicParsing -TimeoutSec 5
    if ($flaskCheck.Content -match '"status"\s*:\s*"ok"') { Write-Host "  Flask  health check passed" -ForegroundColor Green }
} catch { Write-Host "  Flask  health check failed: $_" -ForegroundColor Yellow }
try {
    $javaCheck = Invoke-WebRequest -Uri "http://127.0.0.1:$JavaPort/" -UseBasicParsing -TimeoutSec 5
    if ($javaCheck.StatusCode -eq 200) { Write-Host "  Java   health check passed" -ForegroundColor Green }
} catch { Write-Host "  Java   health check failed: $_" -ForegroundColor Yellow }

Write-Host
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  All services started!"                      -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Teacher  : http://localhost:$JavaPort/"              -ForegroundColor Yellow
Write-Host "  Student  : http://localhost:$JavaPort/student/login" -ForegroundColor Yellow
Write-Host "  H2       : http://localhost:$JavaPort/h2-console"    -ForegroundColor Yellow
Write-Host "  Flask    : http://127.0.0.1:$FlaskPort/api/health"  -ForegroundColor Yellow
Write-Host "  Logs     : $LogDir"                                  -ForegroundColor Yellow
Write-Host "============================================" -ForegroundColor Cyan
Write-Host
Write-Host "Commands:" -ForegroundColor Gray
Write-Host "  .\start_dev.ps1 stop"     -ForegroundColor Gray
Write-Host "  .\start_dev.ps1 status"   -ForegroundColor Gray
Write-Host "  .\start_dev.ps1 restart"  -ForegroundColor Gray
Write-Host "  .\start_dev.ps1 -NoBuild" -ForegroundColor Gray

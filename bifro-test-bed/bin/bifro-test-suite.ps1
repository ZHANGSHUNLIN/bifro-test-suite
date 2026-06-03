<#
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
#>

param(
    [Parameter(Position = 0)]
    [string]$Command,

    [Parameter(Position = 1)]
    [string]$Profile,

    [Parameter(Position = 2)]
    [string]$ExtraJvmOptions
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (Test-Path (Join-Path $ScriptDir "..\conf")) {
    $AppHome = (Resolve-Path (Join-Path $ScriptDir "..")).Path
} elseif (Test-Path (Join-Path $ScriptDir "conf")) {
    $AppHome = (Resolve-Path $ScriptDir).Path
} elseif ($env:APP_HOME) {
    $AppHome = $env:APP_HOME
} else {
    $AppHome = "C:\bifro-test-suite"
}

$AppName = if ($env:APP_NAME) { $env:APP_NAME } else { "bifro-test-suite" }
$MainClass = if ($env:MAIN_CLASS) { $env:MAIN_CLASS } else { "org.apache.bifromq.testsuite.app.App" }
$ConfigDir = Join-Path $AppHome "conf"
$LibDir = Join-Path $AppHome "lib"
$LogDir = Join-Path $AppHome "logs"
$PidFile = Join-Path $AppHome "bin\pid"
$LogFile = Join-Path $LogDir "$AppName.log"
$StdErrFile = Join-Path $LogDir "$AppName.stderr.log"
$StopTimeout = if ($env:STOP_TIMEOUT) { [int]$env:STOP_TIMEOUT } else { 150 }
$StartTimeout = if ($env:START_TIMEOUT) { [int]$env:START_TIMEOUT } else { 60 }
$StartStableSeconds = if ($env:START_STABLE_SECONDS) { [int]$env:START_STABLE_SECONDS } else { 3 }
$GcLogOptions = if ($null -ne $env:GC_LOG_OPTS) {
    $env:GC_LOG_OPTS
} else {
    "-Xlog:gc*,safepoint:file=logs/gc-%t.log:time,uptime,level,tags:filecount=10,filesize=100m"
}
$script:AppPid = $null

Set-Location $AppHome

function Show-Usage {
    Write-Host "Spring Boot Application Launcher"
    Write-Host ""
    Write-Host "Usage: $($MyInvocation.ScriptName) {start|stop|restart|status|log|clean|config}"
    Write-Host "       $($MyInvocation.ScriptName) start [profile] [jvm_options]"
    Write-Host ""
    Write-Host "Commands:"
    Write-Host "  start [profile]    Start application, optional profile (dev, prod, test)"
    Write-Host "  stop               Stop application"
    Write-Host "  restart [profile]  Restart application"
    Write-Host "  status             Show application status"
    Write-Host "  log                Show live log"
    Write-Host "  clean              Clean logs and PID file"
    Write-Host "  config             Show runtime configuration"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\bin\bifro-test-suite.ps1 start"
    Write-Host "  .\bin\bifro-test-suite.cmd start"
    Write-Host "  .\bin\bifro-test-suite.ps1 start prod"
    Write-Host "  .\bin\bifro-test-suite.ps1 start dev `"-Xmx2048m`""
    exit 1
}

function Split-CommandLine {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return @()
    }

    $matches = [regex]::Matches($Value, '("[^"]*"|''[^'']*''|\S+)')
    $args = @()
    foreach ($match in $matches) {
        $item = $match.Value
        if (($item.StartsWith('"') -and $item.EndsWith('"')) -or
            ($item.StartsWith("'") -and $item.EndsWith("'"))) {
            $item = $item.Substring(1, $item.Length - 2)
        }
        $args += $item
    }
    return $args
}

function Quote-Arg {
    param([string]$Value)
    if ($null -eq $Value) {
        return '""'
    }
    if ($Value -notmatch '[\s"]') {
        return $Value
    }
    $escaped = $Value -replace '(\\*)"', '$1$1\"'
    $escaped = $escaped -replace '(\\+)$', '$1$1'
    return '"' + $escaped + '"'
}

function Get-DefaultJvmOptions {
    $totalMemMb = 4096
    try {
        if (Get-Command Get-CimInstance -ErrorAction SilentlyContinue) {
            $totalMemMb = [int]((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1MB)
        } else {
            $totalMemMb = [int]((Get-WmiObject Win32_ComputerSystem).TotalPhysicalMemory / 1MB)
        }
    } catch {
        Write-Host "Warning: failed to read system memory, using default: ${totalMemMb}MB"
    }

    $jvmMemMb = [int]($totalMemMb * 0.75)
    if ($jvmMemMb -lt 512) {
        $jvmMemMb = 512
    }

    return "-Xms${jvmMemMb}m -Xmx${jvmMemMb}m -XX:+UseZGC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs -XX:ConcGCThreads=4"
}

function Initialize-JvmOptions {
    if ([string]::IsNullOrWhiteSpace($env:JVM_OPTS)) {
        $env:JVM_OPTS = Get-DefaultJvmOptions
    }
}

function Test-Environment {
    if (!(Test-Path $ConfigDir)) {
        Write-Host "Error: config directory does not exist: $ConfigDir"
        exit 1
    }
    if (!(Test-Path $LibDir)) {
        Write-Host "Error: library directory does not exist: $LibDir"
        exit 1
    }

    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
    $jarCount = @(Get-ChildItem -Path $LibDir -Filter "*.jar" -File -ErrorAction SilentlyContinue).Count
    if ($jarCount -eq 0) {
        Write-Host "Warning: no JAR files found in library directory: $LibDir"
    }
}

function Build-Classpath {
    $entries = @()
    $classesDir = Join-Path $AppHome "classes"
    if (Test-Path $classesDir) {
        $entries += $classesDir
    }
    $entries += Get-ChildItem -Path $LibDir -Filter "*.jar" -File | Sort-Object Name | ForEach-Object { $_.FullName }
    return ($entries -join ";")
}

function Test-Running {
    if (Test-Path $PidFile) {
        $pidText = (Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
        $pidValue = 0
        if ([int]::TryParse($pidText, [ref]$pidValue)) {
            $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
            if ($null -ne $process) {
                $script:AppPid = $pidValue
                return $true
            }
        }
        Remove-Item -Force $PidFile -ErrorAction SilentlyContinue
    }
    $script:AppPid = $null
    return $false
}

function Test-StartupConfirmed {
    param([int]$ProcessId)
    $pattern = "\s$ProcessId\s.*Started App"
    $files = @($LogFile, (Join-Path $LogDir "info.log"))
    foreach ($file in $files) {
        if (Test-Path $file) {
            $match = Select-String -Path $file -Pattern $pattern -Quiet -ErrorAction SilentlyContinue
            if ($match) {
                return $true
            }
        }
    }
    return $false
}

function Start-App {
    param([string]$ActiveProfile, [string]$JvmOptions)

    Test-Environment
    Initialize-JvmOptions

    if (Test-Running) {
        Write-Host "Application is already running (PID: $script:AppPid)"
        return 1
    }

    Write-Host "Starting $AppName ..."
    $classpath = Build-Classpath
    if ([string]::IsNullOrWhiteSpace($classpath)) {
        Write-Host "Error: failed to build classpath, check lib directory"
        return 1
    }

    $springArgs = @()
    if (![string]::IsNullOrWhiteSpace($ActiveProfile)) {
        $springArgs += "--spring.profiles.active=$ActiveProfile"
        Write-Host "Using profile: $ActiveProfile"
        Write-Host "Will load: conf/application.yml, conf/application-$ActiveProfile.yml"
    } else {
        Write-Host "Using default config: conf/application.yml"
    }
    $springArgs += "--spring.config.location=conf/"

    $finalJvmOptions = "$($env:JVM_OPTS) $GcLogOptions"
    if (![string]::IsNullOrWhiteSpace($JvmOptions)) {
        $finalJvmOptions = "$finalJvmOptions $JvmOptions"
    }

    $javaArgs = @()
    $javaArgs += Split-CommandLine $finalJvmOptions
    $javaArgs += "-cp"
    $javaArgs += $classpath
    $javaArgs += $MainClass
    $javaArgs += $springArgs

    Write-Host "Start command:"
    Write-Host ("  java " + (($javaArgs | ForEach-Object { Quote-Arg $_ }) -join " "))
    Write-Host ""
    Write-Host "Log file: $LogFile"
    Write-Host "PID file:  $PidFile"
    Write-Host ""

    $argumentLine = ($javaArgs | ForEach-Object { Quote-Arg $_ }) -join " "
    $process = Start-Process -FilePath "java" `
        -ArgumentList $argumentLine `
        -WorkingDirectory $AppHome `
        -RedirectStandardOutput $LogFile `
        -RedirectStandardError $StdErrFile `
        -WindowStyle Hidden `
        -PassThru

    Set-Content -Path $PidFile -Value $process.Id -Encoding ASCII

    $elapsed = 0
    while ($elapsed -lt $StartTimeout) {
        Start-Sleep -Seconds 1
        $elapsed++

        if (!(Test-Running)) {
            Write-Host "Start failed: process exited before startup completed"
            Write-Host "View logs for details:"
            Write-Host "    Get-Content -Tail 50 `"$LogFile`""
            if (Test-Path $StdErrFile) {
                Write-Host "    Get-Content -Tail 50 `"$StdErrFile`""
            }
            Remove-Item -Force $PidFile -ErrorAction SilentlyContinue
            return 1
        }

        if (Test-StartupConfirmed $process.Id) {
            Start-Sleep -Seconds $StartStableSeconds
            if (!(Test-Running)) {
                Write-Host "Start failed: process exited immediately after startup"
                Write-Host "View logs for details:"
                Write-Host "    Get-Content -Tail 50 `"$LogFile`""
                Remove-Item -Force $PidFile -ErrorAction SilentlyContinue
                return 1
            }
            Write-Host "Started successfully (PID: $script:AppPid)"
            Write-Host "Use this command to view logs:"
            Write-Host "    Get-Content -Wait `"$LogFile`""
            return 0
        }
    }

    if (Test-Running) {
        Write-Host "Application is running (PID: $script:AppPid), but startup was not confirmed within ${StartTimeout}s"
        Write-Host "Use this command to view logs:"
        Write-Host "    Get-Content -Wait `"$LogFile`""
        return 0
    }

    Write-Host "Start failed"
    Write-Host "View logs for details:"
    Write-Host "    Get-Content -Tail 50 `"$LogFile`""
    Remove-Item -Force $PidFile -ErrorAction SilentlyContinue
    return 1
}

function Wait-ForExit {
    param([int]$Timeout)
    while ($Timeout -gt 0) {
        if (Test-Running) {
            Start-Sleep -Seconds 1
            $Timeout--
            Write-Host -NoNewline "."
        } else {
            return $true
        }
    }
    return $false
}

function Request-JvmExit {
    param([int]$ProcessId)

    $jcmd = Get-Command "jcmd.exe" -ErrorAction SilentlyContinue
    if ($null -eq $jcmd) {
        return $false
    }

    & $jcmd.Source $ProcessId VM.exit 0 | Out-Null
    return ($LASTEXITCODE -eq 0)
}

function Stop-App {
    if (!(Test-Running)) {
        Write-Host "Application is not running"
        return 1
    }

    $targetPid = $script:AppPid
    Write-Host "Stopping $AppName (PID: $targetPid) ..."
    if (!(Request-JvmExit $targetPid)) {
        Write-Host "JVM graceful exit command is unavailable or failed, falling back to taskkill"
        & taskkill.exe /PID $targetPid /T | Out-Null
    }

    if (!(Wait-ForExit $StopTimeout)) {
        Write-Host ""
        Write-Host "Force stopping..."
        & taskkill.exe /PID $targetPid /T /F | Out-Null
        Start-Sleep -Seconds 2
    }

    Remove-Item -Force $PidFile -ErrorAction SilentlyContinue
    Write-Host "Application stopped"
    return 0
}

function Restart-App {
    param([string]$ActiveProfile, [string]$JvmOptions)
    Write-Host "Restarting $AppName ..."
    $stopCode = Stop-App
    Start-Sleep -Seconds 2
    return Start-App $ActiveProfile $JvmOptions
}

function Show-Status {
    if (Test-Running) {
        $process = Get-Process -Id $script:AppPid -ErrorAction SilentlyContinue
        Write-Host "$AppName is running"
        Write-Host "   PID:          $script:AppPid"
        Write-Host ("   Memory usage: {0:N1} MB" -f ($process.WorkingSet64 / 1MB))
        Write-Host "   Log file:     $LogFile"
    } else {
        Write-Host "$AppName is not running"
    }

    Write-Host ""
    Write-Host "Directory info:"
    Write-Host "   Profiles:        $(@(Get-ChildItem -Path $ConfigDir -Filter 'application*.yml' -ErrorAction SilentlyContinue).Count)"
    Write-Host "   Dependency JARs: $(@(Get-ChildItem -Path $LibDir -Filter '*.jar' -ErrorAction SilentlyContinue).Count)"
    if (Test-Path $LogDir) {
        $logSize = ((Get-ChildItem -Path $LogDir -File -Recurse -ErrorAction SilentlyContinue |
            Measure-Object -Property Length -Sum).Sum / 1KB)
        Write-Host ("   Log size:        {0:N1} KB" -f $logSize)
    } else {
        Write-Host "   Log size:        N/A"
    }
}

function Tail-Log {
    if (Test-Path $LogFile) {
        Write-Host "Viewing log: $LogFile"
        Write-Host "Press Ctrl+C to exit"
        Write-Host "----------------------------------------"
        Get-Content -Path $LogFile -Tail 100 -Wait
    } else {
        Write-Host "Log file does not exist: $LogFile"
        Write-Host "Application may have never started"
    }
}

function Clean-App {
    Write-Host "Cleaning..."
    if (Test-Running) {
        Write-Host "Application is running, stopping first..."
        $stopCode = Stop-App
    }

    Remove-Item -Force $PidFile -ErrorAction SilentlyContinue
    if (Test-Path $LogFile) {
        $backup = "$LogFile.$(Get-Date -Format 'yyyyMMdd_HHmmss').bak"
        Move-Item -Force $LogFile $backup
        Write-Host "Backed up log: $backup"
    }
    if (Test-Path $LogDir) {
        Get-ChildItem -Path $LogDir -Filter "*.bak" -File -ErrorAction SilentlyContinue |
            Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-7) } |
            Remove-Item -Force -ErrorAction SilentlyContinue
    }
    Write-Host "Cleanup completed"
}

function Show-Config {
    Initialize-JvmOptions
    Write-Host "=== Application configuration ==="
    Write-Host "Application name: $AppName"
    Write-Host "Main class:       $MainClass"
    Write-Host "Config directory: $ConfigDir"
    Write-Host "Library directory: $LibDir"
    Write-Host "Log directory:    $LogDir"
    Write-Host "JVM options:      $($env:JVM_OPTS)"
    Write-Host "GC log options:   $GcLogOptions"
    Write-Host "Start timeout:    ${StartTimeout}s"
    Write-Host "Start stable:     ${StartStableSeconds}s"
    Write-Host "Stop timeout:     ${StopTimeout}s"
    Write-Host ""
    Write-Host "Profile list:"
    Get-ChildItem -Path $ConfigDir -Filter "application*.yml" -ErrorAction SilentlyContinue |
        ForEach-Object { Write-Host "  $($_.FullName)" }
    Write-Host ""
    Write-Host "Dependency JAR count: $(@(Get-ChildItem -Path $LibDir -Filter '*.jar' -ErrorAction SilentlyContinue).Count)"
}

if ([string]::IsNullOrWhiteSpace($Command)) {
    Show-Usage
}

switch ($Command.ToLowerInvariant()) {
    "start" {
        exit (Start-App $Profile $ExtraJvmOptions)
    }
    "stop" {
        exit (Stop-App)
    }
    "restart" {
        exit (Restart-App $Profile $ExtraJvmOptions)
    }
    "status" {
        Show-Status
        exit 0
    }
    "log" {
        Tail-Log
        exit 0
    }
    { $_ -in @("clean", "cleanup") } {
        Clean-App
        exit 0
    }
    "config" {
        Show-Config
        exit 0
    }
    { $_ -in @("help", "-h", "--help") } {
        Show-Usage
    }
    default {
        Write-Host "Unknown command: $Command"
        Write-Host ""
        Show-Usage
    }
}

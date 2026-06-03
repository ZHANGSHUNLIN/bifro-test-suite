@echo off
rem Licensed to the Apache Software Foundation (ASF) under one or more
rem contributor license agreements. See the NOTICE file distributed with
rem this work for additional information regarding copyright ownership.
rem The ASF licenses this file to You under the Apache License, Version 2.0.

set "SCRIPT_DIR=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%bifro-test-suite.ps1" %*
exit /b %ERRORLEVEL%

@echo off
setlocal enabledelayedexpansion
REM ===========================================================================
REM  Deceased-account block DB - lock / deadlock lab launcher (Windows)
REM
REM  Usage:
REM    lock-lab.bat              ... opens 3 terminals: A, B, OBS
REM    lock-lab.bat A B OBS      ... same as above
REM
REM  What is different from the original KTX launcher:
REM    1) default database is core_bank (this project)
REM    2) each terminal calls lock_lab.iam('<label>') right after it connects.
REM       That stores "connection id -> A/B/OBS" so the OBS terminal can show
REM       which terminal holds or waits for which lock.
REM    3) it checks that 00_setup.sql has been run (lock_lab database exists).
REM
REM  NOTE 1: this file is intentionally ASCII-only.
REM          cmd.exe reads a .bat file with the system codepage, so non-ASCII
REM          text in here gets garbled and executed as commands.
REM  NOTE 2: every window switches itself to UTF-8 (chcp 65001) before mysql
REM          starts, because a new console does not inherit the codepage.
REM ===========================================================================

REM ---- connection settings (edit once to match your machine) ----------------
set "MYSQL_USER=root"
set "MYSQL_PASSWORD=YOUR_PASSWORD"
set "MYSQL_DB=core_bank"
set "MYSQL_HOST=127.0.0.1"
set "MYSQL_PORT=3306"
REM ---------------------------------------------------------------------------

chcp 65001 >nul

set "ERRFILE=%TEMP%\locklab_err.txt"
set "RUNNER=%TEMP%\locklab_session.bat"

REM ---- 1. mysql client on PATH? ---------------------------------------------
where mysql >nul 2>&1
if errorlevel 1 (
  echo [ERROR] "mysql" command not found.
  echo         Add the MySQL "bin" folder to the Path variable.
  echo.
  pause
  exit /b 1
)

REM ---- 2. can we connect? ---------------------------------------------------
mysql -h %MYSQL_HOST% -P %MYSQL_PORT% -u %MYSQL_USER% -p%MYSQL_PASSWORD% --default-character-set=utf8mb4 -e "SELECT 1" >nul 2>"%ERRFILE%"
if errorlevel 1 (
  echo [ERROR] Cannot connect to MySQL. The server said:
  echo.
  type "%ERRFILE%"
  echo.
  echo   "Access denied"  -^> edit MYSQL_PASSWORD at the top of this file.
  echo   "Can't connect"  -^> start the MySQL80 service in services.msc.
  echo.
  pause
  exit /b 1
)

REM ---- 3. project schema and lab tools present? -----------------------------
mysql -h %MYSQL_HOST% -P %MYSQL_PORT% -u %MYSQL_USER% -p%MYSQL_PASSWORD% --default-character-set=utf8mb4 -e "SELECT 1 FROM core_bank.acct WHERE acct_no='10288000000001'" >nul 2>"%ERRFILE%"
if errorlevel 1 (
  echo [ERROR] core_bank or the demo account is missing. The server said:
  echo.
  type "%ERRFILE%"
  echo.
  echo   Run these once, in this order:
  echo     1^) schema.sql
  echo     2^) concurrency\00_setup.sql
  echo.
  pause
  exit /b 1
)
mysql -h %MYSQL_HOST% -P %MYSQL_PORT% -u %MYSQL_USER% -p%MYSQL_PASSWORD% --default-character-set=utf8mb4 -e "CALL lock_lab.reset()" >nul 2>"%ERRFILE%"
if errorlevel 1 (
  echo [ERROR] lock_lab tools are missing. Run concurrency\00_setup.sql first.
  echo.
  type "%ERRFILE%"
  echo.
  pause
  exit /b 1
)

REM ---- 4. per-window helper script -------------------------------------------
REM  %1 is the terminal label (A, B, OBS).
REM  --init-command runs lock_lab.iam('<label>') right after connecting:
REM    - registers this connection id under the label
REM    - sets innodb_lock_wait_timeout to 120 seconds for this session
> "%RUNNER%" echo @echo off
>>"%RUNNER%" echo chcp 65001 ^>nul
>>"%RUNNER%" echo title %%1 terminal
>>"%RUNNER%" echo mysql --prompt="%%1> " --default-character-set=utf8mb4 --init-command="CALL lock_lab.iam('%%1')" -h %MYSQL_HOST% -P %MYSQL_PORT% -u %MYSQL_USER% -p%MYSQL_PASSWORD% %MYSQL_DB%

REM ---- 5. open terminals -----------------------------------------------------
set "TARGETS=%*"
if "%TARGETS%"=="" set "TARGETS=A B OBS"
set /a OPENED=0

for %%L in (%TARGETS%) do (
  set "VALID="
  if /i "%%L"=="A"   set "VALID=1"
  if /i "%%L"=="B"   set "VALID=1"
  if /i "%%L"=="OBS" set "VALID=1"
  if not defined VALID (
    echo [SKIP] Unknown terminal name: %%L   ^(valid: A B OBS^)
  ) else (
    start "%%L" cmd /k "%RUNNER%" %%L
    set /a OPENED+=1
    timeout /t 1 /nobreak >nul
  )
)

del "%ERRFILE%" >nul 2>&1
echo.
echo Opened !OPENED! terminal(s): %TARGETS%
echo Demo account has been reset to 1,000,000.
echo.
echo In the OBS window, check that labels are registered:
echo     SELECT * FROM lock_lab.session_label;
echo.
endlocal

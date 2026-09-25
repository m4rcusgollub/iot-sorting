@echo off
title IoT Sorting - Sistema de Esteira
chcp 65001 > nul
cls

echo ===================================================
echo           Iniciando Sistema IoT Sorting            
echo ===================================================
echo.

set "SCRIPT_DIR=%~dp0"
set "APP_DIR=%SCRIPT_DIR%iot-sorting"

:: 1. Configurar JDK 21 se JAVA_HOME nao estiver setado
if "%JAVA_HOME%"=="" (
    if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    )
)

if not "%JAVA_HOME%"=="" (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERRO] Java 21 nao foi encontrado no sistema.
    echo Configure o JAVA_HOME ou instale o JDK 21.
    pause
    exit /b 1
)

:: 2. Verificar se o JAR ja estiver compilado, inicia imediatamente
if exist "%APP_DIR%\target\iot-sorting-1.0.0.jar" (
    echo [*] Executando aplicacao: JAR compilado...
    echo [*] Banco de dados SQLite local: iot_sorting.db - sem Docker.
    echo [*] Dashboard: http://localhost:8080 - sera aberto quando o servidor iniciar.
    echo [*] Pressione Ctrl+C nesta janela para parar o servidor.
    echo.
    start "" powershell -NoProfile -WindowStyle Hidden -Command "for($i=0;$i -lt 180;$i++){ $up=$false; try { Invoke-WebRequest http://localhost:8080/ -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop | Out-Null; $up=$true } catch { }; if($up){ Start-Process http://localhost:8080; break }; Start-Sleep 1 }"
    java -jar "%APP_DIR%\target\iot-sorting-1.0.0.jar"
    goto fim
)

:: 3. Caso contrario, usa o Maven Wrapper
echo [*] Compilando e executando via Maven Wrapper...
echo [*] Banco de dados SQLite local: iot_sorting.db - sem Docker.
echo [*] Dashboard: http://localhost:8080 - sera aberto quando o servidor iniciar.
echo [*] Pressione Ctrl+C nesta janela para parar o servidor.
echo.
start "" powershell -NoProfile -WindowStyle Hidden -Command "for($i=0;$i -lt 180;$i++){ $up=$false; try { Invoke-WebRequest http://localhost:8080/ -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop | Out-Null; $up=$true } catch { }; if($up){ Start-Process http://localhost:8080; break }; Start-Sleep 1 }"
cd /d "%APP_DIR%"
call mvnw.cmd spring-boot:run

:fim
pause

@echo off
echo 🚀 Selenium Video Recording Test Runner for Windows
echo ====================================================

REM Check prerequisites
echo 🔍 Checking prerequisites...

REM Check Java
java -version >nul 2>&1
if %errorlevel% equ 0 (
    for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        set JAVA_VERSION=%%g
        set JAVA_VERSION=!JAVA_VERSION:"=!
    )
    echo ✅ Java found: !JAVA_VERSION!
) else (
    echo ❌ Java not found. Please install Java 11+:
    echo    winget install Eclipse.Temurin.11.JDK
    pause
    exit /b 1
)

REM Check Maven
mvn -version >nul 2>&1
if %errorlevel% equ 0 (
    for /f "tokens=3" %%g in ('mvn -version 2^>^&1 ^| findstr "Apache Maven"') do (
        set MVN_VERSION=%%g
    )
    echo ✅ Maven found: !MVN_VERSION!
) else (
    echo ❌ Maven not found. Please install Maven:
    echo    winget install Apache.Maven
    pause
    exit /b 1
)

REM Check FFmpeg
ffmpeg -version >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ FFmpeg found
) else (
    echo ❌ FFmpeg not found. Please install FFmpeg:
    echo    winget install Gyan.FFmpeg
    pause
    exit /b 1
)

REM Check Chrome
if exist "C:\Program Files\Google\Chrome\Application\chrome.exe" (
    echo ✅ Google Chrome found
) else if exist "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe" (
    echo ✅ Google Chrome found
) else (
    echo ❌ Google Chrome not found. Please install Chrome:
    echo    winget install Google.Chrome
    pause
    exit /b 1
)

echo ✅ All prerequisites are installed
echo.

REM Create directories
if not exist "videos" mkdir videos
if not exist "logs" mkdir logs
if not exist "frames" mkdir frames

REM Compile and run tests
echo 🔨 Compiling the project...
call mvn clean compile
if %errorlevel% neq 0 (
    echo ❌ Compilation failed
    pause
    exit /b 1
)
echo ✅ Compilation successful

echo.
echo 🧪 Running video recording tests...
call mvn test
if %errorlevel% equ 0 (
    echo.
    echo ✅ Tests completed successfully!
    echo 📹 Check the 'videos' directory for recorded videos
    echo 📋 Check the 'logs' directory for detailed logs
    
    REM Show generated videos
    if exist "videos\*" (
        echo.
        echo 📹 Generated videos:
        dir videos
    )
) else (
    echo ❌ Tests failed. Check the logs for details.
    pause
    exit /b 1
)

pause

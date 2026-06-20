@REM Maven Wrapper Script for Windows
@REM This allows running Maven without having it installed system-wide

@echo off
setlocal

set ERROR_CODE=0

@REM Determine the Java command
if "%JAVA_HOME%" == "" (
    @REM Try to find Java in common locations
    where java >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        set JAVACMD=java
        goto checkWrapper
    )
    
    @REM Check common Java installation paths
    if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot"
        set JAVACMD=%JAVA_HOME%\bin\java.exe
        goto checkWrapper
    )
    
    echo ERROR: JAVA_HOME is not set and no 'java' command could be found.
    echo Java 21 was detected earlier. Please set JAVA_HOME or add Java to your PATH.
    set ERROR_CODE=1
    goto end
) else (
    set JAVACMD=%JAVA_HOME%\bin\java.exe
)

:checkWrapper
@REM Maven Wrapper directory
set MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%
if "%MAVEN_PROJECTBASEDIR%"=="" set MAVEN_PROJECTBASEDIR=%cd%
set WRAPPER_DIR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper
set JAR_PATH=%WRAPPER_DIR%\maven-wrapper.jar

@REM Check if wrapper JAR exists
if not exist "%JAR_PATH%" (
    echo Maven Wrapper JAR not found. Downloading...
    
    @REM Create wrapper directory
    if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
    
    @REM Download using PowerShell
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar' -OutFile '%JAR_PATH%'}"
    
    if %ERRORLEVEL% neq 0 (
        echo ERROR: Failed to download Maven Wrapper.
        set ERROR_CODE=1
        goto end
    )
    
    echo Maven Wrapper downloaded successfully.
)

@REM Execute Maven
"%JAVACMD%" ^
  -classpath "%JAR_PATH%" ^
  "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
  org.apache.maven.wrapper.MavenWrapperMain %*

if %ERRORLEVEL% neq 0 set ERROR_CODE=%ERRORLEVEL%

:end
exit /B %ERROR_CODE%

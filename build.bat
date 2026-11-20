@echo off
setlocal
set "PROJECT_DIR=%~dp0"
set "OUTPUT_DIR=%PROJECT_DIR%.."
set "BUILD_DIR=%PROJECT_DIR%build"

if exist "%BUILD_DIR%\classes" rmdir /s /q "%BUILD_DIR%\classes"
mkdir "%BUILD_DIR%\classes"
dir /s /b "%PROJECT_DIR%src\*.java" > "%BUILD_DIR%\sources.txt"
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "%OUTPUT_DIR%\logisim.jar" -d "%BUILD_DIR%\classes" @"%BUILD_DIR%\sources.txt"
if errorlevel 1 exit /b 1

xcopy /E /I /Y /Q "%PROJECT_DIR%src\resources\*" "%BUILD_DIR%\classes\" >nul
if errorlevel 1 exit /b 1
jar cfm "%OUTPUT_DIR%\logisim-learner.jar" "%PROJECT_DIR%manifest.mf" -C "%BUILD_DIR%\classes" .
if errorlevel 1 exit /b 1
echo Built %OUTPUT_DIR%\logisim-learner.jar

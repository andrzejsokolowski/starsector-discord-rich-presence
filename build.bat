@echo off
echo Building Discord Rich Presence mod...
echo.

REM Prefer the Gradle wrapper if its jar is present
if exist "gradle\wrapper\gradle-wrapper.jar" (
    echo Using Gradle wrapper...
    call gradlew.bat jar
    goto end
)

REM Fall back to system Gradle
where gradle >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo Using system Gradle...
    gradle jar
    goto end
)

echo ERROR: Could not find Gradle.
echo.
echo Options:
echo   1. Open this project in IntelliJ IDEA -- it will download Gradle automatically.
echo   2. Install Gradle from https://gradle.org/install/ and add it to your PATH,
echo      then run this script again.
echo   3. Run: gradle wrapper  (if Gradle is installed) to generate gradlew files,
echo      then run: gradlew.bat jar
echo.
exit /b 1

:end
if %ERRORLEVEL% equ 0 (
    echo.
    echo SUCCESS: jars\discord_presence.jar has been built.
    echo Copy the starsector-discord-presence\ folder into your Starsector mods\ directory.
) else (
    echo.
    echo BUILD FAILED. Check the output above for errors.
)

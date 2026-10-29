@echo off
rem Launches the GUI using the JRE bundled in runtime\win-x64 (no console window stays open).
set "ROOT=%~dp0"
if exist "%ROOT%runtime\win-x64\bin\javaw.exe" (
  start "" "%ROOT%runtime\win-x64\bin\javaw.exe" -jar "%ROOT%lib\ApkExtractor.jar"
) else (
  start "" javaw -jar "%ROOT%lib\ApkExtractor.jar"
)

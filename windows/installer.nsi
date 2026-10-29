; NSIS installer for AAB to APK Extractor (per-user install, no admin rights needed).
; Build: makensis -DVERSION=1.0.0 -DSTAGE=<staged folder> -DOUTFILE=<setup.exe> -DICON=<icon.ico> installer.nsi
Unicode true
!include "MUI2.nsh"

!define APPNAME "AAB to APK Extractor"
!define UNINSTKEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\AABtoAPKExtractor"

Name "${APPNAME}"
OutFile "${OUTFILE}"
RequestExecutionLevel user
InstallDir "$LOCALAPPDATA\Programs\${APPNAME}"
SetCompressor /SOLID lzma
BrandingText "${APPNAME} ${VERSION}"

!define MUI_ICON "${ICON}"
!define MUI_UNICON "${ICON}"
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_FUNCTION LaunchApp
!define MUI_FINISHPAGE_RUN_TEXT "Launch ${APPNAME}"

Function LaunchApp
  SetOutPath "$INSTDIR"
  Exec '"$INSTDIR\runtime\win-x64\bin\javaw.exe" -jar "$INSTDIR\lib\ApkExtractor.jar"'
FunctionEnd

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "${STAGE}/LICENSE"
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "${APPNAME}"
VIAddVersionKey "FileDescription" "${APPNAME} installer"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "LegalCopyright" "MIT License"

Section "Install"
  SetOutPath "$INSTDIR"
  ; remove a previous install first so stale files never linger
  RMDir /r "$INSTDIR\lib"
  RMDir /r "$INSTDIR\runtime"
  File "${STAGE}/icon.ico"
  File "${STAGE}/LICENSE"
  File "${STAGE}/THIRD_PARTY_NOTICES.md"
  File /r "${STAGE}/lib"
  File /r "${STAGE}/runtime"

  SetOutPath "$INSTDIR"
  CreateShortCut "$SMPROGRAMS\${APPNAME}.lnk" "$INSTDIR\runtime\win-x64\bin\javaw.exe" '-jar "$INSTDIR\lib\ApkExtractor.jar"' "$INSTDIR\icon.ico" 0
  CreateShortCut "$DESKTOP\${APPNAME}.lnk" "$INSTDIR\runtime\win-x64\bin\javaw.exe" '-jar "$INSTDIR\lib\ApkExtractor.jar"' "$INSTDIR\icon.ico" 0

  WriteUninstaller "$INSTDIR\Uninstall.exe"
  WriteRegStr HKCU "${UNINSTKEY}" "DisplayName" "${APPNAME}"
  WriteRegStr HKCU "${UNINSTKEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${UNINSTKEY}" "Publisher" "Game District"
  WriteRegStr HKCU "${UNINSTKEY}" "DisplayIcon" "$INSTDIR\icon.ico"
  WriteRegStr HKCU "${UNINSTKEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNINSTKEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegDWORD HKCU "${UNINSTKEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINSTKEY}" "NoRepair" 1
SectionEnd

Section "Uninstall"
  Delete "$SMPROGRAMS\${APPNAME}.lnk"
  Delete "$DESKTOP\${APPNAME}.lnk"
  RMDir /r "$INSTDIR\lib"
  RMDir /r "$INSTDIR\runtime"
  Delete "$INSTDIR\icon.ico"
  Delete "$INSTDIR\LICENSE"
  Delete "$INSTDIR\THIRD_PARTY_NOTICES.md"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
  DeleteRegKey HKCU "${UNINSTKEY}"
SectionEnd

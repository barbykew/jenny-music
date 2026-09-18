; Jenny Music — single-file Windows installer.
;
; Wraps the Compose Desktop app image (desktopApp/build/compose/binaries/main/app/Jenny Music)
; into one Setup .exe. Built with Inno Setup rather than jpackage's own .msi/.exe targets, because
; those need the WiX toolset, and because the ProGuard-ed "release" packaging runs out of memory on
; this machine; the app image comes from `:desktopApp:createDistributable` instead.
;
; Build:   ISCC.exe installer\jenny-music.iss      (after :desktopApp:createDistributable)

#define AppName "Jenny Music"
#define AppVersion "2.1.0"
#define AppExe "Jenny Music.exe"
#define AppImage "..\desktopApp\build\compose\binaries\main\app\Jenny Music"

[Setup]
AppId={{6C3F1B8E-4A2D-4E7B-9C51-7A2E9D0F3B11}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=barbykew
AppPublisherURL=https://github.com/barbykew/jenny-music
; Per-user install: no admin prompt, and the app can write next to itself without elevation.
PrivilegesRequired=lowest
DefaultDirName={localappdata}\Programs\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
SetupIconFile=..\composeApp\icon\jenny_app_icon.ico
UninstallDisplayIcon={app}\{#AppExe}
OutputDir=..\build\installer
OutputBaseFilename=JennyMusic-Setup
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Tasks]
Name: "desktopicon"; Description: "Put Jenny Music on the desktop"; GroupDescription: "Shortcuts:"

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExe}"
Name: "{group}\Uninstall {#AppName}"; Filename: "{uninstallexe}"
Name: "{userdesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "Open Jenny Music"; Flags: nowait postinstall skipifsilent

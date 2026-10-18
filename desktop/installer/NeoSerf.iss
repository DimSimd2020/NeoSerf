; NeoSerf Windows installer (Inno Setup 6). Build with desktop/installer/build.ps1 after desktop/build.ps1.
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#define SourceDir "..\dist\NeoSerf"

[Setup]
AppId={{2031442D-6A1E-4294-8DD0-C3A1790B55E8}
AppName=NeoSerf
AppVersion={#AppVersion}
AppVerName=NeoSerf {#AppVersion}
AppPublisher=NeoSerf
AppPublisherURL=https://github.com/DimSimd2020/NeoSerf
DefaultDirName={autopf}\NeoSerf
DefaultGroupName=NeoSerf
DisableProgramGroupPage=yes
; Per-user install by default, so no administrator prompt; the dialog still allows installing for everyone.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir=..\dist\installer
OutputBaseFilename=NeoSerf-Setup-{#AppVersion}-x64
SetupIconFile=..\assets\neoserf.ico
UninstallDisplayIcon={app}\bin\NeoSerf.exe
UninstallDisplayName=NeoSerf
WizardStyle=modern
Compression=lzma2/ultra64
SolidCompression=yes
CloseApplications=yes

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\NeoSerf"; Filename: "{app}\bin\NeoSerf.exe"
Name: "{autodesktop}\NeoSerf"; Filename: "{app}\bin\NeoSerf.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\bin\NeoSerf.exe"; Description: "{cm:LaunchProgram,NeoSerf}"; Flags: nowait postinstall skipifsilent

# AAB to APK Extractor 1.0.0

First release. A small desktop app that turns an Android App Bundle (`.aab`) into an installable, signed **universal APK** — no command line, nothing to install (Java and bundletool are bundled).

## Downloads

| Your computer | Download |
|---|---|
| Mac with Apple chip (M1/M2/M3/M4) | `AABtoAPK-1.0.0-macOS-AppleSilicon.dmg` |
| Mac with Intel chip | `AABtoAPK-1.0.0-macOS-Intel.dmg` |
| Windows 10/11 (64-bit) | `AABtoAPK-1.0.0-Windows-x64-Setup.exe` |

`SHA256SUMS-1.0.0.txt` lists checksums for all three files.

## Highlights

- **Load an AAB and a keystore** with the native Browse dialog (Finder panel on macOS, Explorer dialog on Windows) or by **drag and drop** — drop an `.aab` and a keystore anywhere on the window, even both at once.
- **Alias picker:** enter the keystore password and the app lists the key aliases in the keystore (JKS and PKCS12 supported). Optional separate key password.
- **One-click Convert:** writes `<name>.apk` next to the selected AAB, signed with your key. A progress bar and log show what bundletool is doing, and **Show in folder** reveals the result.
- **Dark mode by default**, with a Light/Dark switch in the top-right corner (your choice is remembered).
- **Remembers** your last keystore and the alias you used per keystore. Passwords are never stored.
- **Self-contained:** bundles a Java 17 runtime and [bundletool](https://github.com/google/bundletool) 1.18.3. Works offline.

## Installing

**macOS** — open the `.dmg`, drag *AAB to APK Extractor* to *Applications*.
The app is only ad-hoc signed (not notarized), so on first launch macOS may say it is damaged or from an unidentified developer. Either right-click the app → **Open**, or run:

```bash
xattr -dr com.apple.quarantine "/Applications/AAB to APK Extractor.app"
```

macOS may also ask to allow access to Documents/Downloads/Desktop when you browse them — choose **Allow**.

**Windows** — run `AABtoAPK-1.0.0-Windows-x64-Setup.exe`. It installs for the current user (no administrator rights needed), adds Start-menu and Desktop shortcuts, and can be removed from *Settings → Apps*. The installer is not code-signed, so SmartScreen may warn: choose **More info → Run anyway**.

## Known limitations

- Produces a **universal APK** only (one APK for all devices, so it is larger than a device-specific split). Device-specific/split APK output is not included.
- Apps are not notarized (macOS) or code-signed (Windows), see above.
- Apple Silicon and Intel macOS builds are separate downloads; Windows is 64-bit only.

## Build from source

See the [README](../README.md): `scripts/package.sh 1.0.0` builds all three packages.

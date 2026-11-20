# PsiBoard - a light, fast, fully customizable Android keyboard

English and Persian, clipboard manager, emoji panel, suggestions and about 145 settings.
**[Download the latest APK](https://github.com/asandel260-ux/PsiBoard/releases/latest)** (about 0.4 MB, Android 5.0+).
No internet permission, no ads, no third-party libraries (no AndroidX, no Compose).

## Install
1. Download `PsiBoard.apk` from the latest release and open it (allow installs from your browser if Android asks).
2. Open PsiBoard, tap "Enable keyboard", then "Switch to PsiBoard".

If Android refuses to install over an older build, uninstall the old one first: CI builds are signed with a throw-away key.

## Design notes
- **Colour**: palettes are built in OKLCH (perceptually even steps) with Material 3 style tonal elevation: in dark mode, higher surfaces are lighter.
  Key labels keep at least 9:1 contrast (WCAG AAA asks for 7:1) and hints 4.5:1, enforced in code for any theme or seed colour.
  Optional Material You wallpaper colours (Android 12+) and true-black AMOLED. Every colour can still be overridden by hand.
- **Touch**: 48 dp row pitch (Material's minimum target), hit areas fill the gaps between keys, the tap point is corrected upward a little
  (fingers land below where people aim), corner radius is capped relative to key size.
- **Persian**: ISIRI-style 12/11/11 rows, half-space (ZWNJ) key, long-press ز for ژ, Persian digits, Vazirmatn key-cap font.
- **Steady size**: the keyboard keeps the same height on the letters and symbols pages, so the app above it does not jump.

## Build
GitHub Actions builds every push to `main` (`.github/workflows/build.yml`): fetches the font and word lists, runs `gradle assembleRelease`
and publishes a release. Locally: JDK 17, Android SDK 34, Gradle 8.9, then `gradle assembleRelease`.
`screenshots.yml` runs the keyboard in an emulator and saves screenshots of every theme, language and panel.

## Where things are
- Settings.kt        every setting in ONE list; UI, defaults, import/export are generated from it
- Palette.kt         OKLCH colour engine and contrast rules
- Layouts.kt         layout text format (editable in Settings -> Languages & layouts)
- KeyboardView.kt    Theme snapshot, Canvas rendering, multi-touch, popups, swipe gestures
- ClipboardStore.kt / Panels.kt   history, pins, snippets, filters, emoji
- Suggester.kt       binary-search dictionary + learned words + bigrams (assets/dict_*.txt)
- PsiBoardService.kt  the IME itself

## Credits
- Vazirmatn font by Saber Rastikerdar, SIL Open Font License 1.1 (downloaded at build time).
- English and Persian word lists are derived from FrequencyWords by Hermit Dave (built from OpenSubtitles), CC BY-SA 4.0,
  downloaded at build time. A small built-in list is the fallback.

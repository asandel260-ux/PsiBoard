# PsiBoard — a light, fast, fully customizable Android keyboard

Zero dependencies (no AndroidX/Compose), one Canvas-drawn keyboard view, SQLite clipboard.

## Build
Open in Android Studio (Koala+) and run, or: `gradle wrapper && ./gradlew assembleDebug`.
Then: open the app -> "Enable keyboard" -> "Switch to PsiBoard".

## Where things are
- Settings.kt        every setting (~150) in ONE list; UI, defaults, import/export are generated from it
- Layouts.kt         layout text format (editable in Settings -> Languages & layouts)
- KeyboardView.kt    Theme snapshot, Canvas rendering, multi-touch, popups, swipe gestures
- ClipboardStore.kt / Panels.kt   history, pins, snippets, filters, emoji
- Suggester.kt       binary-search dictionary + learned words + bigrams (assets/dict_*.txt; replace with bigger lists)
- PsiBoardService.kt  the IME itself

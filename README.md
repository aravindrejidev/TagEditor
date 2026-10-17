# Tag Editor (Android)

Native Android audio tag editor with a dark neumorphic ("soft UI") interface.
Kotlin, Jetpack Compose, Material 3, jaudiotagger. Builds on GitHub Actions - no PC needed.

## Features

* **Formats:** MP3 (ID3v1 / ID3v2), FLAC (Vorbis comments), M4A / MP4.
* **Single file editor:** shows everything stored in the song, pre-filled and editable: location,
  title, artist, album, album artist, year, genre (with a picker), track, disc, BPM, key,
  language, composer, lyricist, encoder, comment and lyrics. Track and disc accept `3` or `3/12`.
  Optional *Rename file as title* when saving.
* **Album art manager:** shows the embedded cover, saves it as an image file, replaces it with
  a picture from the gallery (optionally resized) or removes it.
* **Batch editing:** pick files or a whole album folder and write album, album artist, artist,
  year, genre, disc, composer, comment and cover to all of them. The current values are shown
  (or what differs between the files) and can be edited. Optional automatic track numbering.
  Only edited / ticked fields are written. Selecting a single song opens the full editor.
* **Tag to file name:** templates such as `%track% - %artist% - %title%`
  (placeholders: `%track% %title% %artist% %album% %albumartist% %year% %genre% %disc% %composer% %filename%`).
* **Lossless saving:** only tag blocks are rewritten. The audio stream is never decoded or
  re-encoded, and every save is verified before it replaces your file.

## How saving stays safe

No storage permission is used. Files come from the Storage Access Framework
(`ACTION_OPEN_DOCUMENT` / `ACTION_OPEN_DOCUMENT_TREE`), which works with Android 10+ scoped storage.

1. The document is copied to the app's private cache (jaudiotagger needs a real file).
2. Tags are written to a working copy.
3. The working copy is read again and its audio stream properties (sample rate, channels,
   duration) must match the original, otherwise nothing is written.
4. Only then the original document is overwritten. If that fails half way, the untouched
   original bytes are copied back automatically.

## Build with GitHub Actions

1. Put the contents of this folder in the root of a GitHub repository (branch `main`).
2. Open the **Actions** tab. The workflow *Build debug APK* runs on every push. You can also
   start it manually: *Actions > Build debug APK > Run workflow*.
3. When the run is green, open it and download **TagEditor-debug-apk** under *Artifacts*.
   Unzip it and install `app-debug.apk`.

The workflow installs JDK 17 and Gradle 8.9 itself, so the Gradle wrapper is not needed.
To create a wrapper for local builds: `gradle wrapper --gradle-version 8.9`.

### Getting the files onto GitHub from a phone

* **Termux:** `pkg install git unzip`, then `unzip TagEditor-Android.zip -d TagEditor`,
  `cd TagEditor`, `git init`, `git add .`, `git commit -m "Initial commit"`,
  `git branch -M main`, `git remote add origin https://github.com/<user>/<repo>.git`,
  `git push -u origin main` (use a personal access token as the password).
* **No Git at all:** upload the ZIP itself to the repository root and add the helper
  workflow `build-from-zip.yml` (delivered next to the ZIP) as
  `.github/workflows/build-from-zip.yml`. Then run *Build APK from ZIP* from the Actions tab.

## Project layout

```
.github/workflows/build.yml          debug APK build
app/build.gradle.kts                 dependencies, Compose, desugaring
app/src/main/AndroidManifest.xml
app/src/main/java/com/app/tageditor/
  MainActivity.kt
  data/        AudioFileParser (jaudiotagger), TagEditorRepository (SAF), models, templates
  viewmodel/   TagEditorViewModel, UiState
  ui/theme/    Color, Type, Shape, Theme, NeumorphicModifiers, Icons
  ui/components/  neumorphic buttons, fields, switches, sliders, dialogs, file pickers
  ui/screens/  HomeScreen, EditorScreen, BatchScreen, RenameScreen, CoverArtPicker
```

## Notes

* Android 11+ does not let apps pick the root of internal storage or the `Download` folder
  itself. Choose the album sub-folder (for example `Music/My Album`) with *Open folder*.
* Renaming uses `DocumentsContract.renameDocument`; some cloud storage providers do not
  support it and will report an error for those files.
* Soft shadows use `Paint.setShadowLayer` (hardware accelerated from Android 9). Older
  devices get a simplified shadow.
* The `release` build type has R8 enabled with rules for jaudiotagger, but it is unsigned.
  Add your own signing config before publishing.

## Third-party software

* [jaudiotagger](https://www.jthink.net/jaudiotagger/) - LGPL 2.1
* AndroidX, Jetpack Compose, Material 3, Kotlin coroutines - Apache 2.0

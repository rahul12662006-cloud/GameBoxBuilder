# GameBox Builder 🎮

Phase 2 build for a template-based no-code Android game builder.

## What is included

- Android app structure using Kotlin + Jetpack Compose
- Home screen with `Make 2D Game` and `Make 3D Game`
- Template picker
- No-code project editor
- Fixed safe options only: character, map, obstacle pack, controls, camera, speed, difficulty, coins, powerups, UI theme
- Internal save/load system
- `.gamebox` export using JSON config
- Phase 2 playable 2D Endless Runner playtest
- Real jump, slide, moving obstacles, coin collection, score, pause, restart and game-over loop
- Speed/difficulty settings now affect the 2D runner gameplay
- Other templates still use safe preview mode
- GitHub Actions workflow to build a debug APK without Android Studio

## Current templates

### 2D
- 2D Endless Runner
- 2D Platformer foundation

### 3D
- 3D Endless Runner preview foundation

## Recommended next phases

### Phase 3
Add obstacle pack tuning, basic level/map editor and UI editor.

### Phase 4
Add the grid/map editor and UI editor.

### Phase 4
Add 3D Endless Runner gameplay.

### Phase 5
Add GameBox Player and `.gamebox` import/run mode.

### Phase 6
Add separate APK export through GitHub Actions/cloud build.

## Build APK without Android Studio

1. Create a new GitHub repository.
2. Upload all files from this folder.
3. Open the repository on GitHub.
4. Go to **Actions**.
5. Open **Build GameBox Builder APK**.
6. Click **Run workflow**.
7. When build completes, open the workflow run and download **GameBoxBuilder-debug-apk** from artifacts.

## Important note

This is Phase 2. The 2D Endless Runner template is now playable, while the project is still intentionally template-based so users cannot create code errors.

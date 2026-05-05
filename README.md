# GameBox Builder

No-code Android game builder foundation.

## Phase 3 build

This version includes:

- Make 2D Game / Make 3D Game flow
- Template picker
- No-code editor
- Save/load `.gamebox` project configuration
- Export `.gamebox` file
- GitHub Actions APK workflow
- Real playable **2D Endless Runner** playtest
- Hitbox and obstacle spacing fix
- Editor rules connected to runner gameplay:
  - Speed affects world movement
  - Difficulty affects obstacle gap/frequency
  - Obstacle pack affects obstacle type
  - Coins toggle works
  - Powerups toggle works
  - Shield, Magnet and 2x Coin powerups
  - Best score saving per project

## Build APK

Push this project to GitHub and run the workflow:

`.github/workflows/android-debug-apk.yml`

The APK is uploaded as a workflow artifact.

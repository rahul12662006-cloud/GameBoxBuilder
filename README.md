# GameBox Builder

No-code Android game builder foundation.

## Phase 4 build

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


## Phase 4

Added a real playable 2D Platformer template with left/right movement, jump physics, platforms, pits, spikes, enemies, checkpoint, coins, lives, finish flag, level complete screen and best score saving. The Preview button now opens the real platformer playtest for the 2D Platformer template.


## Phase 4.1 Platformer Control Hotfix
- Fixed platform landing so the player no longer falls through platform tops.
- Replaced tap-nudge movement with hold-to-move left/right controls.
- Added forgiving platform edge detection, safer pit detection, and lives clamped to 0.
- Improved ground drawing so pits show actual gaps.


## Phase 4.2 Platformer Gameplay Upgrade
- Rebuilt platformer movement with acceleration, friction, smoother jump physics, jump buffering and coyote time.
- Fixed platform collision more deeply: player stays attached to platform tops while moving and no longer falls through supported surfaces.
- Added optional double-jump when powerups are enabled.
- Expanded the platformer level length and improved platform/coin/checkpoint/flag spacing.
- Simplified game-over restart flow and improved lives handling.
- Version updated to 0.4.2.


## Phase 4.3
- Fixed platformer pit gaps so the player cannot run across gaps without jumping.
- Wider pit layout and safer ground collision lock while falling into pits.
- Version 0.4.3.

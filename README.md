# BrawlBrain RT

Real-time on-device vision assistant for Brawl Stars on Android.

The app captures the display with MediaProjection, runs community ONNX vision models locally, tracks the player and enemies, detects walls/cover, and renders a stable HUD with a tactical recommendation.

The project is intentionally a coach/assistant rather than an input injector: it observes the match and gives visual guidance without taking control of the game.

Vision models used by the build:

- PylaEntityDetectorV2 — YOLOv11, enemy / teammate / player
- PylaWallDetectorV2 — YOLOv11, wall / bush / close_bush

The model list and model files are documented in AngelFireLA/BrawlStarsBotMaking. The workflow downloads the current upstream files during CI instead of checking the weights into this repository.

Xiaomi 12X target profile:

- ARM64 only
- Snapdragon 870 / Adreno 650
- screen capture kept at a reduced working resolution
- entity detection about every 125 ms
- wall detection about every 600 ms
- NNAPI is attempted first, with CPU fallback

Build:

1. Open GitHub Actions.
2. Run Build BrawlBrainRT APK.
3. Download the BrawlBrainRT-debug-apk artifact.
4. Install the APK on the phone.

This is an experimental vision assistant. It does not guarantee trophy gains or match outcomes.

# Brawl Dodge AI

Ready-to-run Android vision assistant targeted at Xiaomi 12X / Snapdragon 870.

## Included

- MediaProjection capture.
- Foreground service for Android 13/14/15 style background execution.
- Overlay appears immediately after launch.
- Newest-frame processing.
- Lightweight real-time vision for player/enemy/projectile/area candidates.
- Threat score and directional danger indicator.
- Thermal-aware processing rate.
- ARM64 Android build.
- No root and no touch injection.

## Start

1. Install the APK.
2. Allow "Display over other apps".
3. Press "2. Запустить Brawl Dodge AI".
4. Accept the Android screen-capture dialog.
5. Open Brawl Stars and test in Training Cave or Friendly Battle.

The overlay is intentionally visible before the first CV result, so a permission or service failure cannot look like a blank transparent screen.

## Xiaomi 12X

For long tests, remove battery restrictions for the app and allow autostart/background activity in HyperOS.

## Scope

The built-in detector is a lightweight color/shape vision baseline. It is intentionally self-contained and does not depend on external model downloads, so startup and APK installation are reliable. It is not a perfect universal Brawl Stars classifier.

# Brawl Dodge AI

Milestone 1 implements the real-time screen-capture foundation for the Xiaomi 12X target.

## Scope of M1

- MediaProjection screen capture.
- Newest-frame-only processing with an ImageReader queue depth of two.
- Working resolution around 640x288, preserving the 20:9 landscape aspect ratio.
- Native C++ color/shape detector through one JNI call per processed frame.
- Stable full-screen debug overlay.
- Thermal-safe processing rate based on Android thermal status.
- No AccessibilityService and no touch injection in M1.

## M1 detector

The native detector is intentionally lightweight. It produces candidates for:

- player marker (cyan/green saturated blob)
- enemy / enemy health-bar candidate (red)
- projectile candidate (small red blob)
- area indicator candidate (orange compact blob)

This is a bootstrap detector, not a brawler-specific universal classifier. It must be calibrated and benchmarked against real Training Cave recordings before later milestones depend on it.

## Build

GitHub Actions installs Android SDK, NDK and CMake, then builds the debug APK.

Local build requires JDK 17, Android SDK 35, NDK 27.2.12479018, CMake 3.22.1 and Gradle 8.9.

## Testing

Use the Training Cave or Friendly Battle only. After granting overlay and screen-capture permissions, start M1 and verify that the debug boxes follow the player and visible red/orange threats.

M1 is observation-only. Touch injection is deliberately absent.

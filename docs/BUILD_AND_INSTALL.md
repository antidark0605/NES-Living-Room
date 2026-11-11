# Build and install on MiTV-MSSP3

The emulator APK is built for `armeabi-v7a` (Android 9/API 28 compatible). The native FCEUmm core is compiled from pinned GPLv2 source in GitHub Actions. This does not prove compatibility until verified on hardware.

## Download APK

1. Open the [Android build workflow](https://github.com/antidark0605/NES-Living-Room/actions/workflows/android.yml).
2. Open the most recent **successful** run for `feat/android-tv-mvp`.
3. Scroll to **Artifacts**, download `nes-living-room-armv7-debug`, unzip and locate `app-debug.apk`.
4. On your computer with ADB connected to the TV:

```sh
adb install -r app-debug.apk
adb shell monkey -p dev.antidark.neslivingroom 1
```

## First tests

- Use the TV remote's D-pad / OK / Back to navigate the home screen, controller setup and tester.
- The ROM picker is Android's system document picker. If it cannot be operated with the TV remote, that is a separate compatibility issue to address.
- To test NES gameplay, copy your legally obtained `.nes` file to TV storage and select it. Then choose **Play selected ROM**.
- If the game fails, collect `adb logcat -v time` with the reproduce steps. The app is still an experimental build.

## Native modules

Expected APK library payload:
- `lib/armeabi-v7a/libnesbridge.so`: JNI Libretro host
- `lib/armeabi-v7a/libretro.so`: pinned FCEUmm NES engine

An APK missing either library is not a playable build and must not be shared as one.

The source, pinned core revision and GPLv2 notice are documented in [THIRD_PARTY.md](THIRD_PARTY.md).

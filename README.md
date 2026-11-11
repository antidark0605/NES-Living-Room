# NES Living Room

A lightweight, remote-first NES application for Android TV.

> **Status: playable-core experimental APK.** The CI pipeline builds a GPLv2 FCEUmm native core for ARMv7 and packages it with our libretro bridge. The result has not been validated on MiTV-MSSP3 hardware; ROM/video/audio compatibility is not guaranteed.

## Target hardware

- Xiaomi MiTV-MSSP3, Android 9 (API 28)
- `armeabi-v7a` / `armeabi`; **not** `arm64-v8a`
- TV remote: D-pad / OK / Back throughout menus
- Optional Android-compatible Bluetooth or USB gamepad
- Note: USB devices using the Xbox 360 `045e:028e` protocol may not work on a kernel without `CONFIG_JOYSTICK_XPAD`. App-level mapping cannot fix missing kernel drivers.

## MVP features

- Android TV launcher entry (`LEANBACK_LAUNCHER`)
- No touch screen required
- System document picker for `.nes` files, iNES/NES 2.0 header validation and persistent URI access when granted
- Configurable physical keys for NES Up, Down, Left, Right, A, B, Start, Select
- Separate Turbo A / Turbo B bindings (default **X / Y**); adjustable turbo rate (10 / 15 / 20 presses per second)
- On-device input tester with a 60 Hz status update
- Per-device-independent profile for initial iteration; multi-device profiles are planned
- No ROMs or copyrighted game assets bundled

## Build

Install JDK 17 and Android SDK Platform 35. From the repository root (after placing the ARMv7 `libretro.so` in `app/src/main/jniLibs/armeabi-v7a/` and installing Android NDK 27.2 / CMake 3.22.1):

```sh
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The Gradle wrapper is not committed in this first scaffold. Use a locally installed Gradle 8.7 or add a standard verified wrapper before CI automation. Android Gradle Plugin 8.5.2 requires Gradle 8.7.

## TV navigation

- Use remote D-pad to select buttons, OK to activate, Back to return.
- Select **Choose NES ROM**, then **Play selected ROM** to launch the FCEUmm core.
- Select **Controller settings** and choose a NES action, then press its physical gamepad button to bind it.
- Select **Input tester** to see effective NES controls including turbo pulses.
- In a game, Back opens a simple pause dialog. Resume currently restarts the ROM from the beginning; save states are not implemented.

## Input model

NES input has only 8 standard actions: D-pad x4, A, B, Start, Select. Turbo A and Turbo B are **additional physical bindings**, not extra NES buttons. Default mapping: A → NES A, B → NES B, X → Turbo A, Y → Turbo B. Previously saved custom bindings remain unchanged. When Turbo A is held, A alternates pressed/released at the selected rate. Ordinary A remains a held input. No Xbox-specific key layout is assumed.

Supported initially: Android KeyEvent digital buttons and D-pad. Controllers reporting only analog axes or unsupported HID/USB protocols are not yet supported.

## Build a playable APK

CI installs Android SDK + NDK, fetches pinned [FCEUmm](https://github.com/libretro/libretro-fceumm) revision `e0f3086a8104f8fbbe49a2615b10401504a9533f`, compiles `libretro.so` for `armeabi-v7a`, and assembles the Android APK with `nesbridge.so`.

1. Open [GitHub Actions](https://github.com/antidark0605/NES-Living-Room/actions) and select a successful **Android build** from this branch.
2. Download the `nes-living-room-debug` artifact and unzip it.
3. Install `app-debug.apk` on Android TV using `adb install -r app-debug.apk`.
4. Navigate with the TV remote's D-pad / OK / Back, select a `.nes` file, then select **Play selected ROM**.

CI contains a pinned source checkout so builds can be reproduced. The core source, its GPLv2 license and our JNI integration source must be provided when distributing a combined APK. See [third-party notice](docs/THIRD_PARTY.md).

## Known limitations

- Only `.nes` ROM files supported by FCEUmm are in scope; `.fds`/`.unf` not yet wired to ROM selection.
- No save states, save-RAM persistence, controller profile per physical device, or analog axis support.
- The pause dialog's Resume action reloads the game rather than restoring running state.
- Audio/video synchronization and 32-bit native core behavior need device testing.
- The Android Storage Access Framework picker itself depends on a TV-accessible document provider.

## Legal

Supply your own legally obtained ROMs. This project does not include games, BIOS files, or game assets.

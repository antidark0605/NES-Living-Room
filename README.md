# NES Living Room

A lightweight, remote-first NES application for Android TV.

> **Status: Android TV frontend and input-test MVP.** The NES emulation engine is **not integrated yet**. This version cannot execute ROMs. The ROM picker validates access only; it is not a playable emulator.

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

Install JDK 17 and Android SDK Platform 35. From the repository root:

```sh
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The Gradle wrapper is not committed in this first scaffold. Use a locally installed Gradle 8.7 or add a standard verified wrapper before CI automation. Android Gradle Plugin 8.5.2 requires Gradle 8.7.

## TV navigation

- Use remote D-pad to select buttons, OK to activate, Back to return.
- Select **Choose NES ROM** to test document access; selection is stored but playback is not implemented.
- Select **Controller settings** and choose a NES action, then press its physical gamepad button to bind it.
- Select **Input tester** to see effective NES controls including turbo pulses.
- In a game (future core integration), Back must open a pause menu and never be consumed by the NES controller.

## Input model

NES input has only 8 standard actions: D-pad x4, A, B, Start, Select. Turbo A and Turbo B are **additional physical bindings**, not extra NES buttons. Default mapping: A → NES A, B → NES B, X → Turbo A, Y → Turbo B. Previously saved custom bindings remain unchanged. When Turbo A is held, A alternates pressed/released at the selected rate. Ordinary A remains a held input. No Xbox-specific key layout is assumed.

Supported initially: Android KeyEvent digital buttons and D-pad. Controllers reporting only analog axes or unsupported HID/USB protocols are not yet supported.

## Roadmap

1. TV UI, ROM picker, header checks, key-binding UI and tester (this PR)
2. Integrate a licensed NES emulation core with armeabi-v7a binary, rendering, audio and ROM loading
3. Pause/resume, save states, in-game controller configuration
4. Controller-specific profiles, analog axis handling, automated hardware validation

Choose an emulator core only after reviewing source license and 32-bit ABI support. Never copy third-party emulator code without preserving notices and license requirements.

## Planned core\n\nFCEUmm (Libretro) is the leading candidate. It is GPLv2 licensed, so we must review source distribution and license obligations before shipping a combined application. The core has not yet been integrated or tested on ARMv7. Track work in [Issue #2](https://github.com/antidark0605/NES-Living-Room/issues/2).\n\n## Legal

Supply your own legally obtained ROMs. This project does not include games, BIOS files, or game assets.

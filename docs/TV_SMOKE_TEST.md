# MiTV-MSSP3 smoke test (Android 9, API 28, ARMv7)

**Build state must first be green in [Actions](https://github.com/antidark0605/NES-Living-Room/actions).** The native core build is new and has not yet been tested on the television.

## 1. Remote-only UI test (no gamepad required)

1. Unzip the successful **nes-living-room-armv7-debug** workflow artifact, if present.
2. `adb install -r app-debug.apk`.
3. Launch **NES Living Room** from Android TV apps, not a touch-only launcher.
4. Use only remote D-pad to focus **Choose NES ROM**, **Play selected ROM**, **Controller settings** and **Input tester**.
5. Confirm remote OK activates the focused item and remote Back navigates to the previous screen.
6. Confirm setting screen scrolls from top to bottom with remote D-pad only.
7. In **Input tester**, D-pad still controls UI focus and is not swallowed as NES input.
8. Open **Choose NES ROM**: Android's document picker must also be navigable with D-pad/OK/Back; this depends on the installed document provider.

## 2. ROM boot smoke test

1. Copy a legally obtained `.nes` ROM to TV storage using `adb push`.
2. Select ROM through system picker, verify selected title persists after restarting the app.
3. Select **Play selected ROM**. Expect game video in a 4:3 view with black sidebars and audible game audio.
4. Use remote D-pad for movement in-game. The remote is intended for UI testing first and is **not** a complete NES pad.
5. Press remote Back in-game and verify the pause menu. Resume currently restarts ROM from frame zero; Exit returns to TV menu.
6. Repeat with a second ROM and check black screen, audio underrun, crash, or timing problems.

## 3. Gamepad (later)

- Verify Android enumerates the device in `adb shell getevent -lp` first.
- Default A/B normal; X/Y Turbo A/B. Start, Select and D-pad.
- Test independent turbo pulses, key rebinding, and no stuck inputs after game exit or disconnect.
- Existing USB Xbox 360 mode `045e:028e` may not work if kernel lacks `CONFIG_JOYSTICK_XPAD`.

## 4. Logs to collect when failing

```sh
adb logcat -c
adb logcat -v time > nes-tv-test-logcat.txt
adb shell dumpsys input
adb shell getprop ro.product.cpu.abilist
```

**Acceptance:** A CI pass is not a substitute for actual MiTV-MSSP3 verification.

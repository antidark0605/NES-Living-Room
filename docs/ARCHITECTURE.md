# Architecture

```text
TV remote (D-pad/OK/Back) --> Android TV menus
                               | ROM URI (SAF)
Physical gamepad KeyEvents --> NesInput --> 8-bit NES pad mask
                                           |
                                           v
                                    [NES core TODO]
                                           |
                                  video / audio TODO
```

## Boundaries

- **UI focus is separate from NES control:** remote keys navigate menus. Tester intercepts a configured key if it collides with a focus key; keep testing limited to external gamepad buttons.
- **Input mapper:** digital Android key codes, 8 NES buttons, two dedicated turbo bindings. Independent from any Xbox layout.
- **Turbo:** timestamps use `SystemClock.uptimeMillis()`; 10/15/20 pulses per second. The frontend returns an 8-bit mask to a future core at emulated-frame intervals.
- **ROM loading:** Android Storage Access Framework grants URI access; the future core must read bytes from `ContentResolver` rather than assuming filesystem paths.
- **Emulator:** intentionally absent. Integrate only after selecting a license-compatible implementation and building 32-bit ARM binaries.
- **Unsupported hardware:** missing kernel input driver cannot be fixed by an APK. Analog axes are also out of scope for this PR.

## Validation needed

1. Install APK via ADB on MiTV-MSSP3 and verify TV launcher tile.
2. Navigate every screen using only remote D-pad / OK / Back.
3. Pick a local NES file using system document picker and verify persistence across restart.
4. Pair/plug recognized gamepad; bind keys and view tester status.
5. Verify Turbo A and B independently.
6. On a controller with no Android input device entry, record USB VID/PID and `getevent -lp` before changing the app.

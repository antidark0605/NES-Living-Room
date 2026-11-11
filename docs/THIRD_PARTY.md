# Third-party core and redistribution

NES Living Room's native emulation engine is [FCEUmm](https://github.com/libretro/libretro-fceumm), which is licensed under the **GNU General Public License version 2** (GPLv2).

Pinned upstream source revision: `e0f3086a8104f8fbbe49a2615b10401504a9533f`.

The CI workflow checks out this source revision and builds the ARMv7 native library without modifying upstream source. The compiled `libretro.so` and our JNI bridge are packaged together in the test APK.

## License and corresponding source

See the [upstream GPLv2 license text](https://github.com/libretro/libretro-fceumm/blob/e0f3086a8104f8fbbe49a2615b10401504a9533f/Copying), upstream source code at the pinned revision, and this repository's frontend/bridge source. The CI build steps document how to reproduce the binary.

The repository and binary distribution should carry notices and complete corresponding source as required by GPLv2. This test frontend and combined distribution are intended to be GPLv2-compatible; verify third-party dependency terms before publishing a stable release. No Nintendo ROMs or copyrighted game content are included.

## Limitations

A successful compile does not establish real-device compatibility. MiTV-MSSP3 video, audio, ROM mapper support and remote interaction require actual hardware validation.

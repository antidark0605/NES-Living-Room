package dev.antidark.neslivingroom;

/** JNI facade around an independently compiled libretro NES core (ARMv7). */
public final class NativeNes {
    static { System.loadLibrary("nesbridge"); }
    private NativeNes() {}
    public static native boolean open(String libraryPath, String systemDir);
    public static native boolean load(byte[] rom);
    public static native void runFrame(int inputMask);
    public static native int[] framePixels();
    public static native int frameWidth();
    public static native int frameHeight();
    public static native short[] frameAudio();
    public static native int sampleRate();
    public static native double frameRate();
    public static native byte[] saveState();
    public static native boolean loadState(byte[] state);
    public static native void close();
}

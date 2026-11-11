package dev.antidark.neslivingroom;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

/** Maps Android digital key events into NES controls. No emulator core is loaded. */
public final class NesInput {
    public static final String[] NAMES = {"Up", "Down", "Left", "Right", "A", "B", "Start", "Select", "Turbo A", "Turbo B"};
    public static final int UP=0, DOWN=1, LEFT=2, RIGHT=3, A=4, B=5, START=6, SELECT=7, TURBO_A=8, TURBO_B=9;
    private static final int[] DEFAULTS = {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y
    };
    private final SharedPreferences prefs;
    private final boolean[] physical = new boolean[10];
    private final int[] keys = new int[10];
    private int turboHz;

    public NesInput(Context context) {
        prefs = context.getSharedPreferences("nes_input", Context.MODE_PRIVATE);
        for (int i=0;i<keys.length;i++) keys[i] = prefs.getInt("key_"+i, DEFAULTS[i]);
        turboHz = prefs.getInt("turbo_hz", 15);
    }
    public int binding(int action) { return keys[action]; }
    public void bind(int action, int keyCode) {
        if (action < 0 || action >= keys.length) throw new IllegalArgumentException("action");
        keys[action]=keyCode;
        physical[action]=false;
        prefs.edit().putInt("key_"+action,keyCode).apply();
    }
    public int turboHz() { return turboHz; }
    public void cycleTurboHz() {
        turboHz = turboHz==10 ? 15 : turboHz==15 ? 20 : 10;
        prefs.edit().putInt("turbo_hz",turboHz).apply();
    }
    public boolean onKey(int keyCode, boolean down) {
        boolean consumed=false;
        for (int i=0;i<keys.length;i++) {
            if (keys[i]==keyCode) { physical[i]=down; consumed=true; }
        }
        return consumed;
    }
    /** At a specific monotonic timestamp, return an NES-standard eight-button mask. */
    public int mask(long uptimeMillis) {
        boolean pulse = ((uptimeMillis * turboHz / 500L) % 2L)==0L;
        int mask=0;
        for (int i=0;i<8;i++) {
            boolean held=physical[i] || (i==A && physical[TURBO_A] && pulse)
                    || (i==B && physical[TURBO_B] && pulse);
            if (held) mask|=1<<i;
        }
        return mask;
    }
    public void releaseAll() {
        for (int i=0;i<physical.length;i++) physical[i]=false;
    }
}

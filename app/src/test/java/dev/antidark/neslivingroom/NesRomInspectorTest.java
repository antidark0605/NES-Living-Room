package dev.antidark.neslivingroom;

import org.junit.Test;
import static org.junit.Assert.*;

public final class NesRomInspectorTest {
    @Test public void acceptsInesMagic() {
        byte[] h = new byte[16];
        h[0]='N'; h[1]='E'; h[2]='S'; h[3]=0x1a;
        assertTrue(NesRomInspector.isNesHeader(h,16));
    }
    @Test public void rejectsTruncatedHeader() {
        byte[] h = new byte[16];
        h[0]='N'; h[1]='E'; h[2]='S'; h[3]=0x1a;
        assertFalse(NesRomInspector.isNesHeader(h,15));
    }
    @Test public void rejectsUnknownContent() {
        assertFalse(NesRomInspector.isNesHeader(new byte[16],16));
    }
}

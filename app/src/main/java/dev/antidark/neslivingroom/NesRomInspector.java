package dev.antidark.neslivingroom;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;

/** Header-only check for iNES and NES 2.0 ROM images; not a full integrity check. */
public final class NesRomInspector {
    private NesRomInspector() {}

    public static boolean hasValidHeader(ContentResolver resolver, Uri uri) {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return false;
            byte[] header = new byte[16];
            int count = 0;
            while (count < header.length) {
                int n = in.read(header, count, header.length - count);
                if (n < 0) break;
                if (n == 0) {
                    int single = in.read();
                    if (single < 0) break;
                    header[count++] = (byte) single;
                } else {
                    count += n;
                }
            }
            return isNesHeader(header, count);
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    static boolean isNesHeader(byte[] header, int count) {
        return header != null && count >= 16 && header.length >= 16
                && header[0]=='N' && header[1]=='E' && header[2]=='S'
                && (header[3] & 0xff)==0x1a;
    }
}

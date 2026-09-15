import com.kemi.dualscreensaver.WallpaperLayout;

public final class LayoutCheck {
    static void equal(float actual, float expected) {
        if (Math.abs(actual - expected) > 0.001f)
            throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        for (int rotation = 0; rotation < 4; rotation++) {
            int w = rotation % 2 == 0 ? 1920 : 1280;
            int h = rotation % 2 == 0 ? 1280 : 1920;
            WallpaperLayout p = WallpaperLayout.fit(w, h, 1920, 1280, rotation);
            equal(p.scale, 1f);
            equal(p.degrees, rotation == 1 ? 90 : rotation == 3 ? -90 : 0);
            float outW = p.degrees == 0 ? p.drawWidth : p.drawHeight;
            float outH = p.degrees == 0 ? p.drawHeight : p.drawWidth;
            equal(outW, w);
            equal(outH, h);
            // Full four-corner coverage with integral centered offsets and no crop/letterbox.
            equal((w - outW) / 2, 0);
            equal((h - outH) / 2, 0);
            System.out.println("PASS rotation=" + (rotation * 90) + " surface=" + w + "x" + h + " scale=1 no crop");
        }
        WallpaperLayout other = WallpaperLayout.fit(1600, 900, 1920, 1280, 0);
        equal(other.scale, 900f / 1280f);
        equal(other.drawHeight, 900);
        if (other.drawWidth > 1600) throw new AssertionError("Crops nonstandard display");
        try {
            WallpaperLayout.fit(0, 1280, 1920, 1280, 0);
            throw new AssertionError("Zero surface accepted");
        } catch (IllegalArgumentException expected) {}
        System.out.println("PASS nonstandard ratio preserves full image; invalid surface rejected");
    }
}

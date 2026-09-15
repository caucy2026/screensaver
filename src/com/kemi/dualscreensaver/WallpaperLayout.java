package com.kemi.dualscreensaver;

/** Pixel geometry independent of Android; rotation is Surface.ROTATION_0..270 (0..3). */
public final class WallpaperLayout {
    public final float degrees;
    public final float scale;
    public final float drawWidth;
    public final float drawHeight;

    private WallpaperLayout(float degrees, float scale, int imageWidth, int imageHeight) {
        this.degrees = degrees;
        this.scale = scale;
        this.drawWidth = imageWidth * scale;
        this.drawHeight = imageHeight * scale;
    }

    public static WallpaperLayout fit(int surfaceWidth, int surfaceHeight,
            int imageWidth, int imageHeight, int rotation) {
        if (surfaceWidth <= 0 || surfaceHeight <= 0 || imageWidth <= 0 || imageHeight <= 0)
            throw new IllegalArgumentException("Image and Surface dimensions must be positive");
        boolean quarterTurn = (surfaceHeight > surfaceWidth) != (imageHeight > imageWidth);
        float degrees = quarterTurn ? (rotation == 3 ? -90f : 90f) : 0f;
        // Android already orients the Surface at 180 degrees: do not rotate twice.
        float scale = quarterTurn
                ? Math.min((float) surfaceHeight / imageWidth, (float) surfaceWidth / imageHeight)
                : Math.min((float) surfaceWidth / imageWidth, (float) surfaceHeight / imageHeight);
        return new WallpaperLayout(degrees, scale, imageWidth, imageHeight);
    }
}

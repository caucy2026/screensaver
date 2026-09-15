package com.kemi.dualscreensaver;

/** Full 1920x2560 frame is stored clockwise as 2560x1920 for hardware decoding. */
public final class VideoFrameLayout {
    private VideoFrameLayout() {}
    /** BL, BR, TL, TR; SurfaceTexture applies the final producer transform. */
    public static float[] textureCoordinates(int displayId) {
        float top=displayId==2?1f:0.5f;
        float bottom=top-0.5f;
        return new float[]{bottom,1,bottom,0,top,1,top,0};
    }
}

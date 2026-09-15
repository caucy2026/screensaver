import com.kemi.dualscreensaver.VideoFrameLayout;
/** Verify pixel centers after clockwise storage, producer flip and panel split. */
public final class VideoFrameCheck {
    static void near(float actual,float expected) {
        if(Math.abs(actual-expected)>.001f) throw new AssertionError(actual+" != "+expected);
    }
    static float[] source(int display,int x,int y) {
        float[] uv=VideoFrameLayout.textureCoordinates(display);
        float sx=(x+.5f)/1920,sy=(y+.5f)/1280;
        float encodedX=uv[4]+(uv[0]-uv[4])*sy;
        float preFlipY=uv[5]+(uv[7]-uv[5])*sx;
        return new float[]{(1-preFlipY)*1920,(1-encodedX)*2560};
    }
    public static void main(String[] args) {
        for(int display:new int[]{2,0}) for(int x:new int[]{0,1,959,1918,1919})
            for(int y:new int[]{0,1,639,1278,1279}) {
                float[] p=source(display,x,y);
                near(p[0],x+.5f); near(p[1],y+.5f+(display==2?0:1280));
            }
        for(int x:new int[]{0,959,1919}) near(source(0,x,0)[1]-source(2,x,1279)[1],1);
        System.out.println("PASS: both panels preserve 1:1 pixel centers and share an exact contiguous seam");
    }
}

package com.kemi.dualscreensaver;
import android.app.Activity;
import android.os.Bundle;
import android.view.*;
/** Ordinary app window on D0; no overlay permission or system window type. */
public final class ScreensaverPanelActivity extends Activity implements SurfaceHolder.Callback {
    private static ScreensaverPanelActivity shown;
    private ScreensaverDreamService owner;
    private ScreensaverRenderer renderer;
    private SurfaceView panel;
    private boolean closing;
    private int width,height;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); owner=ScreensaverDreamService.active;
        if(owner==null || !owner.accepts(getIntent().getLongExtra("session",-1))) { finishAndRemoveTask(); return; }
        shown=this; panel=new SurfaceView(this);panel.getHolder().addCallback(this);
        setContentView(panel);ScreensaverDreamService.immersive(getWindow());
    }
    static void closeSession() {
        if(shown!=null) { ScreensaverPanelActivity a=shown;shown=null;a.closing=true;a.release();a.finishAndRemoveTask(); }
    }
    private void exit() { if(!closing && owner!=null) owner.requestExit("D0-touch"); }
    @Override public boolean dispatchTouchEvent(MotionEvent e) { if(e.getActionMasked()==MotionEvent.ACTION_DOWN) exit();return true; }
    @Override public boolean dispatchKeyEvent(KeyEvent e) { if(e.getAction()==KeyEvent.ACTION_DOWN) exit();return true; }
    public void surfaceCreated(SurfaceHolder h) {
        if(closing || owner!=ScreensaverDreamService.active) return;
        if(panel.getDisplay()==null || panel.getDisplay().getDisplayId()!=0) { owner.requestExit("D0-wrong-display");return; }
        if(android.os.Build.VERSION.SDK_INT>=30) h.getSurface().setFrameRate(24f,Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
        renderer=new ScreensaverRenderer(getAssets(),h.getSurface(),0);
    }
    public void surfaceChanged(SurfaceHolder h,int format,int w,int z) { width=w;height=z;if(renderer!=null) renderer.configure(width,height,panel.getDisplay().getRotation(),true); }
    public void surfaceDestroyed(SurfaceHolder h) { release(); }
    private void release() { if(renderer!=null){renderer.destroy();renderer=null;} }
    @Override protected void onDestroy() { release();if(shown==this)shown=null;if(!closing && owner==ScreensaverDreamService.active)owner.requestExit("D0-window-closed");super.onDestroy(); }
}

package com.kemi.dualscreensaver;
import android.app.Presentation;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.service.dreams.DreamService;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** Android hosts this screen saver independently of the selector and wallpaper. */
public final class ScreensaverDreamService extends DreamService
        implements DisplayManager.DisplayListener {
    static ScreensaverDreamService active;
    private boolean secondaryActivityStarted;
    private static final String TAG="KemiScreensaver";
    private Panel mainPanel,secondaryPanel;
    private Presentation presentation;
    private DisplayManager displays;
    private boolean running,cleanupStarted;
    private long session,attachDeadline;
    private final java.util.concurrent.atomic.AtomicBoolean exitRequested=new java.util.concurrent.atomic.AtomicBoolean();
    static void immersive(Window window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowManager.LayoutParams attr=window.getAttributes();attr.preferredRefreshRate=24f;window.setAttributes(attr);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        cleanupStarted=false; secondaryActivityStarted=false; active=this; exitRequested.set(false);
        setInteractive(false); setFullscreen(true); setScreenBright(true);
        if(!ScreensaverCatalog.complete(getAssets())) { Log.e(TAG,"All active videos are required"); finish(); return; }
        session=ScreensaverRenderer.beginSession(getAssets(),ScreensaverCatalog.selected(this));
        mainPanel=new Panel(this,0); setContentView(mainPanel); immersive(getWindow());
    }
    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if(mainPanel==null) { finish(); return; }
        running=true; attachDeadline=android.os.SystemClock.uptimeMillis()+2000; mainPanel.post(this::startPanels);
    }
    private void startPanels() {
        if(!running || presentation!=null || secondaryActivityStarted) return;
        displays=(DisplayManager)getSystemService(DISPLAY_SERVICE);
        Display display=displays.getDisplay(2);
        Display main=mainPanel.getDisplay();
        if(main==null && android.os.SystemClock.uptimeMillis()<attachDeadline) {
            mainPanel.postDelayed(this::startPanels,50); return;
        }
        if(display==null || main==null || (main.getDisplayId()!=0 && main.getDisplayId()!=2)) {
            Log.e(TAG,"Expected S1 displays unavailable: main="+(main==null?"null":main.getDisplayId())+" D2="+(display!=null)); finish(); return;
        }
        final int secondaryId=main.getDisplayId()==0?2:0;
        display=displays.getDisplay(secondaryId);
        if(display==null) { finish(); return; }
        running=true;
        displays.registerDisplayListener(this,null);
        if(secondaryId==0) {
            secondaryActivityStarted=true;
            android.app.ActivityOptions options=android.app.ActivityOptions.makeBasic();
            options.setLaunchDisplayId(0);
            android.content.Intent intent=new android.content.Intent(this,ScreensaverPanelActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION);
            intent.putExtra("session",session);
            try { startActivity(intent,options.toBundle()); mainPanel.refresh(); }
            catch(RuntimeException e) { Log.e(TAG,"Secondary activity failed",e); requestExit("D0-launch-failure"); }
            return;
        }
        presentation=new Presentation(this,display,android.R.style.Theme_Material_NoActionBar_Fullscreen) {
            @Override protected void onCreate(Bundle state) {
                super.onCreate(state); secondaryPanel=new Panel(getContext(),secondaryId); setContentView(secondaryPanel); immersive(getWindow());
            }
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN) requestExit("D"+secondaryId+"-touch");
                return true;
            }
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                if(event.getAction()==KeyEvent.ACTION_DOWN) requestExit("D"+secondaryId+"-key");
                return true;
            }
        };
        try { presentation.show(); mainPanel.refresh(); Log.i(TAG,"Dream started session="+session+" with one shared fixed session choice D2 D0"); }
        catch(WindowManager.InvalidDisplayException | WindowManager.BadTokenException e) {
            Log.e(TAG,"Secondary display unavailable",e); finish();
        }
    }
    @Override public void onDisplayAdded(int id) {}
    @Override public void onDisplayChanged(int id) {
        if(mainPanel!=null) mainPanel.refresh(); if(secondaryPanel!=null) secondaryPanel.refresh();
    }
    @Override public void onDisplayRemoved(int id) { if(id==0 || id==2) finish(); }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN) requestExit("D"+(mainPanel==null?0:mainPanel.displayId)+"-touch");
        return true;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getAction()==KeyEvent.ACTION_DOWN) requestExit("D"+(mainPanel==null?0:mainPanel.displayId)+"-key");
        return true;
    }
    void requestExit(String source) {
        if(!exitRequested.compareAndSet(false,true)) return;
        Log.i(TAG,"Exit requested session="+session+" source="+source+" targets=D2 D0");
        stopPanels();
        finish();
    }
    private void stopPanels() {
        if(cleanupStarted) return;
        cleanupStarted=true; running=false; active=null;
        ScreensaverPanelActivity.closeSession();
        long start=android.os.SystemClock.elapsedRealtime();
        if(displays!=null) displays.unregisterDisplayListener(this);
        boolean released=ScreensaverRenderer.endSession();
        if(mainPanel!=null) mainPanel.release();
        if(secondaryPanel!=null) secondaryPanel.release();
        if(presentation!=null) { presentation.dismiss(); presentation=null; }
        Log.i(TAG,"Cleanup complete session="+session+" targets=D2 D0 decoderReleased="+released
            +" elapsedMs="+(android.os.SystemClock.elapsedRealtime()-start));
    }
    @Override public void onDreamingStopped() { stopPanels(); super.onDreamingStopped(); }
    @Override public void onDetachedFromWindow() { stopPanels(); mainPanel=null; secondaryPanel=null; super.onDetachedFromWindow(); }
    @Override public void onDestroy() { stopPanels(); super.onDestroy(); }
    boolean accepts(long id) { return running && !cleanupStarted && session==id; }
    private final class Panel extends SurfaceView implements SurfaceHolder.Callback {
        int displayId;
        ScreensaverRenderer renderer;
        int width,height;
        Panel(Context context,int id) { super(context); displayId=id; getHolder().addCallback(this); }
        @Override public void surfaceCreated(SurfaceHolder holder) {
            if(cleanupStarted) return;
            Display actual=getDisplay();
            if(actual!=null) displayId=actual.getDisplayId();
            if(android.os.Build.VERSION.SDK_INT>=30) holder.getSurface().setFrameRate(24f,android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
            renderer=new ScreensaverRenderer(getAssets(),holder.getSurface(),displayId);
            width=holder.getSurfaceFrame().width(); height=holder.getSurfaceFrame().height(); refresh();
        }
        @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h) { width=w; height=h; refresh(); }
        void refresh() {
            Display display=getDisplay();
            if(renderer!=null && display!=null) renderer.configure(width,height,display.getRotation(),running);
        }
        void release() { if(renderer!=null) { renderer.destroy(); renderer=null; } }
        @Override public void surfaceDestroyed(SurfaceHolder holder) { release(); }
    }
}

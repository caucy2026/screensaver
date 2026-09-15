package com.kemi.dualscreensaver;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** All displays sample one decoded frame, on one GL thread and context. */
final class ScreensaverRenderer {
    private static final String TAG = "KemiScreensaverVideo";
    private static Hub shared;
    private static final java.util.concurrent.atomic.AtomicLong sessions=new java.util.concurrent.atomic.AtomicLong();
    static synchronized long beginSession(AssetManager assets,int selected) {
        if(shared==null) shared=new Hub(assets);
        final Hub hub=shared;
        final long session=sessions.incrementAndGet();
        hub.handler.post(() -> hub.beginSession(session,selected));
        return session;
    }
    static boolean endSession() {
        final Hub old;
        synchronized(ScreensaverRenderer.class) { old=shared; shared=null; }
        if(old==null) return true;
        CountDownLatch done=new CountDownLatch(1);
        if(!old.handler.post(() -> {
            try { old.shutdown(); } finally { done.countDown(); }
        })) return !old.thread.isAlive();
        try { return done.await(2,TimeUnit.SECONDS) && old.player==null
                && old.decoderSurface==null && old.decoderTexture==null && old.targets.isEmpty() && !old.initialized; }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); return false; }
    }
    private final Hub hub;
    private final Target target;

    ScreensaverRenderer(AssetManager assets, Surface output, int displayId) {
        synchronized (ScreensaverRenderer.class) {
            if (shared == null) shared = new Hub(assets);
            hub = shared;
        }
        target = new Target(output,displayId);
    }
    void configure(int width,int height,int rotation,boolean visible) {
        hub.handler.post(() -> hub.configure(target,width,height,rotation,visible));
    }
    void destroy() {
        CountDownLatch done = new CountDownLatch(1);
        if(!hub.handler.post(() -> { try { hub.remove(target); } finally { done.countDown(); } })) return;
        try { done.await(2,TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static final class Target {
        final Surface output;
        final int displayId;
        EGLSurface window = EGL14.EGL_NO_SURFACE;
        int width,height,rotation,theme;
        boolean visible,destroyed;
        Target(Surface output,int displayId) { this.output=output; this.displayId=displayId; }
        boolean videoVisible() { return visible && ScreensaverCatalog.isVideo(theme) && !destroyed; }
        String poster() { return ScreensaverCatalog.stem(theme)+(displayId==2?"_d2":"_d0")+".png"; }
    }

    private static final class Hub {
        final Handler handler;
        final HandlerThread thread;
        final AssetManager assets;
        final Set<Target> targets = new java.util.TreeSet<>((a,b) -> Integer.compare(b.displayId,a.displayId));
        EGLDisplay display = EGL14.EGL_NO_DISPLAY;
        EGLContext context = EGL14.EGL_NO_CONTEXT;
        EGLSurface offscreen = EGL14.EGL_NO_SURFACE;
        EGLConfig config;
        boolean initialized,hasFrame;
        int imageProgram,videoProgram,videoTexture,playingTheme;
        final PlaybackSequence sequence=new PlaybackSequence(new java.util.Random(),ScreensaverCatalog.activeIds());
        MediaPlayer player;
        SurfaceTexture decoderTexture;
        Surface decoderSurface;
        final float[] textureMatrix = new float[16];
        static final String VERTEX="attribute vec2 aPosition; attribute vec2 aTex; uniform mat4 uTexMatrix; varying vec2 vTex; void main(){gl_Position=vec4(aPosition,0.,1.);vTex=(uTexMatrix*vec4(aTex,0.,1.)).xy;}";
        static final String FRAGMENT="precision mediump float; varying vec2 vTex; uniform sampler2D uTexture; void main(){gl_FragColor=texture2D(uTexture,vTex);}";
        static final String VIDEO_FRAGMENT="#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 vTex; uniform samplerExternalOES uTexture; void main(){gl_FragColor=texture2D(uTexture,vTex);}";

        int selected=1;
        long loggedLoop=-1;
        Hub(AssetManager assets) {
            this.assets=assets;
            thread=new HandlerThread("ScreensaverSharedFrame"); thread.start();
            handler=new Handler(thread.getLooper());
        }
        void configure(Target t,int width,int height,int rotation,boolean visible) {
            if(t.destroyed || width<=0 || height<=0) return;
            t.theme=selected; t.width=width; t.height=height; t.rotation=rotation; t.visible=visible;
            targets.add(t);
            try {
                if(!initialized) initialize();
                if(t.window==EGL14.EGL_NO_SURFACE) {
                    t.window=EGL14.eglCreateWindowSurface(display,config,t.output,new int[]{EGL14.EGL_NONE},0);
                    if(t.window==EGL14.EGL_NO_SURFACE) throw new IllegalStateException("eglCreateWindowSurface");
                }
                if(t.videoVisible() && t.theme==playingTheme && hasFrame) drawVideo(t,System.nanoTime()+41_666_667L);
                else drawPoster(t);
                reconcileVideo();
            } catch(Exception e) { Log.e(TAG,"Configure D"+t.displayId,e); }
        }
        void beginSession(long session,int choice) {
            if(initialized) makeCurrent(offscreen);
            stopVideo(); sequence.startSelected(session,choice); selected=sequence.current();
            Log.i(TAG,"Playlist session="+sequence.session()+" selected="+selected+" candidates="+ScreensaverCatalog.activeIds().length);
            for(Target t:targets) t.theme=selected;
        }
        void shutdown() {
            sequence.stop();
            try {
                if(initialized) makeCurrent(offscreen);
                stopVideo();
                for(Target target:new java.util.ArrayList<>(targets)) remove(target);
                Log.i(TAG,"Session released session="+sequence.session()+" frames="+sequence.frame()
                    +" targets="+targets.size()+" player="+(player==null?"none":"present")
                    +" decoderSurface="+(decoderSurface==null?"none":"present"));
            } finally { thread.quitSafely(); }
        }
        void initialize() {
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY); int[] versions=new int[2];
            if(!EGL14.eglInitialize(display,versions,0,versions,1)) throw new IllegalStateException("eglInitialize");
            int[] attrs={EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT|EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_NONE};
            EGLConfig[] configs=new EGLConfig[1]; int[] n=new int[1];
            if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,n,0)||n[0]==0) throw new IllegalStateException("eglChooseConfig");
            config=configs[0];
            context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            offscreen=EGL14.eglCreatePbufferSurface(display,config,new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
            makeCurrent(offscreen);
            imageProgram=program(FRAGMENT); videoProgram=program(VIDEO_FRAGMENT); initialized=true;
        }
        void makeCurrent(EGLSurface surface) {
            if(!EGL14.eglMakeCurrent(display,surface,surface,context)) throw new IllegalStateException("eglMakeCurrent");
            // Submit both displays without waiting for the first display's vblank.
            EGL14.eglSwapInterval(display,0);
        }
        void remove(Target t) {
            t.destroyed=true; targets.remove(t);
            if(!initialized) return;
            makeCurrent(offscreen);
            if(t.window!=EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,t.window);
            t.window=EGL14.EGL_NO_SURFACE;
            reconcileVideo();
            if(targets.isEmpty()) {
                stopVideo(); GLES20.glDeleteProgram(imageProgram); GLES20.glDeleteProgram(videoProgram);
                EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(display,offscreen); EGL14.eglDestroyContext(display,context);
                EGL14.eglTerminate(display); initialized=false;
            }
        }
        void reconcileVideo() {
            int wanted=0, panels=0;
            for(Target t:targets) if(t.videoVisible() && t.window!=EGL14.EGL_NO_SURFACE) { wanted=t.theme; panels++; }
            if(panels!=2 || !sequence.active()) wanted=0;
            if(wanted==0) stopVideo();
            else if(player==null || playingTheme!=wanted) {
                stopVideo();
                try { startVideo(wanted); }
                catch(Exception e) { Log.e(TAG,"Shared decoder setup failed",e); stopVideo(); }
            }
        }
        void startVideo(int theme) throws Exception {
            playingTheme=theme;
            makeCurrent(offscreen);
            videoTexture=texture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            decoderTexture=new SurfaceTexture(videoTexture);
            decoderTexture.setDefaultBufferSize(2560,1920);
            decoderTexture.setOnFrameAvailableListener(st -> acquireAndRender(st),handler);
            decoderSurface=new Surface(decoderTexture);
            MediaPlayer mp=new MediaPlayer(); player=mp;
            mp.setVolume(0,0); mp.setLooping(true); mp.setSurface(decoderSurface);
            try(AssetFileDescriptor fd=assets.openFd(ScreensaverCatalog.stem(theme)+"_video.mp4")) {
                mp.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());
            }
            final long session=sequence.session();
            mp.setOnPreparedListener(p -> {
                if(player!=p || !sequence.matches(session)) return;
                sequence.duration(p.getDuration()*1_000_000L);
                p.start();
                Log.i(TAG,"Single decoder session="+session+" content="+theme+" nativeLoop=true playing "+p.getVideoWidth()+"x"+p.getVideoHeight()+" duration="+p.getDuration());
            });
            mp.setOnErrorListener((p,what,extra) -> {
                Log.e(TAG,"Shared decoder error "+what+"/"+extra);
                handler.post(() -> {
                    if(player!=p) return;
                    stopVideo(); for(Target t:targets) if(t.videoVisible()) drawPoster(t);
                });
                return true;
            });
            mp.prepareAsync();
        }
        void acquireAndRender(SurfaceTexture st) {
            if(st==null || st!=decoderTexture || !sequence.active()) return;
            try {
                makeCurrent(offscreen);
                st.updateTexImage(); st.getTransformMatrix(textureMatrix);
                PlaybackSequence.Frame frame=sequence.acquired(st.getTimestamp());
                if(frame==null) return;
                hasFrame=true;
                long presentAt=System.nanoTime()+41_666_667L;
                StringBuilder rendered=new StringBuilder();
                for(Target t:targets) if(t.videoVisible() && t.theme==frame.content) {
                    drawVideo(t,presentAt); rendered.append('D').append(t.displayId).append(' ');
                }
                if(frame.number%60==1 || frame.loop!=loggedLoop) {
                    loggedLoop=frame.loop;
                    Log.i(TAG,
                    "Shared session="+frame.session+" loop="+frame.loop+" content="+frame.content
                    +" frame="+frame.number+" pts="+frame.pts+" timeline="+frame.timeline+" targets="+rendered);
                }
            } catch(Exception e) { Log.e(TAG,"Shared frame failed",e); }
        }
        void stopVideo() {
            if(player!=null) { MediaPlayer old=player; player=null; old.release(); Log.i(TAG,"Shared decoder released"); }
            if(decoderSurface!=null) { decoderSurface.release(); decoderSurface=null; }
            if(decoderTexture!=null) { decoderTexture.setOnFrameAvailableListener(null); decoderTexture.release(); decoderTexture=null; }
            if(videoTexture!=0) { GLES20.glDeleteTextures(1,new int[]{videoTexture},0); videoTexture=0; }
            hasFrame=false; playingTheme=0;
        }
        void drawVideo(Target t,long presentAt) {
            draw(t,videoProgram,GLES11Ext.GL_TEXTURE_EXTERNAL_OES,videoTexture,textureMatrix,
                VideoFrameLayout.textureCoordinates(t.displayId),presentAt);
        }
        void drawPoster(Target t) {
            Bitmap bitmap=null; int id=0;
            try(InputStream in=assets.open(t.poster())) {
                makeCurrent(t.window);
                BitmapFactory.Options options=new BitmapFactory.Options(); options.inScaled=false;
                options.inPreferredConfig=Bitmap.Config.RGB_565;
                bitmap=BitmapFactory.decodeStream(in,null,options);
                if(bitmap==null) throw new IllegalStateException("Missing poster");
                id=texture(GLES20.GL_TEXTURE_2D); GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);
                float[] matrix=new float[16]; Matrix.setIdentityM(matrix,0);
                draw(t,imageProgram,GLES20.GL_TEXTURE_2D,id,matrix,new float[]{0,1,1,1,0,0,1,0},0);
            } catch(Exception e) { Log.e(TAG,"Poster D"+t.displayId,e); }
            finally { if(bitmap!=null) bitmap.recycle(); if(id!=0) GLES20.glDeleteTextures(1,new int[]{id},0); }
        }
        void draw(Target t,int program,int target,int texture,float[] matrix,float[] uv,long presentAt) {
            makeCurrent(t.window);
            WallpaperLayout layout=WallpaperLayout.fit(t.width,t.height,1920,1280,t.rotation);
            float[] base={-1,-1,1,-1,-1,1,1,1},vertices=new float[8];
            double radians=Math.toRadians(-layout.degrees);
            float c=(float)Math.cos(radians),s=(float)Math.sin(radians);
            for(int i=0;i<4;i++) {
                float x=base[i*2]*layout.drawWidth/2,y=base[i*2+1]*layout.drawHeight/2;
                vertices[i*2]=(x*c-y*s)*2/t.width; vertices[i*2+1]=(x*s+y*c)*2/t.height;
            }
            GLES20.glViewport(0,0,t.width,t.height); GLES20.glClearColor(0,0,0,1); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            int pos=GLES20.glGetAttribLocation(program,"aPosition"),tex=GLES20.glGetAttribLocation(program,"aTex");
            GLES20.glEnableVertexAttribArray(pos); GLES20.glEnableVertexAttribArray(tex);
            GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,floats(vertices));
            GLES20.glVertexAttribPointer(tex,2,GLES20.GL_FLOAT,false,0,floats(uv));
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uTexMatrix"),1,false,matrix,0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(target,texture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTexture"),0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            GLES20.glDisableVertexAttribArray(pos); GLES20.glDisableVertexAttribArray(tex);
            if(presentAt!=0) EGLExt.eglPresentationTimeANDROID(display,t.window,presentAt);
            if(!EGL14.eglSwapBuffers(display,t.window)) throw new IllegalStateException("eglSwapBuffers D"+t.displayId);
        }
        static int shader(int type,String source) {
            int s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,source); GLES20.glCompileShader(s);
            int[] ok=new int[1]; GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
            if(ok[0]==0) throw new IllegalStateException(GLES20.glGetShaderInfoLog(s)); return s;
        }
        static int program(String fragment) {
            int v=shader(GLES20.GL_VERTEX_SHADER,VERTEX),f=shader(GLES20.GL_FRAGMENT_SHADER,fragment);
            int p=GLES20.glCreateProgram(); GLES20.glAttachShader(p,v); GLES20.glAttachShader(p,f); GLES20.glLinkProgram(p);
            GLES20.glDeleteShader(v); GLES20.glDeleteShader(f); int[] ok=new int[1];
            GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);
            if(ok[0]==0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(p)); return p;
        }
        static int texture(int target) {
            int[] ids=new int[1]; GLES20.glGenTextures(1,ids,0); GLES20.glBindTexture(target,ids[0]);
            GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            return ids[0];
        }
        static FloatBuffer floats(float[] values) {
            FloatBuffer b=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            b.put(values).position(0); return b;
        }
    }
}

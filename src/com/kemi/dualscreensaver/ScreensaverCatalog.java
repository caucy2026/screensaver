package com.kemi.dualscreensaver;
import android.content.res.AssetManager;
import java.io.InputStream;
/** Separate content IDs: never persisted in or sent to the wallpaper catalog. */
public final class ScreensaverCatalog {
    private ScreensaverCatalog() {}
    public static final int COUNT=3;
    private static final String CHOICE_KEY="kemi_screensaver_selected_content";
    public static int selected(android.content.Context context) {
        int id=android.provider.Settings.Secure.getInt(context.getContentResolver(),CHOICE_KEY,1);
        return id==3?3:1;
    }
    public static boolean select(android.content.Context context,int id) {
        if(id!=1 && id!=3) return false;
        return android.provider.Settings.Secure.putInt(context.getContentResolver(),CHOICE_KEY,id);
    }
    public static int[] activeIds() { return new int[]{1,3}; }
    public static final String SERVICE_PACKAGE="com.kemi.dualscreensaver";
    public static boolean valid(int id) { return id>=1 && id<=COUNT; }
    public static boolean isVideo(int id) { return valid(id); }
    public static String stem(int id) {
        if(!valid(id)) throw new IllegalArgumentException("content");
        return id==1 ? "silver" : id==2 ? "douyin" : "spring";
    }
    public static boolean complete(AssetManager assets) { for(int id:activeIds()) if(!available(assets,id)) return false; return true; }
    public static boolean available(AssetManager assets,int id) {
        if(!valid(id)) return false;
        try(InputStream in=assets.open("ready_"+id)) { return in.read()==49; }
        catch(Exception e) { return false; }
    }
}

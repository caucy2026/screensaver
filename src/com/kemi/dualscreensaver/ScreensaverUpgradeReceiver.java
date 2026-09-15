package com.kemi.dualscreensaver;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.provider.Settings;
/** Move only this app's legacy selection; preserve another selected screensaver. */
public final class ScreensaverUpgradeReceiver extends BroadcastReceiver {
    static void migrate(Context context) {
        String old=new ComponentName("com.kemi.dualscreensaver.service",ScreensaverDreamService.class.getName()).flattenToString();
        String current=new ComponentName(context,ScreensaverDreamService.class).flattenToString();
        for(String key:new String[]{"screensaver_components","screensaver_default_component","screen_protection_plugged"}) {
            if(old.equals(Settings.Secure.getString(context.getContentResolver(),key))) {
                if(!Settings.Secure.putString(context.getContentResolver(),key,current))
                    android.util.Log.e("KemiScreensaver","Legacy selection migration rejected: "+key);
            }
        }
    }
    @Override public void onReceive(Context context,Intent intent) {
        if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) migrate(context);
    }
}

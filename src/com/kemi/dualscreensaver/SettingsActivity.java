package com.kemi.dualscreensaver;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.util.ArrayList;

public final class SettingsActivity extends Activity {
    private static final ComponentName DREAM=new ComponentName(ScreensaverCatalog.SERVICE_PACKAGE,
        "com.kemi.dualscreensaver.ScreensaverDreamService");
    private final LinearLayout[] cards=new LinearLayout[ScreensaverCatalog.COUNT];
    private final ArrayList<Bitmap> images=new ArrayList<>();
    private TextView status;
    private Button enable,preview;
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private String text(String key) { return getString(getResources().getIdentifier(key,"string",getPackageName())); }
    private TextView label(String value,int size) {
        TextView v=new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(0xff1d1d1f);
        v.setTextDirection(View.TEXT_DIRECTION_LOCALE); v.setGravity(Gravity.START); return v;
    }
    private GradientDrawable background(boolean active) {
        GradientDrawable d=new GradientDrawable(); d.setColor(active?0xffedf5ff:Color.WHITE); d.setCornerRadius(dp(18));
        d.setStroke(dp(active?3:1),active?0xff0071e3:0xffdadade); return d;
    }
    private Button button(String key,Runnable action) {
        Button b=new Button(this); b.setText(text(key)); b.setAllCaps(false); b.setMinHeight(dp(48));
        b.setOnClickListener(v -> action.run()); return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(0xfff5f5f7);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(28),dp(12),dp(28),dp(12));
        page.addView(root,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(label("KEMI S1",14),new LinearLayout.LayoutParams(0,-2,1));
        top.addView(button("exit_button",() -> finishAndRemoveTask())); root.addView(top);
        TextView title=label(text("app_name"),32); title.setTypeface(null,Typeface.BOLD); root.addView(title);
        LinearLayout row=new LinearLayout(this); row.setPadding(0,dp(10),0,dp(8));
        for(int i:ScreensaverCatalog.activeIds()) {
            final int id=i; boolean available=ScreensaverCatalog.available(getAssets(),id);
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(12),dp(12),dp(12),dp(16));
            cards[i-1]=card;
            ImageView image=new ImageView(this); image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setBackgroundColor(0xffe5e5ea);
            try(InputStream in=getAssets().open("preview_"+ScreensaverCatalog.stem(i)+".jpg")) {
                Bitmap bitmap=BitmapFactory.decodeStream(in); if(bitmap!=null) { images.add(bitmap); image.setImageBitmap(bitmap); }
            } catch(Exception ignored) { image.setImageDrawable(null); }
            card.addView(image,new LinearLayout.LayoutParams(-1,0,1));
            String name=text(i==1?"silver_title":i==2?"douyin_title":"spring_title");
            TextView caption=label(name,15); caption.setPadding(0,dp(10),0,dp(8)); card.addView(caption);
            if(!available) card.addView(label(text("pending"),14));
            card.setContentDescription(name+(available?"":". "+text("pending")));
            card.setEnabled(available); card.setAlpha(available?1f:.58f);
            card.setBackground(background(false));
            card.setOnClickListener(v -> {
                if(ScreensaverCatalog.select(this,id)) { status.setText("");update(); }
                else status.setText(text("error"));
            });
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-1,1); params.setMarginEnd(dp(12)); row.addView(card,params);
        }
        root.addView(row,new LinearLayout.LayoutParams(-1,0,1));
        status=label(ScreensaverCatalog.complete(getAssets())?"":text("pending"),15); root.addView(status);
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        enable=button("enable",() -> { if(enableDream()) status.setText(text("enabled")); });
        preview=button("preview",() -> {
            if(!enableDream()) return;
            try {
                Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"dreams");
                Object manager=Class.forName("android.service.dreams.IDreamManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
                // Explicit component preview avoids vendor firmware choosing its bundled Rain dream.
                Class<?> api=Class.forName("android.service.dreams.IDreamManager");
                try { api.getMethod("testDream",ComponentName.class).invoke(manager,DREAM); }
                catch(NoSuchMethodException e) { api.getMethod("testDream",int.class,ComponentName.class).invoke(manager,android.os.Process.myUid()/100000,DREAM); }
                finishAndRemoveTask();
            } catch(Exception e) { android.util.Log.e("KemiScreensaverUI","Start dream failed",e); status.setText(text("error")); }
        });
        actions.addView(enable,new LinearLayout.LayoutParams(0,dp(52),1)); actions.addView(preview,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(actions); setContentView(page); update();
    }
    private void update() {
        int selected=ScreensaverCatalog.selected(this);
        for(int id:ScreensaverCatalog.activeIds()) {
            LinearLayout card=cards[id-1];card.setSelected(id==selected);card.setActivated(id==selected);
            card.setBackground(background(id==selected));
        }
        boolean ready=ScreensaverCatalog.complete(getAssets());
        enable.setEnabled(ready); preview.setEnabled(ready);
    }
    private boolean enableDream() {
        if(!ScreensaverCatalog.complete(getAssets())) { status.setText(text("pending")); return false; }
        // S1 firmware uses the default component for non-preview idle dreams.
        String[] keys={"screensaver_components","screensaver_default_component","screen_protection_plugged","screensaver_activate_on_sleep","screensaver_enabled"};
        String[] values={DREAM.flattenToString(),DREAM.flattenToString(),DREAM.flattenToString(),"1","1"};
        String[] old=new String[keys.length]; int changed=0;
        try {
            getPackageManager().getServiceInfo(DREAM,0);
            for(int i=0;i<keys.length;i++) old[i]=Settings.Secure.getString(getContentResolver(),keys[i]);
            for(int i=0;i<keys.length;i++) {
                if(!Settings.Secure.putString(getContentResolver(),keys[i],values[i])) throw new IllegalStateException("Setting rejected");
                changed=i+1;
            }
            return true;
        } catch(Exception e) {
            for(int i=changed-1;i>=0;i--) try { Settings.Secure.putString(getContentResolver(),keys[i],old[i]); } catch(Exception ignored) {}
            android.util.Log.e("KemiScreensaverUI","Enable dream failed",e); status.setText(text("error")); return false;
        }
    }
    @Override public void onDestroy() { for(Bitmap bitmap:images) bitmap.recycle(); images.clear(); super.onDestroy(); }
}

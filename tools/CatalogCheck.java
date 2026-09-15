import com.kemi.dualscreensaver.ScreensaverCatalog;
public final class CatalogCheck {
    public static void main(String[] args) {
        if(!java.util.Arrays.equals(ScreensaverCatalog.activeIds(),new int[]{1,3})) throw new AssertionError("Wrong test pool");
        if(ScreensaverCatalog.COUNT!=3) throw new AssertionError("Expected three independent contents");
        for(int id:new int[]{1,2,3}) if(!ScreensaverCatalog.valid(id)||!ScreensaverCatalog.isVideo(id)) throw new AssertionError("Missing video");
        for(int id:new int[]{Integer.MIN_VALUE,-1,0,4,31,32,33,Integer.MAX_VALUE}) {
            if(ScreensaverCatalog.valid(id)) throw new AssertionError("Wallpaper ID accepted");
            try { ScreensaverCatalog.stem(id); throw new AssertionError("Invalid asset accepted"); }
            catch(IllegalArgumentException expected) {}
        }
        System.out.println("PASS: exactly three independent contents; wallpaper IDs rejected");
    }
}

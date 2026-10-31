package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Ships the Apple-looking wallpaper and theme inside the same launcher APK. */
public final class AppleTheme {
    private static final int VERSION = 4;
    private AppleTheme() {}

    public static void ensureInstalled(Context c) {
        try {
            File folder = c.getExternalFilesDir(null);
            if (folder == null) return;
            SharedPreferences prefs = c.getSharedPreferences("cm2012_apple_asset_version", Context.MODE_PRIVATE);
            if (prefs.getInt("version", 0) == VERSION &&
                    new File(folder, "config.json").isFile() &&
                    new File(folder, "wallpaper.jpg").isFile()) return;
            if (!folder.exists() && !folder.mkdirs()) return;
            copy(c, folder, "cm2012_apple_config.json", "config.json");
            copy(c, folder, "cm2012_apple_wallpaper.jpg", "wallpaper.jpg");
            // Match upstream theme tool: a new revision invalidates saved layout overrides.
            File marker = new File(folder, "theme_revision");
            try (FileOutputStream o = new FileOutputStream(marker)) {
                o.write(("cm2012-apple-v" + VERSION).getBytes("UTF-8"));
            }
            prefs.edit().putInt("version", VERSION).apply();
        } catch (Exception e) {
            android.util.Log.w("CM2012AppleTheme", "Could not stage embedded theme", e);
        }
    }

    private static void copy(Context c, File folder, String asset, String name) throws Exception {
        File tmp = new File(folder, name + ".cm2012.tmp");
        try (InputStream src = c.getAssets().open(asset);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = src.read(buf)) != -1) out.write(buf, 0, n);
            out.getFD().sync();
        }
        File dst = new File(folder, name);
        if (dst.exists() && !dst.delete()) throw new java.io.IOException("Cannot replace " + name);
        if (!tmp.renameTo(dst)) throw new java.io.IOException("Cannot stage " + name);
    }
}

package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Copies the six built-in CM2012 wallpapers into the *existing* CleanYourTV
 * theme collection. CleanYourTV owns the selection UI, rotation, and rendering.
 * No background service, external downloader or dangerous permission needed.
 */
public final class AppleWallpaperPack {
    private static final String TAG = "CM2012Wallpaper";
    private static final int PACK_VERSION = 53;
    private static final String[] IMAGES = {
            "cm2012_apple_01_blue.jpg", "cm2012_apple_02_purple.jpg",
            "cm2012_apple_03_gold.jpg", "cm2012_apple_04_teal.jpg",
            "cm2012_apple_05_black.jpg", "cm2012_apple_06_emerald.jpg"
    };

    private AppleWallpaperPack() { }

    public static void ensureInstalled(Context context) {
        try {
            File root = context.getExternalFilesDir(null);
            if (root == null) {
                Log.w(TAG, "external app files unavailable; built-in wallpaper pack not installed");
                return;
            }
            File folder = new File(root, "wallpapers");
            if (!folder.isDirectory() && !folder.mkdirs()) {
                Log.w(TAG, "cannot create wallpaper directory");
                return;
            }
            SharedPreferences prefs = context.getSharedPreferences("cm2012_v53_wallpaper_pack", 0);
            boolean upgrading = prefs.getInt("pack_version", 0) < PACK_VERSION;
            boolean allGood = true;
            for (String image : IMAGES) {
                File dest = new File(folder, image);
                if (upgrading || !dest.isFile() || dest.length() == 0) {
                    allGood &= copyAsset(context, "cm2012_wallpapers/" + image, dest);
                }
            }
            // The upstream wallpaper picker does not need captions.txt; avoid
            // overwriting captions a user has already edited.
            File captions = new File(folder, "captions.txt");
            if (!captions.isFile()) {
                copyAsset(context, "cm2012_wallpapers/captions.txt", captions);
            }
            if (allGood && upgrading) prefs.edit().putInt("pack_version", PACK_VERSION).apply();
            // V4 wrote a theme config.json already. Only introduce missing V5.3
            // keys; preserve pre-existing preferences and other theme settings.
            enableCollectionDefaults(root);
        } catch (Exception e) {
            Log.w(TAG, "nonfatal wallpaper pack init failure", e);
        }
    }

    private static boolean copyAsset(Context ctx, String asset, File destination) {
        File temp = new File(destination.getParentFile(), destination.getName() + ".part");
        try (InputStream in = ctx.getAssets().open(asset);
             FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.getFD().sync();
        } catch (Exception e) {
            temp.delete();
            Log.w(TAG, "asset copy failed: " + asset, e);
            return false;
        }
        File old = new File(destination.getParentFile(), destination.getName() + ".bak");
        if (old.exists() && !old.delete()) { temp.delete(); return false; }
        boolean hadOld = destination.exists();
        if (hadOld && !destination.renameTo(old)) { temp.delete(); return false; }
        if (!temp.renameTo(destination)) {
            if (hadOld) old.renameTo(destination);
            temp.delete();
            return false;
        }
        if (hadOld) old.delete();
        return true;
    }

    private static void enableCollectionDefaults(File root) {
        File config = new File(root, "config.json");
        if (!config.isFile()) return;
        try {
            byte[] buffer = new byte[(int) Math.min(config.length(), 256 * 1024)];
            if (buffer.length == 0 || config.length() > buffer.length) return;
            int total = 0;
            try (FileInputStream input = new FileInputStream(config)) {
                while (total < buffer.length) {
                    int read = input.read(buffer, total, buffer.length - total);
                    if (read < 0) break;
                    total += read;
                }
            }
            JSONObject json = new JSONObject(new String(buffer, 0, total, StandardCharsets.UTF_8));
            boolean modified = false;
            if (!json.has("wallpaper_rotation_minutes")) {
                json.put("wallpaper_rotation_minutes", 1440); // every 24 hours
                modified = true;
            }
            if (!json.has("wallpaper_order")) {
                json.put("wallpaper_order", "sequential");
                modified = true;
            }
            if (!modified) return;
            File tmp = new File(root, "config.json.v53.tmp");
            try (FileOutputStream output = new FileOutputStream(tmp)) {
                output.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            // Do not risk losing the old theme if an atomic replace fails.
            File backup = new File(root, "config.json.v53.bak");
            if (backup.exists() && !backup.delete()) return;
            if (!config.renameTo(backup)) return;
            if (!tmp.renameTo(config)) {
                backup.renameTo(config);
                return;
            }
            backup.delete();
        } catch (Exception e) {
            Log.w(TAG, "cannot update existing theme settings; wallpaper files still available", e);
        }
    }
}

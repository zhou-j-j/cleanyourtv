package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * V5.5 launcher-only icon overrides; never changes the target app's APK.
 * Stores a single package -> 'builtin:<id>' or 'file:<hash>' mapping and a
 * small downsampled PNG inside this launcher's private app storage.
 */
public final class AppleIconManager55 {
    private static final String PREF = "cm2012_v55_icons";
    private static final String TAG = "AppleIcon55";
    // Cached decoded user images: prevents repeated decode during TV focus/scroll.
    private static final android.util.LruCache<String, Bitmap> USER_BITMAPS =
        new android.util.LruCache<String, Bitmap>(5 * 1024 * 1024) {
            @Override protected int sizeOf(String key, Bitmap value) {
                return value == null ? 0 : value.getByteCount();
            }
        };
    private static final WeakHashMap<ViewTreeObserver, Boolean> TRACKED = new WeakHashMap<>();
    private static final int[] BACKGROUNDS = {
        0xff2765bf, 0xff7148ba, 0xff158d80, 0xffd68534,
        0xff406074, 0xffa64863, 0xff394e9c, 0xff398c52,
        0xff245c85, 0xff4f647b, 0xff9347b7, 0xff3b4a64
    };
    private static final String[] GLYPHS = {
        "▶", "♪", "✦", "✿", "▣", "✉", "☼", "★", "⊕", "◈", "●", "⌂"
    };
    public static final String[] NAMES = {
        "视频", "音乐", "语音", "设置", "文件", "邮件", "天气", "收藏", "工具", "浏览器", "应用", "主页"
    };
    private AppleIconManager55() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    public static boolean hasAny(Context context) { return !prefs(context).getAll().isEmpty(); }
    public static boolean hasOverride(Context c, String pkg) {
        return pkg != null && !prefs(c).getString(pkg, "").isEmpty();
    }
    public static void useBuiltin(Context c, String pkg, int index) {
        if (pkg == null || pkg.isEmpty() || index < 0 || index >= NAMES.length) return;
        prefs(c).edit().putString(pkg, "builtin:" + index).apply();
    }
    public static void reset(Context c, String pkg) {
        if (pkg != null) prefs(c).edit().remove(pkg).apply();
    }
    public static boolean saveFromUri(Context c, String pkg, Uri uri) {
        if (pkg == null || pkg.isEmpty() || uri == null) return false;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) return false;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > 10000
                    || bounds.outHeight > 10000) return false;
            BitmapFactory.Options decode = new BitmapFactory.Options();
            int larger = Math.max(bounds.outWidth, bounds.outHeight);
            decode.inSampleSize = 1;
            while (larger / decode.inSampleSize > 512) decode.inSampleSize *= 2;
            Bitmap raw;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) return false;
                raw = BitmapFactory.decodeStream(in, null, decode);
            }
            if (raw == null) return false;
            Bitmap scaled = Bitmap.createScaledBitmap(raw, 256, 256, true);
            if (scaled != raw) raw.recycle();
            File dir = new File(c.getFilesDir(), "apple_icons_v55");
            if (!dir.exists() && !dir.mkdirs()) return false;
            String name = digest(pkg) + ".png";
            File tmp = new File(dir, name + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) return false;
                out.flush();
            } finally {
                scaled.recycle();
            }
            File result = new File(dir, name);
            if (!tmp.renameTo(result)) {
                if (result.exists() && !result.delete()) return false;
                if (!tmp.renameTo(result)) return false;
            }
            USER_BITMAPS.remove(name);
            prefs(c).edit().putString(pkg, "file:" + name).apply();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Could not import user icon", e);
            return false;
        }
    }
    private static String digest(String input) throws Exception {
        byte[] sha = MessageDigest.getInstance("SHA-256").digest(input.getBytes("UTF-8"));
        StringBuilder s = new StringBuilder();
        for (byte b : sha) s.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return s.substring(0, 28);
    }
    public static Drawable resolve(Context c, String pkg, Drawable original) {
        if (!hasOverride(c, pkg)) return original;
        String value = prefs(c).getString(pkg, "");
        try {
            if (value.startsWith("builtin:")) {
                int index = Integer.parseInt(value.substring(8));
                if (index >= 0 && index < NAMES.length) return new SquircleDrawable(index);
            } else if (value.startsWith("file:")) {
                String name = value.substring(5);
                if (!name.matches("[0-9a-f]{28}\\.png")) return original;
                File file = new File(new File(c.getFilesDir(), "apple_icons_v55"), name);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                Bitmap bitmap = USER_BITMAPS.get(name);
                if (bitmap == null) {
                    bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                    if (bitmap != null) USER_BITMAPS.put(name, bitmap);
                }
                if (bitmap != null) return new RoundedBitmapDrawable(bitmap);
            }
        } catch (Exception e) { Log.w(TAG, "User icon could not be loaded", e); }
        return original;
    }
    public static Drawable preview(int index) { return new SquircleDrawable(index); }

    /** Bind after the original RecyclerView item binding. Unknown tile types remain unchanged. */
    public static void afterBind(RecyclerView.Adapter<?> adapter, RecyclerView.ViewHolder holder, int index) {
        if (holder == null || adapter == null || index < 0) return;
        Context c = holder.itemView.getContext();
        if (!hasAny(c)) return; // Almost zero cost on untouched installations.
        try {
            String pkg = packageFromAdapter(adapter, index);
            if (!hasOverride(c, pkg)) return;
            ImageView image = findIcon(holder.itemView);
            if (image == null) return;
            Drawable replaced = resolve(c, pkg, image.getDrawable());
            if (replaced != null) {
                image.setImageTintList(null);
                image.setImageDrawable(replaced);
            }
        } catch (Exception e) { Log.w(TAG, "Tile icon override skipped", e); }
    }
    /** Package name for the selected Home tile, if it represents an installed app. */
    public static String packageAt(RecyclerView.Adapter<?> adapter, int index) {
        if (adapter == null || index < 0) return null;
        try { return packageFromAdapter(adapter, index); }
        catch (Exception ignored) { return null; }
    }
    private static String packageFromAdapter(Object adapter, int index) throws Exception {
        List<Field> fields = new ArrayList<>();
        Class<?> t = adapter.getClass();
        while (t != null && t != Object.class) {
            Collections.addAll(fields, t.getDeclaredFields());
            t = t.getSuperclass();
        }
        Collections.sort(fields, Comparator.comparingInt(f -> {
            String s = f.getName().toLowerCase(java.util.Locale.ROOT);
            return (s.contains("visible") || s.contains("filtered") ? -4 : 0)
                    + (s.contains("apps") || s.contains("cards") || s.contains("items") ? -2 : 0);
        }));
        for (Field f : fields) {
            if (!List.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            Object val = f.get(adapter);
            if (!(val instanceof List)) continue;
            List<?> items = (List<?>) val;
            if (index >= items.size()) continue;
            String pkg = packageFromModel(items.get(index), 0);
            if (pkg != null) return pkg;
        }
        // AppLibraryDialog.Catalog is a non-static inner adapter. Its app list
        // belongs to the outer AppLibraryDialog (this$0), not to Catalog.
        for (Field f : fields) {
            if (!f.getName().equals("this$0")) continue;
            f.setAccessible(true);
            Object parent = f.get(adapter);
            if (parent != null && parent != adapter) {
                String pkg = packageFromAdapter(parent, index);
                if (pkg != null) return pkg;
            }
        }
        return null;
    }
    private static String packageFromModel(Object item, int depth) throws Exception {
        if (item == null || depth > 2) return null;
        if (item instanceof ResolveInfo) {
            ActivityInfo ai = ((ResolveInfo) item).activityInfo;
            return ai == null ? null : ai.packageName;
        }
        if (item instanceof PackageItemInfo) return ((PackageItemInfo) item).packageName;
        if (item instanceof String) {
            String value = (String) item;
            return value.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+") ? value : null;
        }
        for (String name : new String[] {"packageName", "pkg", "packageId", "packageKey", "key"}) {
            try {
                Field f = item.getClass().getDeclaredField(name);
                f.setAccessible(true);
                String pkg = packageFromModel(f.get(item), depth + 1);
                if (pkg != null) return pkg;
            } catch (NoSuchFieldException ignored) {}
        }
        for (String name : new String[] {"activityInfo", "app", "resolveInfo", "info", "intent"}) {
            try {
                Field f = item.getClass().getDeclaredField(name);
                f.setAccessible(true);
                Object child = f.get(item);
                if (child instanceof android.content.Intent) {
                    android.content.Intent i = (android.content.Intent) child;
                    if (i.getComponent() != null) return i.getComponent().getPackageName();
                    return i.getPackage();
                }
                String pkg = packageFromModel(child, depth + 1);
                if (pkg != null) return pkg;
            } catch (NoSuchFieldException ignored) {}
        }
        for (String name : new String[] {"getPackageName", "packageName"}) {
            try {
                Method m = item.getClass().getMethod(name);
                if (m.getParameterTypes().length == 0) {
                    String pkg = packageFromModel(m.invoke(item), depth + 1);
                    if (pkg != null) return pkg;
                }
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }
    private static ImageView findIcon(View root) {
        List<ImageView> all = new ArrayList<>();
        collect(root, all);
        ImageView best = null;
        int score = -99999;
        for (ImageView i : all) {
            String name = "";
            try { if (i.getId() != View.NO_ID) name = i.getResources().getResourceEntryName(i.getId()); }
            catch (Exception ignored) {}
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            int next = (lower.contains("icon") ? 10000 : 0)
                + (lower.contains("art") || lower.contains("banner") ? 1000 : 0)
                + Math.min(500, Math.max(i.getWidth(), i.getHeight()));
            if (i.getVisibility() == View.VISIBLE && next > score) {
                score = next; best = i;
            }
        }
        return best;
    }
    private static void collect(View v, List<ImageView> results) {
        if (v instanceof ImageView) results.add((ImageView) v);
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int n = 0; n < group.getChildCount(); n++) collect(group.getChildAt(n), results);
        }
    }
    /** Adds optional subtle focus lift without replacing any existing OnFocusChangeListener. */
    public static void addFocusTracking(View root) {
        if (root == null) return;
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (!observer.isAlive() || TRACKED.containsKey(observer)) return;
        TRACKED.put(observer, true);
        observer.addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (oldFocus != null && isTile(oldFocus)) lift(oldFocus, false);
            if (newFocus != null && isTile(newFocus)) lift(newFocus, true);
        });
    }
    private static boolean isTile(View v) {
        try { return v.getId() == R.id.card; }
        catch (Exception e) { return false; }
    }
    private static void lift(View v, boolean yes) {
        v.animate().scaleX(yes ? 1.07f : 1f).scaleY(yes ? 1.07f : 1f)
            .setDuration(yes ? 170L : 130L).start();
    }
    private static abstract class IconDrawable extends Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void setAlpha(int a) { p.setAlpha(a); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { p.setColorFilter(f); invalidateSelf(); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
    private static final class SquircleDrawable extends IconDrawable {
        final int type;
        SquircleDrawable(int type) { this.type = type; }
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height());
            if (s <= 0) return;
            int save = canvas.save();
            canvas.translate(b.left + (b.width() - s) / 2f, b.top + (b.height() - s) / 2f);
            p.setStyle(Paint.Style.FILL); p.setColor(BACKGROUNDS[type]);
            canvas.drawRoundRect(new RectF(0, 0, s, s), s * .23f, s * .23f, p);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            p.setTextSize(s * .49f); p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Color.WHITE);
            String txt = GLYPHS[type];
            Paint.FontMetrics fm = p.getFontMetrics();
            canvas.drawText(txt, s / 2f, s / 2f - (fm.ascent + fm.descent) / 2f, p);
            canvas.restoreToCount(save);
        }
        @Override public int getIntrinsicWidth() { return 128; }
        @Override public int getIntrinsicHeight() { return 128; }
    }
    private static final class RoundedBitmapDrawable extends IconDrawable {
        final Bitmap bmp;
        RoundedBitmapDrawable(Bitmap bmp) { this.bmp = bmp; }
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            RectF dest = new RectF(b);
            Path path = new Path();
            path.addRoundRect(dest, Math.min(b.width(), b.height()) * .23f,
                    Math.min(b.width(), b.height()) * .23f, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(path);
            canvas.drawBitmap(bmp, null, dest, p);
            canvas.restoreToCount(save);
        }
        @Override public int getIntrinsicWidth() { return 128; }
        @Override public int getIntrinsicHeight() { return 128; }
    }
}

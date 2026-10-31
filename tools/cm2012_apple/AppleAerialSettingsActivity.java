package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

/**
 * CM201-2 Apple V5.1 / Android 9: remote-friendly, explicit source selection.
 * Compatible with AppleAerialDream reading PREFS + VIDEO_URI.
 * Uncompiled source patch: keep the existing V5 until the replacement is built/tested.
 */
public class AppleAerialSettingsActivity extends Activity {
    private static final String PREFS = "cm2012_apple_aerial";
    private static final String VIDEO_URI = "video_uri";
    private static final String SOURCE_MODE = "source_mode";
    private static final String PLAYLIST = "online_playlist_json";
    private static final String LAST_INDEX = "online_playlist_last_index";
    private static final int MAX_VIDEOS = 30;
    private static final int REQUEST_LOCAL = 2105;
    private static final int WHITE = 0xffffffff;
    private static final int MUTED = 0xffd8cede;
    private static final int GREEN = 0xff94f0b6;
    private static final int GOLD = 0xffffd88e;
    private static final int BG = 0xff241a2c;
    private static final int FOCUS_BG = 0xff624279;

    private LinearLayout content;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(0xff130d19);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(52), dp(30), dp(52), dp(40));
        scroller.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroller);
        render();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }
    private String savedUri() {
        String value = prefs().getString(VIDEO_URI, "");
        return value == null ? "" : value.trim();
    }
    private String effectiveMode() {
        String uri = savedUri();
        if (uri.length() == 0) return "none";
        if (uri.startsWith("https://") || uri.startsWith("http://")) return "online";
        return "local";
    }

    private GradientDrawable shape(boolean focused, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(focused ? FOCUS_BG : BG);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(focused ? 4 : selected ? 2 : 1),
                focused ? WHITE : selected ? GREEN : 0xff645369);
        return drawable;
    }

    private void decorateFocusable(final TextView view, final boolean selected) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setClickable(true);
        view.setBackground(shape(false, selected));
        view.setTextColor(selected ? GREEN : WHITE);
        view.setOnFocusChangeListener((v, hasFocus) -> {
            view.setBackground(shape(hasFocus, selected));
            view.setTextColor(hasFocus ? WHITE : selected ? GREEN : WHITE);
            view.setScaleX(hasFocus ? 1.015f : 1f);
            view.setScaleY(hasFocus ? 1.015f : 1f);
        });
    }

    private TextView text(String value, int size, int color) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(color);
        label.setTextSize(size);
        label.setPadding(0, dp(7), 0, dp(7));
        content.addView(label, new LinearLayout.LayoutParams(-1, -2));
        return label;
    }

    private TextView option(String caption, boolean selected, View.OnClickListener handler) {
        TextView view = new TextView(this);
        view.setText(caption);
        view.setTextSize(23);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(22), dp(16), dp(22), dp(16));
        decorateFocusable(view, selected);
        view.setOnClickListener(handler);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        content.addView(view, lp);
        return view;
    }

    private void render() {
        content.removeAllViews();
        text("航拍屏保 · 视频来源", 31, WHITE);
        text("白色粗边框＝遥控器当前焦点   绿色边框／已启用＝已保存的视频来源", 17, MUTED);
        String mode = effectiveMode();
        String uri = savedUri();
        String source = "none".equals(mode) ? "未设置视频" :
                "local".equals(mode) ? "本地 MP4（已启用）" : "在线 MP4（已启用）";
        text("当前模式：" + source, 24, "none".equals(mode) ? GOLD : GREEN);
        if (uri.length() == 0) {
            text("没有保存视频地址，屏保会显示日期／渐变背景。请在下面选择本地文件或保存在线直链。", 17, GOLD);
        } else {
            text("已保存视频地址：\n" + uri, 15, MUTED);
        }

        TextView local = option("本地 MP4" + ("local".equals(mode) ? "    ✓ 已启用" : "    · 选择视频"),
                "local".equals(mode), v -> chooseLocal());
        option("在线 MP4" + ("online".equals(mode) ? "    ✓ 已启用" : "    · 输入网址"),
                "online".equals(mode), v -> showOnlineEditor());
        option("管理在线视频列表（" + loadPlaylist().size() + " 条）",
                false, v -> showPlaylistManager());
        option("▶ 预览当前屏保", false, v -> preview());
        option("清除视频设置（需要再次确认）", false, v -> confirmClear());
        option("返回", false, v -> finish());
        text("选择本地视频后会立即保存文件授权；在线模式需点击“确认保存”。只有保存成功才切换来源。", 16, MUTED);
        local.post(local::requestFocus);
    }

    private boolean save(String uri, String mode) {
        boolean ok = prefs().edit().putString(VIDEO_URI, uri)
                .putString(SOURCE_MODE, mode).commit();
        ok = ok && uri.equals(savedUri());
        Toast.makeText(this, ok ? "保存成功：" + ("local".equals(mode) ? "本地 MP4" :
                "online".equals(mode) ? "在线 MP4" : "已清除") :
                "保存失败：原视频设置未改变", Toast.LENGTH_LONG).show();
        if (ok) render();
        return ok;
    }

    private void chooseLocal() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, REQUEST_LOCAL); }
        catch (Exception e) {
            Toast.makeText(this, "文件选择器打不开：请检查系统 DocumentsUI", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != REQUEST_LOCAL || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            save(uri.toString(), "local");
        } catch (SecurityException denied) {
            // Some customized Android 9 pickers return a readable URI but don't permit
            // persistable grants. Copy the chosen file into app-private storage instead.
            copyLocalIntoApp(uri);
        }
    }

    private void copyLocalIntoApp(Uri original) {
        File target = new File(getFilesDir(), "aerial_selected.mp4");
        File tmp = new File(getFilesDir(), "aerial_selected.tmp");
        try (InputStream in = getContentResolver().openInputStream(original);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new IllegalStateException("无法打开视频内容");
            byte[] chunk = new byte[65536];
            int read;
            while ((read = in.read(chunk)) != -1) out.write(chunk, 0, read);
            out.flush();
            if (!tmp.renameTo(target)) throw new IllegalStateException("本地缓存文件替换失败");
            save(Uri.fromFile(target).toString(), "local");
            Toast.makeText(this, "系统不支持长期授权，已存入应用专用目录", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            tmp.delete();
            Toast.makeText(this, "选择本地文件失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // V5.2.1: multi-URL video list; legacy single video_uri auto-migrates in memory.
    private List<String> loadPlaylist() {
        List<String> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs().getString(PLAYLIST, "[]"));
            for (int i = 0; i < a.length(); i++) {
                String u = a.optString(i, "").trim();
                if (validOnline(u) && !list.contains(u)) list.add(u);
            }
        } catch (Exception ignored) {}
        String old = savedUri();
        if ("online".equals(effectiveMode()) && validOnline(old)
                && !list.contains(old) && list.isEmpty()) list.add(old);
        return list;
    }

    private boolean validOnline(String input) {
        if (input == null || !input.startsWith("https://")) return false;
        Uri u = Uri.parse(input);
        return u.getHost() != null && u.getHost().length() > 0;
    }

    private boolean savePlaylist(List<String> list, boolean enableOnline) {
        JSONArray a = new JSONArray();
        for (String u : list) a.put(u);
        SharedPreferences.Editor editor = prefs().edit()
                .putString(PLAYLIST, a.toString())
                .putInt(LAST_INDEX, -1);
        if (enableOnline) {
            editor.putString(VIDEO_URI, list.isEmpty() ? "" : list.get(0))
                    .putString(SOURCE_MODE, list.isEmpty() ? "none" : "online");
        }
        boolean ok = editor.commit();
        Toast.makeText(this, ok ? "在线视频列表已保存（" + list.size() + " 条）"
                : "视频列表保存失败", Toast.LENGTH_LONG).show();
        return ok;
    }

    private void showPlaylistManager() {
        content.removeAllViews();
        text("管理在线视频 · 自动轮播", 29, WHITE);
        text("短按确定键管理单条视频；白色粗边框是焦点。"
                + " 播放失败会自动跳过；全部失败后稍后重试。", 17, MUTED);
        List<String> list = loadPlaylist();
        text("已保存：" + list.size() + " / " + MAX_VIDEOS + " 条", 22, GREEN);
        TextView first = option("＋ 添加在线 MP4 地址", false, v -> showOnlineEditor(-1));
        option("▶ 启用在线轮播", "online".equals(effectiveMode()), v -> {
            if (list.isEmpty()) {
                Toast.makeText(this, "请先添加至少一条视频", Toast.LENGTH_LONG).show();
            } else if (savePlaylist(list, true)) render();
        });
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            String u = list.get(i);
            option((i + 1) + ". " + u,
                    "online".equals(effectiveMode()) && u.equals(savedUri()),
                    v -> showVideoActions(index));
        }
        option("返回屏保设置", false, v -> render());
        first.post(first::requestFocus);
    }

    private void showVideoActions(int index) {
        List<String> list = loadPlaylist();
        if (index < 0 || index >= list.size()) { showPlaylistManager(); return; }
        final String[] actions = {"设为首条并启用在线轮播", "修改地址", "上移", "下移", "删除"};
        new AlertDialog.Builder(this)
                .setTitle("视频 " + (index + 1))
                .setItems(actions, (dialog, which) -> {
                    List<String> next = loadPlaylist();
                    if (index >= next.size()) return;
                    if (which == 0) {
                        String chosen = next.remove(index);
                        next.add(0, chosen);
                        if (savePlaylist(next, true)) render();
                    } else if (which == 1) {
                        showOnlineEditor(index);
                    } else if (which == 2 && index > 0) {
                        java.util.Collections.swap(next, index, index - 1);
                        if (savePlaylist(next, "online".equals(effectiveMode()))) showPlaylistManager();
                    } else if (which == 3 && index < next.size() - 1) {
                        java.util.Collections.swap(next, index, index + 1);
                        if (savePlaylist(next, "online".equals(effectiveMode()))) showPlaylistManager();
                    } else if (which == 4) {
                        new AlertDialog.Builder(this)
                                .setTitle("确认删除这条视频？")
                                .setMessage(next.get(index))
                                .setNegativeButton("取消", null)
                                .setPositiveButton("删除", (d, w) -> {
                                    List<String> removal = loadPlaylist();
                                    if (index < removal.size()) {
                                        removal.remove(index);
                                        if (savePlaylist(removal, "online".equals(effectiveMode())))
                                            showPlaylistManager();
                                    }
                                }).show();
                    } else {
                        showPlaylistManager();
                    }
                })
                .setNegativeButton("返回", (d, which) -> showPlaylistManager())
                .show();
    }

    private void showOnlineEditor() { showOnlineEditor(-1); }

    private void showOnlineEditor(int editIndex) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(20), dp(24), dp(20));
        body.setBackgroundColor(0xff2b2037);
        TextView title = new TextView(this);
        title.setText(editIndex >= 0 ? "修改在线 MP4 地址" : "添加在线 MP4 地址（HTTPS）");
        title.setTextColor(WHITE);
        title.setTextSize(23);
        body.addView(title);
        EditText field = new EditText(this);
        field.setTextColor(WHITE);
        field.setHintTextColor(MUTED);
        field.setTextSize(18);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setHint("https://example.com/aerial.mp4");
        List<String> initialList = loadPlaylist();
        if (editIndex >= 0 && editIndex < initialList.size())
            field.setText(initialList.get(editIndex));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, dp(65));
        fieldParams.topMargin = dp(15);
        body.addView(field, fieldParams);
        TextView tip = new TextView(this);
        tip.setText("必须是能直接播放的 MP4 视频地址。输入完成后，用方向键选中“确认保存”。");
        tip.setTextColor(MUTED);
        tip.setTextSize(16);
        tip.setPadding(0, dp(10), 0, dp(15));
        body.addView(tip);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = new Button(this);
        cancel.setText("取消");
        Button save = new Button(this);
        save.setText("确认保存");
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(65), 1));
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(65), 1));
        body.addView(actions);
        decorateFocusable(cancel, false);
        decorateFocusable(save, false);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(body).create();
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String input = field.getText().toString().trim();
            Uri parsed = Uri.parse(input);
            if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null
                    || parsed.getHost().length() == 0) {
                field.setError("请输入完整的 HTTPS MP4 直链");
                Toast.makeText(this, "未保存：请输入有效 HTTPS 网址", Toast.LENGTH_LONG).show();
                return;
            }
            List<String> next = loadPlaylist();
            if (editIndex >= 0 && editIndex < next.size()) next.set(editIndex, input);
            else if (!next.contains(input)) {
                if (next.size() >= MAX_VIDEOS) {
                    Toast.makeText(this, "最多保存 " + MAX_VIDEOS + " 条视频", Toast.LENGTH_LONG).show();
                    return;
                }
                next.add(input);
            }
            List<String> unique = new ArrayList<>();
            for (String u : next) if (!unique.contains(u)) unique.add(u);
            if (savePlaylist(unique, true)) {
                dialog.dismiss();
                showPlaylistManager();
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(dp(840), WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        field.requestFocus();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("确定清除视频？")
                .setMessage("清除后将不再播放当前本地或在线视频，只显示屏保背景／日期。")
                .setNegativeButton("取消", (d, which) -> d.dismiss())
                .setPositiveButton("清除", (d, which) -> save("", "none"))
                .show();
    }

    private void preview() {
        if (savedUri().length() == 0) {
            Toast.makeText(this, "尚未选择视频，请先保存本地或在线来源", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent intent = new Intent();
            intent.setClassName("com.android.systemui", "com.android.systemui.Somnambulator");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "预览失败，可等屏保超时后测试", Toast.LENGTH_LONG).show();
        }
    }
}

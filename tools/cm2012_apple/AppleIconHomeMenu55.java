package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;

/**
 * Home tile hold-OK entry point. The original Home tile management remains
 * available as a second action instead of being overwritten or lost.
 */
public final class AppleIconHomeMenu55 {
    private AppleIconHomeMenu55() {}
    public static void show(Activity activity, String pkg, Runnable nativeMenu) {
        if (activity == null || activity.isFinishing() || pkg == null || pkg.isEmpty()) {
            if (nativeMenu != null) nativeMenu.run();
            return;
        }
        new AlertDialog.Builder(activity)
            .setTitle("应用操作")
            .setItems(new String[] {"更改图标", "原有管理菜单", "取消"}, (dlg, which) -> {
                if (which == 0) AppleIconPicker55.open(activity, pkg);
                else if (which == 1 && nativeMenu != null) nativeMenu.run();
            })
            .show();
    }
}

package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.widget.Toast;

/** Opens the actual Bluetooth page when ROM exposes it; never toggles the adapter. */
public final class AppleBluetoothSettings55 {
    private AppleBluetoothSettings55() {}

    public static void open(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        // Some vendor ROMs register both generic Settings and the dedicated
        // BluetoothSettings activity. Try the Settings package first.
        Intent standard = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        Intent preferred = new Intent(standard).setPackage("com.android.settings");
        if (startIfAvailable(activity, preferred)) return;
        if (startIfAvailable(activity, standard)) return;

        // An explicit class is tried only if this known AOSP activity exists.
        // Vendor ROMs may not have it, in which case we do not assume its name.
        Intent explicit = new Intent().setClassName("com.android.settings",
                "com.android.settings.Settings$BluetoothSettingsActivity");
        if (startIfAvailable(activity, explicit)) return;

        Toast.makeText(activity, "固件没有提供独立蓝牙设置页，改为打开系统设置", Toast.LENGTH_LONG).show();
        if (startIfAvailable(activity, new Intent(Settings.ACTION_WIRELESS_SETTINGS))) return;
        startIfAvailable(activity, new Intent(Settings.ACTION_SETTINGS));
    }
    private static boolean startIfAvailable(Activity activity, Intent intent) {
        try {
            PackageManager pm = activity.getPackageManager();
            if (intent.resolveActivity(pm) == null) return false;
            activity.startActivity(intent);
            return true;
        } catch (Exception ignored) { return false; }
    }
}

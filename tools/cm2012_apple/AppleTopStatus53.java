package com.gathin.tvhome;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * CM2012 V5.3 header. Reuses the already-visible network pill (click handler
 * remains unchanged) and inserts a separate Bluetooth icon alongside it.
 * No new top-level pill is added if the V4 header no longer matches.
 */
public final class AppleTopStatus53 {
    private static final String TAG = "CM2012Status53";
    private AppleTopStatus53() { }

    public static void attach(Activity activity, LinearLayout header, TextView stockNetwork) {
        if (activity == null || header == null || stockNetwork == null) return;
        header.post(new Controller(activity, header, stockNetwork));
    }

    private static final class Controller implements Runnable, View.OnAttachStateChangeListener {
        private final Activity activity;
        private final LinearLayout header;
        private final TextView original;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private TextView network;
        private TextView bluetooth;
        private boolean detached;

        Controller(Activity a, LinearLayout h, TextView n) {
            activity = a; header = h; original = n;
            header.addOnAttachStateChangeListener(this);
        }

        @Override public void onViewAttachedToWindow(View v) {
            detached = false;
            handler.removeCallbacks(this);
            handler.post(this);
        }
        @Override public void onViewDetachedFromWindow(View v) {
            detached = true;
            handler.removeCallbacks(this);
            header.removeOnAttachStateChangeListener(this);
        }
        @Override public void run() {
            if (detached || activity.isFinishing()) return;
            if (!header.isAttachedToWindow()) { handler.postDelayed(this, 1200L); return; }
            try {
                if (network == null || !network.isShown()) network = findNetwork(header);
                if (network == null && original.isShown()) network = original;
                if (network != null) {
                    drawNetwork(network);
                    ensureBluetoothNextTo(network);
                    if (bluetooth != null) drawBluetooth(bluetooth);
                }
            } catch (Exception e) {
                Log.w(TAG, "header refresh failed", e);
            }
            handler.postDelayed(this, 3000L);
        }

        private TextView findNetwork(View view) {
            if (view instanceof TextView && view != bluetooth) {
                TextView t = (TextView) view;
                String value = String.valueOf(t.getText()) + " " + String.valueOf(t.getContentDescription());
                String label = value.toLowerCase(Locale.ROOT);
                if (t.isShown() && (t == original || label.contains("wifi") ||
                        label.contains("wi-fi") || label.contains("网络") ||
                        label.contains("有线") || label.contains("ethernet"))) return t;
            }
            if (view instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) view;
                for (int i = 0; i < g.getChildCount(); i++) {
                    TextView found = findNetwork(g.getChildAt(i));
                    if (found != null) return found;
                }
            }
            return null;
        }

        private void drawNetwork(TextView target) {
            int type = NetworkStatusDrawable.DISCONNECTED;
            int level = 0;
            boolean ethernetUsable = false;
            ConnectivityManager cm = (ConnectivityManager)
                    activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo active = cm == null ? null : cm.getActiveNetworkInfo();
            NetworkInfo eth = cm == null ? null : cm.getNetworkInfo(ConnectivityManager.TYPE_ETHERNET);
            if (active != null && active.isConnected() && active.getType() == ConnectivityManager.TYPE_WIFI) {
                type = NetworkStatusDrawable.WIFI;
                WifiManager wifi = (WifiManager) activity.getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
                if (info != null && info.getRssi() > -127 && info.getRssi() < 0)
                    level = WifiManager.calculateSignalLevel(info.getRssi(), 5);
            } else if ((active != null && active.getType() == ConnectivityManager.TYPE_ETHERNET)
                    || eth != null) {
                type = NetworkStatusDrawable.ETHERNET;
                boolean linked = eth != null && eth.isConnected();
                if (!linked && active != null && active.getType() == ConnectivityManager.TYPE_ETHERNET)
                    linked = active.isConnected();
                if (linked) {
                    Network network = cm == null ? null : cm.getActiveNetwork();
                    NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
                    ethernetUsable = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                            && (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            || (active != null && active.isConnected()
                            && active.getType() == ConnectivityManager.TYPE_ETHERNET
                            && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)));
                }
            }
            target.setText(""); // Replace the literal WIFI label; keep original click action.
            target.setGravity(Gravity.CENTER);
            target.setCompoundDrawablePadding(0);
            target.setCompoundDrawableTintList((ColorStateList) null);
            target.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    new NetworkStatusDrawable(activity.getResources().getDisplayMetrics().density,
                            type, level, ethernetUsable), null, null, null);
            String text = type == NetworkStatusDrawable.ETHERNET
                    ? (ethernetUsable ? "有线网络正常" : "有线网络未连通")
                    : type == NetworkStatusDrawable.WIFI ? "无线网络信号 " + level + " 格" : "网络已断开";
            target.setContentDescription(text + "，按确定键打开网络设置");
            // On V4 the pill may be wider than its icon; shrink only WRAP_CONTENT
            // widths, never change constrained/MATCH_PARENT parent layouts.
            ViewGroup.LayoutParams lp = target.getLayoutParams();
            if (lp != null && lp.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
                target.setMinimumWidth(dp(46));
                target.setPadding(dp(11), target.getPaddingTop(), dp(11), target.getPaddingBottom());
            }
        }

        private void ensureBluetoothNextTo(TextView target) {
            // The V4 menu may wrap the clickable TextView inside another group.
            // Insert after that wrapper in its nearest horizontal LinearLayout.
            View anchor = target;
            ViewGroup parent = (target.getParent() instanceof ViewGroup)
                    ? (ViewGroup) target.getParent() : null;
            while (parent != null && !(parent instanceof LinearLayout)) {
                if (!(parent.getParent() instanceof ViewGroup)) return;
                anchor = parent;
                parent = (ViewGroup) parent.getParent();
            }
            if (!(parent instanceof LinearLayout)) return;
            LinearLayout row = (LinearLayout) parent;
            int netIndex = row.indexOfChild(anchor);
            if (netIndex < 0) return;
            if (bluetooth == null) {
                bluetooth = new TextView(activity);
                bluetooth.setText("");
                bluetooth.setGravity(Gravity.CENTER);
                bluetooth.setFocusable(true);
                bluetooth.setClickable(true);
                bluetooth.setContentDescription("蓝牙状态，按确定键打开蓝牙设置");
                Drawable background = target.getBackground();
                if (background != null && background.getConstantState() != null) {
                    bluetooth.setBackground(background.getConstantState().newDrawable().mutate());
                }
                bluetooth.setOnClickListener(v -> {
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                    } catch (Exception first) {
                        try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                        catch (Exception ignored) { }
                    }
                });
            }
            if (bluetooth.getParent() != row) {
                if (bluetooth.getParent() instanceof ViewGroup)
                    ((ViewGroup) bluetooth.getParent()).removeView(bluetooth);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(46),
                        target.getHeight() > 0 ? target.getHeight() : dp(42));
                p.leftMargin = dp(7);
                row.addView(bluetooth, Math.min(netIndex + 1, row.getChildCount()), p);
            }
        }

        private void drawBluetooth(TextView target) {
            int state = 0;
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isEnabled()) {
                state = 1;
                // 4 is HID_HOST on Android 9 (paired Bluetooth remotes/keyboards).
                // Check profiles independently so an unsupported profile cannot hide
                // a connected headset or remote on a vendor-modified Bluetooth stack.
                for (int profile : new int[] {BluetoothProfile.A2DP,
                        BluetoothProfile.HEADSET, BluetoothProfile.HID_DEVICE, 4}) {
                    try {
                        if (adapter.getProfileConnectionState(profile)
                                == BluetoothProfile.STATE_CONNECTED) {
                            state = 2;
                            break;
                        }
                    } catch (RuntimeException ignored) { }
                }
            }
            target.setCompoundDrawableTintList((ColorStateList) null);
            target.setCompoundDrawablesRelativeWithIntrinsicBounds(new BluetoothIcon(dp(24), state), null, null, null);
            target.setContentDescription(state == 2 ? "蓝牙已连接设备，按确定键进入蓝牙设置"
                    : state == 1 ? "蓝牙已开启，未检测到已连接设备，按确定键进入蓝牙设置"
                    : "蓝牙已关闭或不可用，按确定键进入蓝牙设置");
        }
        private int dp(float v) { return (int) (activity.getResources().getDisplayMetrics().density * v + .5f); }
    }

    /** Small, dependency-free Bluetooth logo with off/on/connected colours. */
    private static final class BluetoothIcon extends Drawable {
        private final int size;
        private final int state;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int alpha = 255;
        BluetoothIcon(int size, int state) { this.size = size; this.state = state; }
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            int saved = canvas.save();
            canvas.translate(b.left, b.top);
            canvas.scale(b.width() / 24f, b.height() / 24f);
            paint.setColor(state == 2 ? Color.rgb(78, 179, 255)
                    : state == 1 ? Color.WHITE : Color.rgb(130, 138, 150));
            paint.setAlpha(alpha);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.1f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(12, 2); path.lineTo(18.5f, 7.5f); path.lineTo(6, 17.5f);
            path.moveTo(12, 22); path.lineTo(18.5f, 16.5f); path.lineTo(6, 6.5f);
            path.moveTo(12, 2); path.lineTo(12, 22);
            canvas.drawPath(path, paint);
            canvas.restoreToCount(saved);
        }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}

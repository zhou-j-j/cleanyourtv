package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** V5.2.1: updates an existing top-row network button when visible.
 * Never appends a fallback network badge to the right of Settings.
 * Does not modify the existing network button click handler.
 */
public final class AppleNetworkBadge {
    private AppleNetworkBadge() {}

    public static void attach(Activity activity, LinearLayout header, TextView originalNetwork) {
        if (activity == null || header == null || originalNetwork == null) return;
        Controller controller = new Controller(activity, header, originalNetwork);
        header.post(controller);
    }

    private static final class Controller implements Runnable, View.OnAttachStateChangeListener {
        private final Activity activity;
        private final LinearLayout header;
        private final TextView original;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private TextView lastTarget;
        private int lastMode = -1;
        private int lastLevel = -1;
        private boolean lastOnline;
        private boolean stopped;

        Controller(Activity activity, LinearLayout header, TextView original) {
            this.activity = activity;
            this.header = header;
            this.original = original;
            header.addOnAttachStateChangeListener(this);
        }
        @Override public void onViewAttachedToWindow(View v) {
            stopped = false;
            handler.removeCallbacks(this);
            handler.post(this);
        }
        @Override public void onViewDetachedFromWindow(View v) {
            stopped = true;
            handler.removeCallbacks(this);
            header.removeOnAttachStateChangeListener(this);
        }
        @Override public void run() {
            if (stopped || activity.isFinishing() || !header.isAttachedToWindow()) return;
            try {
                TextView target = findVisibleNetworkButton(header);
                if (target == null && original.isShown()) target = original;
                // Do not create a fourth header control to the right of Settings.
                // If the original network button is hidden, leave the row unchanged.

                int mode = NetworkStatusDrawable.DISCONNECTED;
                int level = 0;
                boolean usable = false;
                ConnectivityManager cm = (ConnectivityManager)
                        activity.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo active = cm == null ? null : cm.getActiveNetworkInfo();
                NetworkInfo ethernet = cm == null ? null :
                        cm.getNetworkInfo(ConnectivityManager.TYPE_ETHERNET);
                if (active != null && active.isConnected()
                        && active.getType() == ConnectivityManager.TYPE_WIFI) {
                    mode = NetworkStatusDrawable.WIFI;
                    WifiManager wifi = (WifiManager) activity.getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);
                    WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
                    if (info != null && info.getRssi() > -127 && info.getRssi() < 0)
                        level = WifiManager.calculateSignalLevel(info.getRssi(), 5);
                } else if ((active != null && active.getType() == ConnectivityManager.TYPE_ETHERNET)
                        || ethernet != null) {
                    mode = NetworkStatusDrawable.ETHERNET;
                    boolean link = ethernet != null && ethernet.isConnected();
                    if (!link && active != null && active.getType() == ConnectivityManager.TYPE_ETHERNET)
                        link = active.isConnected();
                    if (link) {
                        Network network = cm.getActiveNetwork();
                        NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
                        boolean isEth = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
                        // Prefer Android's real reachability test. Operator firmware may omit
                        // VALIDATED; the fallback means "connected, Internet route declared",
                        // not proof that a remote server is reachable.
                        usable = isEth && (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                                || (active != null && active.isConnected()
                                && active.getType() == ConnectivityManager.TYPE_ETHERNET
                                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)));
                    }
                }
                if (target != null) {
                    // The stock pill may reapply a white tint after connectivity events.
                    // Apply the state again even when unchanged, but only once per poll.
                    target.setCompoundDrawableTintList((ColorStateList) null);
                    target.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            new NetworkStatusDrawable(activity.getResources().getDisplayMetrics().density,
                                    mode, level, usable), null, null, null);
                    String name = mode == NetworkStatusDrawable.ETHERNET
                            ? (usable ? "有线网络正常" : "有线网络未连通")
                            : mode == NetworkStatusDrawable.WIFI
                            ? "Wi-Fi 信号 " + level + " 格" : "网络已断开";
                    target.setContentDescription(name + "，按确定键进入网络设置");
                }
                lastTarget = target;
                lastMode = mode;
                lastLevel = level;
                lastOnline = usable;
            } catch (RuntimeException e) {
                android.util.Log.w("CM2012NetBadge", "network badge update failed", e);
            }
            handler.postDelayed(this, 3000L);
        }

        private TextView findVisibleNetworkButton(View v) {
            if (v instanceof TextView) {
                TextView t = (TextView) v;
                String label = t.getText() == null ? "" : t.getText().toString();
                String desc = t.getContentDescription() == null ? "" : t.getContentDescription().toString();
                if (t.isShown() && (t == original || isNetworkLabel(label) || isNetworkLabel(desc)))
                    return t;
            }
            if (v instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) v;
                for (int i = 0; i < group.getChildCount(); i++) {
                    TextView found = findVisibleNetworkButton(group.getChildAt(i));
                    if (found != null) return found;
                }
            }
            return null;
        }
        private boolean isNetworkLabel(String s) {
            String l = s.toLowerCase(java.util.Locale.ROOT);
            return l.contains("网络") || l.contains("wi-fi") || l.contains("wifi")
                    || l.contains("ethernet") || l.contains("有线") || l.contains("无线");
        }
    }
}

package com.gathin.tvhome;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.NetworkInterface;

/** Prefer eth0's actual carrier, not the Wi-Fi on/off setting or just active route. */
final class AppleLinkStatus57 {
    final int mode;
    final int level;
    final boolean connected;
    final String description;
    private AppleLinkStatus57(int m,int l,boolean c,String d) {
        mode=m;level=l;connected=c;description=d;
    }
    static int readCarrier() {
        try (BufferedReader r = new BufferedReader(new FileReader("/sys/class/net/eth0/carrier"))) {
            String text = r.readLine();
            if ("1".equals(text)) return 1;
            if ("0".equals(text)) return 0;
        } catch (Exception ignored) { }
        // Some vendor kernels hide the carrier attribute but expose operstate.
        try (BufferedReader r = new BufferedReader(new FileReader("/sys/class/net/eth0/operstate"))) {
            String state = r.readLine();
            if ("up".equals(state)) return 1;
            if ("down".equals(state) || "lowerlayerdown".equals(state)) return 0;
        } catch (Exception ignored) { }
        return -1; // access denied or unknown; don't mistake interface UP for cable plugged
    }
    static AppleLinkStatus57 read(Context c) {
        ConnectivityManager cm = (ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo eth = null, wifiNetwork = null;
        try {
            if (cm != null) {
                eth = cm.getNetworkInfo(ConnectivityManager.TYPE_ETHERNET);
                wifiNetwork = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            }
        } catch (Exception ignored) { }
        int carrier = readCarrier();
        boolean ethConnected = eth != null && eth.isConnected();
        // On CM201-2 Ethernet and Wi-Fi may be up together. A plugged cable wins.
        if (carrier == 1 || (carrier == -1 && ethConnected)) {
            boolean linked = carrier == 1 || ethConnected;
            return new AppleLinkStatus57(NetworkStatusDrawable.ETHERNET, 4, linked,
                    linked ? "网线已连接" : "网线未连接");
        }
        WifiManager wm = (WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean wifiOn = false;
        int strength = 0;
        boolean wifiConnected = wifiNetwork != null && wifiNetwork.isConnected();
        boolean strengthKnown = false;
        try {
            if (wm != null) {
                wifiOn = wm.isWifiEnabled();
                WifiInfo wi = wm.getConnectionInfo();
                if (wi != null && wi.getRssi() > -127 && wi.getRssi() < 0) {
                    strengthKnown = true;
                    strength = WifiManager.calculateSignalLevel(wi.getRssi(), 5);
                }
            }
        } catch (Exception ignored) { }
        if (wifiConnected || wifiOn) {
            return new AppleLinkStatus57(NetworkStatusDrawable.WIFI, strength, wifiConnected,
                    wifiConnected ? (strengthKnown ? "Wi-Fi 已连接，信号 " + (strength+1) + " 级" : "Wi-Fi 已连接，信号强度未知")
                            : "Wi-Fi 未连接");
        }
        // Ethernet present but unplugged: keep cable icon grey, else generic grey.
        boolean ethExists = eth != null;
        if (!ethExists) {
            try { ethExists = NetworkInterface.getByName("eth0") != null; }
            catch (Exception ignored) { }
        }
        return new AppleLinkStatus57(ethExists ? NetworkStatusDrawable.ETHERNET : NetworkStatusDrawable.DISCONNECTED,
                0, false, "网络未连接");
    }
}
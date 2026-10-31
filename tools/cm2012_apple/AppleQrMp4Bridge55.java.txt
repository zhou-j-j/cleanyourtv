package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ScrollView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Temporary, local-network-only MP4 address input for CM201-2.
 *
 * Start only when the user clicks the QR button. A phone in the same home LAN
 * opens the page and POSTs an HTTPS URL. It fills the existing TV editor; it
 * NEVER changes the playlist or saves automatically. Closes on dismiss or
 * after 5 minutes. No cloud service or WeChat SDK/account integration.
 *
 * If WeChat's built-in browser refuses local HTTP, open the link in the
 * phone's normal browser. Use only on a trusted home LAN: the connection is HTTP.
 */
public final class AppleQrMp4Bridge {
    private static final String TAG = "AppleQrMp4Bridge";
    private static final long TTL_MS = 5 * 60_000L;
    private static final int MAX_BODY = 8192;
    private static final int MAX_URL = 4096;
    private static final int WHITE = 0xfff6f5fa;
    private static final int MUTED = 0xffcbc6d9;

    private AppleQrMp4Bridge() {}

    public static void show(Activity activity, EditText target, AlertDialog editor) {
        if (activity == null || target == null || editor == null || activity.isFinishing()) return;
        String ip = localIp(activity);
        if (ip == null) {
            Toast.makeText(activity, "未找到局域网 IPv4 地址，请连接 Wi-Fi 或有线网络", Toast.LENGTH_LONG).show();
            return;
        }
        final Session session;
        try { session = new Session(activity, target, editor); }
        catch (IOException e) {
            Toast.makeText(activity, "无法开启手机扫码接收端口：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        String url = "http://" + ip + ":" + session.port() + "/" + session.token;
        try {
            LinearLayout layout = new LinearLayout(activity);
            layout.setPadding(dp(activity, 20), dp(activity, 14), dp(activity, 20), dp(activity, 14));
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER_HORIZONTAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xff172238);
            bg.setCornerRadius(dp(activity, 20));
            layout.setBackground(bg);

            TextView title = text(activity, "手机扫码填写在线视频地址", 23, WHITE);
            layout.addView(title, new LinearLayout.LayoutParams(-1, dp(activity, 44)));
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            layout.addView(row, new LinearLayout.LayoutParams(-1, dp(activity, 208)));

            ImageView image = new ImageView(activity);
            image.setImageBitmap(qr(url, 340));
            image.setBackgroundColor(Color.WHITE);
            image.setPadding(dp(activity, 6), dp(activity, 6), dp(activity, 6), dp(activity, 6));
            LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(activity, 190), dp(activity, 190));
            qrParams.rightMargin = dp(activity, 20);
            row.addView(image, qrParams);

            LinearLayout instructions = new LinearLayout(activity);
            instructions.setOrientation(LinearLayout.VERTICAL);
            row.addView(instructions, new LinearLayout.LayoutParams(0, -2, 1));
            TextView help = text(activity,
                    "① 微信扫一扫或用手机浏览器扫描\n② 在手机上粘贴长网址\n③ 点击“发送到电视”\n④ 返回电视查看后确认", 16, MUTED);
            help.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            instructions.addView(help);
            TextView status = text(activity, "状态：等待手机连接…", 16, 0xffa8d9ff);
            status.setGravity(Gravity.LEFT);
            status.setPadding(0, dp(activity, 10), 0, dp(activity, 6));
            instructions.addView(status);
            session.statusView = status;
            TextView urlText = text(activity, url, 13, MUTED);
            urlText.setGravity(Gravity.LEFT);
            urlText.setMaxLines(2);
            instructions.addView(urlText);

            TextView previewTitle = text(activity, "接收到的网址（可检查后返回编辑器修改）", 14, MUTED);
            previewTitle.setGravity(Gravity.LEFT);
            previewTitle.setPadding(0, dp(activity, 12), 0, dp(activity, 5));
            layout.addView(previewTitle);
            ScrollView previewScroll = new ScrollView(activity);
            GradientDrawable previewBg = new GradientDrawable();
            previewBg.setColor(0xff273651);
            previewBg.setCornerRadius(dp(activity, 10));
            previewScroll.setBackground(previewBg);
            TextView preview = text(activity, "尚未收到视频地址", 14, WHITE);
            preview.setGravity(Gravity.TOP | Gravity.LEFT);
            preview.setPadding(dp(activity, 11), dp(activity, 8), dp(activity, 11), dp(activity, 8));
            preview.setTextIsSelectable(true);
            previewScroll.addView(preview);
            layout.addView(previewScroll, new LinearLayout.LayoutParams(-1, dp(activity, 90)));
            session.previewView = preview;

            TextView footer = text(activity,
                    "同一家庭局域网 · 5 分钟内有效 · 手机地址只填入输入框，不会自动保存", 13, MUTED);
            layout.addView(footer, new LinearLayout.LayoutParams(-1, dp(activity, 33)));
            AlertDialog popup = new AlertDialog.Builder(activity)
                    .setView(layout)
                    .setPositiveButton("完成，返回编辑", (d, w) -> d.dismiss())
                    .setNegativeButton("取消扫码", (d, w) -> d.dismiss())
                    .create();
            session.popup = popup;
            popup.setOnDismissListener(d -> session.stop());
            // Do not overwrite the editor's own dismiss listener. The receiver
            // observes editor.isShowing() and stops when this popup is dismissed.
            popup.show();
            if (popup.getWindow() != null) {
                popup.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                int width = Math.min(dp(activity, 780),
                        (int) (activity.getResources().getDisplayMetrics().widthPixels * .92f));
                popup.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            }
            session.start();
        } catch (Exception e) {
            session.stop();
            Log.w(TAG, "QR dialog failed", e);
            Toast.makeText(activity, "无法生成二维码，请重试", Toast.LENGTH_LONG).show();
        }
    }

    private static TextView text(Activity a, String value, int sp, int color) {
        TextView t = new TextView(a);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }
    private static int dp(Activity a, int value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }

    private static Bitmap qr(String url, int size) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix matrix = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, size, size, hints);
        int[] px = new int[matrix.getWidth() * matrix.getHeight()];
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++)
                px[y * matrix.getWidth() + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
        }
        Bitmap bitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.setPixels(px, 0, matrix.getWidth(), 0, 0, matrix.getWidth(), matrix.getHeight());
        return bitmap;
    }

    private static String localIp(Activity activity) {
        // Prefer the actual active connection: some boxes expose both eth0 and
        // wlan0 even when one is disconnected or isolated from the phone.
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = cm == null ? null : cm.getActiveNetwork();
            LinkProperties links = active == null ? null : cm.getLinkProperties(active);
            if (links != null) {
                for (LinkAddress address : links.getLinkAddresses()) {
                    InetAddress ip = address.getAddress();
                    if (ip instanceof Inet4Address && ip.isSiteLocalAddress())
                        return ip.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            if (all == null) return null;
            String fallback = null;
            for (NetworkInterface nif : Collections.list(all)) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                String n = nif.getName().toLowerCase(java.util.Locale.ROOT);
                if (n.startsWith("tun") || n.startsWith("tap") || n.startsWith("docker")) continue;
                for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress() || a.isLinkLocalAddress()
                            || !a.isSiteLocalAddress()) continue;
                    if (n.startsWith("wlan") || n.startsWith("eth") || n.startsWith("en"))
                        return a.getHostAddress();
                    if (fallback == null) fallback = a.getHostAddress();
                }
            }
            return fallback;
        } catch (Exception e) { return null; }
    }

    private static final class Session {
        private final Activity activity;
        private final EditText target;
        private final AlertDialog editor;
        private final ServerSocket server;
        private final Handler ui = new Handler(Looper.getMainLooper());
        private final long created = SystemClock.elapsedRealtime();
        private final String token;
        private volatile boolean stopped;
        private volatile boolean submitted;
        private volatile boolean aborted;
        private AlertDialog popup;
        private TextView statusView;
        private TextView previewView;

        Session(Activity a, EditText t, AlertDialog e) throws IOException {
            activity = a; target = t; editor = e;
            byte[] nonce = new byte[16];
            new SecureRandom().nextBytes(nonce);
            StringBuilder b = new StringBuilder(32);
            for (byte x : nonce) b.append(String.format(java.util.Locale.ROOT, "%02x", x & 255));
            token = b.toString();
            server = new ServerSocket(0, 3);
            server.setSoTimeout(1000);
        }
        int port() { return server.getLocalPort(); }
        void start() {
            Thread t = new Thread(this::serve, "CM2012-MP4-QR");
            t.setDaemon(true);
            t.start();
        }
        void stop() {
            if (!submitted) aborted = true;
            stopped = true;
            try { server.close(); } catch (IOException ignored) {}
        }
        boolean active() {
            return !stopped && !submitted && !server.isClosed()
                    && SystemClock.elapsedRealtime() - created < TTL_MS;
        }
        void serve() {
            while (active()) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(4000);
                    handle(socket);
                } catch (SocketTimeoutException ignored) {
                    // Wake regularly to enforce expiry even if nobody connects.
                } catch (IOException e) {
                    if (!stopped) Log.w(TAG, "temporary HTTP server", e);
                }
            }
            boolean done = submitted;
            stop();
            if (!done) ui.post(() -> {
                if (statusView != null && popup != null && popup.isShowing())
                    statusView.setText("状态：二维码已关闭或过期，请重新打开");
            });
        }
        private void handle(Socket socket) throws IOException {
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            String first = readLine(in, 4096);
            if (first == null) return;
            String[] part = first.split(" ");
            String path = part.length == 3 ? part[1].split("\\?", 2)[0] : "";
            if (part.length != 3 || !path.equals("/" + token) || !active()) {
                respond(out, 404, "Not found", "<p>页面不可用或已过期。</p>");
                return;
            }
            int size = -1;
            String line;
            int headerCount = 0;
            while ((line = readLine(in, 4096)) != null && !line.isEmpty() && headerCount++ < 40) {
                String lower = line.toLowerCase(java.util.Locale.ROOT);
                if (lower.startsWith("content-length:")) {
                    try { size = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim()); }
                    catch (NumberFormatException ignored) { size = -1; }
                }
            }
            if (line == null || headerCount >= 40) return;
            if ("GET".equals(part[0])) {
                ui.post(() -> {
                    if (statusView != null && popup != null && popup.isShowing())
                        statusView.setText("状态：手机已打开页面，等待发送地址…");
                });
                respond(out, 200, "OK", page(""));
            } else if ("POST".equals(part[0])) {
                if (size < 1 || size > MAX_BODY) {
                    respond(out, 413, "Payload too large", page("地址过长，请缩短至 4096 字符以内"));
                    return;
                }
                byte[] body = new byte[size];
                int offset = 0;
                while (offset < size) {
                    int n = in.read(body, offset, size - offset);
                    if (n == -1) return;
                    offset += n;
                }
                String video = null;
                for (String pair : new String(body, StandardCharsets.UTF_8).split("&")) {
                    if (pair.startsWith("video_url=")) {
                        try { video = URLDecoder.decode(pair.substring(10), "UTF-8").trim(); }
                        catch (IllegalArgumentException ignored) { video = null; }
                        break;
                    }
                }
                if (video == null || video.length() == 0 || video.length() > MAX_URL
                        || !validHttps(video)) {
                    respond(out, 400, "Bad Request", page("请输入完整的 HTTPS 在线 MP4 地址"));
                    return;
                }
                if (submitted || stopped) {
                    respond(out, 409, "Already submitted", page("本次扫码已结束"));
                    return;
                }
                submitted = true; // Reject a second POST while the TV main thread applies this one.
                final String value = video;
                CountDownLatch applied = new CountDownLatch(1);
                AtomicBoolean ok = new AtomicBoolean(false);
                ui.post(() -> {
                    try {
                        if (aborted || activity.isFinishing() || !editor.isShowing()) return;
                        target.setText(value);
                        target.setSelection(target.length());
                        if (previewView != null) previewView.setText(value);
                        if (statusView != null) statusView.setText("状态：电视已收到地址，可以返回编辑器确认保存");
                        ok.set(true);
                    } finally { applied.countDown(); }
                });
                boolean finished = false;
                try { finished = applied.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                if (finished && ok.get()) {
                    respond(out, 200, "OK", "<h2>电视已收到地址</h2>" +
                            "<p>可以返回电视检查网址，点击“完成，返回编辑”后按“确认保存”。</p>");
                } else {
                    respond(out, 409, "Conflict", "<h2>电视编辑窗口已关闭或响应超时</h2>" +
                            "<p>请重新打开电视二维码后再发送。</p>");
                }
            } else {
                respond(out, 405, "Method Not Allowed", "<p>不支持此请求。</p>");
            }
        }
        private String page(String error) {
            return "<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'>" +
                    "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                    "<title>发送 MP4 地址到电视</title><style>body{font-family:sans-serif;max-width:600px;" +
                    "margin:40px auto;padding:0 18px;color:#f6f7ff;background:#101827}" +
                    "textarea{width:100%;box-sizing:border-box;min-height:120px;padding:12px;" +
                    "font:16px sans-serif;border-radius:12px}button{margin-top:14px;padding:14px 18px;" +
                    "border:0;border-radius:12px;background:#397efb;color:white;font-size:17px}" +
                    "</style></head><body><h2>发送在线视频地址</h2><p>请粘贴 HTTPS 视频直链，" +
                    "然后点发送。此页面仅与同一局域网内的电视通信。</p>" +
                    (error.isEmpty() ? "" : "<p style='color:#ffadad'>" + error + "</p>") +
                    "<form method='post' action='" + "/" + token + "'>" +
                    "<textarea name='video_url' maxlength='4096' required" +
                    " placeholder='https://example.com/video.mp4'></textarea>" +
                    "<button type='submit'>发送到电视</button></form>" +
                    "<p>视频不会立即开始播放；请回电视确认保存。</p></body></html>";
        }
        private boolean validHttps(String value) {
            try {
                java.net.URI uri = new java.net.URI(value);
                return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                        && !uri.getHost().isEmpty() && uri.getRawUserInfo() == null;
            } catch (Exception e) { return false; }
        }
        private void respond(BufferedOutputStream out, int status, String label, String body) throws IOException {
            String html = body.startsWith("<!doctype html>") ? body
                    : "<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'>"
                    + "<meta name='referrer' content='no-referrer'></head><body>"
                    + body + "</body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            String header = "HTTP/1.1 " + status + " " + label + "\r\n" +
                    "Content-Type: text/html; charset=UTF-8\r\n" +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Referrer-Policy: no-referrer\r\n" +
                    "X-Content-Type-Options: nosniff\r\n" +
                    "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
            out.flush();
        }
        private String readLine(BufferedInputStream in, int max) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while (out.size() < max) {
                int b = in.read();
                if (b == -1) return null;
                if (b == '\n') return out.toString("UTF-8").replace("\r", "");
                out.write(b);
            }
            return null;
        }
    }
}

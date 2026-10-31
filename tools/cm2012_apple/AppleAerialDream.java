package com.gathin.tvhome;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * CM201-2 V5.2.1: online MP4 playlist with skip, timeout, and original local mode.
 * Reads the same SharedPreferences/video_uri used by V5.1.
 * Intentional: no pre-downloads or background network requests outside DreamService.
 */
public final class AppleAerialDream extends DreamService {
    private static final String PREFS = "cm2012_apple_aerial";
    private static final String URI_KEY = "video_uri";
    private static final String LIST_KEY = "online_playlist_json";
    private static final String MODE_KEY = "source_mode";
    private static final String LAST_INDEX_KEY = "online_playlist_last_index";
    private static final long LOAD_TIMEOUT_MS = 18000L;
    private static final long PROGRESS_INTERVAL_MS = 15000L;
    private static final long RETRY_DELAY_MS = 60000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout frame;
    private VideoView player;
    private TextView clock;
    private final List<String> playlist = new ArrayList<>();
    private boolean online;
    private boolean stopped;
    private long token;
    private int currentIndex = -1;
    private int failures;
    private int lastPosition;
    private int noProgressChecks;
    private Runnable loadTimeout;
    private Runnable progressCheck;
    private Runnable retry;
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (stopped || clock == null) return;
            clock.setText(new SimpleDateFormat("yyyy年M月d日  EEEE  HH:mm", Locale.CHINA)
                    .format(new Date()));
            handler.postDelayed(this, 30000L);
        }
    };

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        setInteractive(false);
        frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        player = new VideoView(this);
        frame.addView(player, new FrameLayout.LayoutParams(-1, -1));
        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(19f);
        clock.setShadowLayer(6f, 1f, 2f, Color.BLACK);
        clock.setPadding(dp(30), dp(12), dp(30), dp(26));
        FrameLayout.LayoutParams clockLp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.START);
        frame.addView(clock, clockLp);
        setContentView(frame);
    }

    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        stopped = false;
        token = 0;
        failures = 0;
        playlist.clear();
        clockTick.run();
        SharedPreferences pref = prefs();
        String uri = pref.getString(URI_KEY, "");
        if (uri == null) uri = "";
        String mode = pref.getString(MODE_KEY, "");
        online = "online".equals(mode) || (mode.length() == 0 && isHttps(uri));
        if (online) {
            try {
                JSONArray a = new JSONArray(pref.getString(LIST_KEY, "[]"));
                for (int i = 0; i < a.length(); i++) {
                    String entry = a.optString(i, "").trim();
                    if (isHttps(entry) && !playlist.contains(entry)) playlist.add(entry);
                }
            } catch (Exception ignored) { /* Corrupted list: fallback to legacy URI. */ }
            if (playlist.isEmpty() && isHttps(uri)) playlist.add(uri);
            if (!playlist.isEmpty()) {
                int last = pref.getInt(LAST_INDEX_KEY, -1);
                playIndex((last + 1 + playlist.size()) % playlist.size());
            }
        } else if (!uri.isEmpty()) {
            playLocal(uri);
        }
    }

    private boolean isHttps(String value) {
        return value != null && value.startsWith("https://")
                && Uri.parse(value).getHost() != null;
    }

    private void cancelPlaybackTasks() {
        if (loadTimeout != null) handler.removeCallbacks(loadTimeout);
        if (progressCheck != null) handler.removeCallbacks(progressCheck);
        if (retry != null) handler.removeCallbacks(retry);
        loadTimeout = null;
        progressCheck = null;
        retry = null;
    }

    private void playLocal(String uri) {
        if (stopped || player == null) return;
        final long currentToken = ++token;
        cancelPlaybackTasks();
        player.setOnPreparedListener(mp -> {
            if (stopped || token != currentToken) return;
            mp.setLooping(true);
            player.start();
        });
        player.setOnErrorListener((mp, what, extra) -> true);
        try {
            player.setVideoURI(Uri.parse(uri));
            player.start();
        } catch (Exception ignored) { /* Screen saver still shows clock. */ }
    }

    private void playIndex(int index) {
        if (stopped || player == null || playlist.isEmpty()) return;
        cancelPlaybackTasks();
        final long currentToken = ++token;
        currentIndex = index;
        String uri = playlist.get(index);
        player.stopPlayback();
        player.setOnPreparedListener(mp -> {
            if (stopped || token != currentToken) return;
            if (loadTimeout != null) handler.removeCallbacks(loadTimeout);
            loadTimeout = null;
            failures = 0;
            lastPosition = -1;
            noProgressChecks = 0;
            prefs().edit().putInt(LAST_INDEX_KEY, index).apply();
            player.start();
            scheduleProgress(currentToken);
        });
        player.setOnCompletionListener(mp -> {
            if (stopped || token != currentToken) return;
            failures = 0;
            playIndex((currentIndex + 1) % playlist.size());
        });
        player.setOnErrorListener((mp, what, extra) -> {
            if (!stopped && token == currentToken) skipFailed(currentToken);
            return true;
        });
        loadTimeout = () -> {
            if (!stopped && token == currentToken) skipFailed(currentToken);
        };
        handler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);
        try {
            player.setVideoURI(Uri.parse(uri));
            player.start();
        } catch (Exception ignored) { skipFailed(currentToken); }
    }

    private void scheduleProgress(final long currentToken) {
        progressCheck = () -> {
            if (stopped || token != currentToken || player == null) return;
            try {
                int now = player.getCurrentPosition();
                if (lastPosition >= 0 && now <= lastPosition) noProgressChecks++;
                else noProgressChecks = 0;
                lastPosition = now;
                if (noProgressChecks >= 2) {
                    skipFailed(currentToken);
                    return;
                }
            } catch (RuntimeException ignored) { /* wait for next check */ }
            handler.postDelayed(progressCheck, PROGRESS_INTERVAL_MS);
        };
        handler.postDelayed(progressCheck, PROGRESS_INTERVAL_MS);
    }

    private void skipFailed(long failedToken) {
        if (stopped || failedToken != token) return;
        ++token; // Invalidate any late player events before changing/retrying stream.
        cancelPlaybackTasks();
        try { player.stopPlayback(); } catch (RuntimeException ignored) {}
        failures++;
        if (failures >= playlist.size()) {
            // Every URL failed in this pass. Keep the date visible and retry later.
            failures = 0;
            retry = () -> { if (!stopped) playIndex((currentIndex + 1) % playlist.size()); };
            handler.postDelayed(retry, RETRY_DELAY_MS);
        } else {
            handler.post(() -> { if (!stopped) playIndex((currentIndex + 1) % playlist.size()); });
        }
    }

    @Override public void onDreamingStopped() {
        stopped = true;
        ++token;
        cancelPlaybackTasks();
        handler.removeCallbacks(clockTick);
        if (player != null) {
            player.setOnErrorListener(null);
            player.setOnCompletionListener(null);
            player.setOnPreparedListener(null);
            player.stopPlayback();
        }
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        stopped = true;
        ++token;
        cancelPlaybackTasks();
        handler.removeCallbacks(clockTick);
        super.onDetachedFromWindow();
    }
}

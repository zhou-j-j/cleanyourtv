package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** TV D-pad friendly quick-radio pages: no scaled focus rectangles to clip. */
final class AppleQuickUi56 {
    private AppleQuickUi56() { }
    static int dp(Activity a, float n) { return (int) (n * a.getResources().getDisplayMetrics().density + 0.5f); }
    static GradientDrawable shape(Activity a, boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(focused ? 0xFF294A75 : 0xFF18253E);
        d.setCornerRadius(dp(a, 13));
        d.setStroke(dp(a, focused ? 2 : 1), focused ? 0xFF9FDEFF : 0xFF405577);
        return d;
    }
    static LinearLayout page(Activity a, String title) {
        a.getWindow().setStatusBarColor(0xFF091125);
        a.getWindow().setNavigationBarColor(0xFF091125);
        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(0xFF091125);
        scroll.setFillViewport(true);
        LinearLayout column = new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        int edge = Math.min(dp(a, 100), Math.max(dp(a, 28), a.getResources().getDisplayMetrics().widthPixels / 10));
        column.setPadding(edge, dp(a, 26), edge, dp(a, 40));
        scroll.addView(column);
        a.setContentView(scroll);
        TextView h = new TextView(a);
        h.setText(title);
        h.setTextSize(27);
        h.setTypeface(null, Typeface.BOLD);
        h.setTextColor(Color.WHITE);
        h.setPadding(0, 0, 0, dp(a, 16));
        column.addView(h);
        return column;
    }
    static TextView caption(Activity a, LinearLayout parent, String value) {
        TextView t = new TextView(a);
        t.setText(value);
        t.setTextSize(15);
        t.setTextColor(0xFFAFBED2);
        t.setPadding(dp(a, 7), dp(a, 11), dp(a, 7), dp(a, 12));
        parent.addView(t);
        return t;
    }
    static void section(Activity a, LinearLayout parent, String label) {
        TextView t = caption(a, parent, label);
        t.setTextColor(0xFF7FCFFF);
        t.setTypeface(null, Typeface.BOLD);
    }
    static final class Row {
        final LinearLayout root;
        final TextView title;
        final TextView description;
        Row(LinearLayout r, TextView t, TextView d) { root = r; title = t; description = d; }
        void onClick(Runnable action) { root.setOnClickListener(v -> action.run()); }
        void onLongClick(Runnable action) {
            root.setOnLongClickListener(v -> { action.run(); return true; });
        }
    }
    static Row row(Activity a, LinearLayout parent, String heading, String subtitle) {
        LinearLayout tile = new LinearLayout(a);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(a, 20), dp(a, 13), dp(a, 20), dp(a, 13));
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackground(shape(a, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(a, 7);
        parent.addView(tile, lp);
        TextView title = new TextView(a);
        title.setText(heading);
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(null, Typeface.BOLD);
        tile.addView(title);
        TextView description = new TextView(a);
        description.setText(subtitle);
        description.setTextSize(13);
        description.setTextColor(0xFFB4C5DC);
        tile.addView(description);
        tile.setOnFocusChangeListener((v, focus) -> tile.setBackground(shape(a, focus)));
        return new Row(tile, title, description);
    }
}

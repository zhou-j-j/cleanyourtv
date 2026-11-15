package com.gathin.tvhome;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Minimal D-pad radio page: single switch followed immediately by visible rows. */
final class AppleRadioUi57 {
    private AppleRadioUi57() { }
    static int dp(Activity a, float v) { return (int)(a.getResources().getDisplayMetrics().density*v + .5f); }
    static GradientDrawable focus(Activity a, boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(selected ? 0xFF153155 : 0x0010203B);
        d.setCornerRadius(dp(a, 4));
        d.setStroke(dp(a, selected ? 2 : 1), selected ? 0xFF79BDFF : 0x0010203B);
        return d;
    }
    static LinearLayout page(Activity a) {
        a.getWindow().setStatusBarColor(0xFF07142A);
        a.getWindow().setNavigationBarColor(0xFF07142A);
        ScrollView scroll = new ScrollView(a);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF071428,0xFF0D2750,0xFF0B367B});
        scroll.setBackground(bg);
        scroll.setFillViewport(true);
        LinearLayout outer = new LinearLayout(a);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        outer.setPadding(dp(a, 12), dp(a, 30), dp(a, 12), dp(a, 28));
        scroll.addView(outer);
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        int screenWidth = a.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(dp(a, 770), Math.max(dp(a, 280), screenWidth * 72 / 100));
        outer.addView(body, new LinearLayout.LayoutParams(width, -2));
        a.setContentView(scroll);
        return body;
    }
    static final class Toggle {
        final LinearLayout row;
        final Switch toggle;
        Toggle(LinearLayout r, Switch s) { row=r; toggle=s; }
        void setOn(boolean on) { toggle.setChecked(on); }
        void action(Runnable run) { row.setOnClickListener(v -> run.run()); }
    }
    static Toggle switchRow(Activity a, LinearLayout parent, String title) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a, 9), dp(a, 4), dp(a, 8), dp(a, 4));
        row.setMinimumHeight(dp(a, 40));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(focus(a, false));
        row.setOnFocusChangeListener((v, f) -> row.setBackground(focus(a, f)));
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(a, 43)));
        TextView name = new TextView(a);
        name.setText(title);
        name.setTextSize(17);
        name.setTextColor(Color.WHITE);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch s = new Switch(a);
        s.setFocusable(false);
        s.setClickable(false);
        s.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0xFFFFFFFF,0xFF8D9BB0}));
        s.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0xFF21BC67,0xFF566579}));
        row.addView(s, new LinearLayout.LayoutParams(-2,-2));
        return new Toggle(row, s);
    }
    static final class Row {
        final LinearLayout root;
        Row(LinearLayout v) { root=v; }
        void action(Runnable r) { root.setOnClickListener(v -> r.run()); }
    }
    static Row item(Activity a, LinearLayout parent, String name, String badge, Drawable signal, boolean selected) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a, 10), dp(a, 6), dp(a, 9), dp(a, 6));
        row.setMinimumHeight(dp(a, 47));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(focus(a, false));
        row.setOnFocusChangeListener((v, focused) -> row.setBackground(focus(a, focused)));
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(a, 47)));
        TextView title = new TextView(a);
        title.setText(name);
        title.setTextSize(16);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setTextColor(selected ? 0xFF31E57C : 0xFFF3F6FC);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView info = new TextView(a);
        info.setText(badge);
        info.setTextSize(13);
        info.setTextColor(selected ? 0xFF31E57C : 0xFFB1C1D5);
        info.setGravity(Gravity.CENTER_VERTICAL);
        if (signal != null) {
            info.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, signal, null);
            info.setCompoundDrawablePadding(dp(a, 7));
        }
        row.addView(info, new LinearLayout.LayoutParams(-2, -2));
        return new Row(row);
    }
    static TextView hint(Activity a, LinearLayout parent) {
        TextView t = new TextView(a);
        t.setTextSize(13);
        t.setTextColor(0xFF96AFCF);
        t.setPadding(dp(a, 10), dp(a, 10), dp(a, 10), 0);
        parent.addView(t);
        return t;
    }
}
package vn.hannom.keyboard;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Small helpers for the two app screens (setup + settings), light/dark aware. */
final class Ui {
    final Activity a;
    final boolean dark;
    final Theme kb;          // keyboard palette, for the accent colour
    final int bg, card, text, dim, line, accent, onAccent;

    Ui(Activity a) {
        this.a = a;
        this.dark = Theme.isDark(a, Prefs.get(a));
        this.kb = Theme.current(a);
        bg = dark ? Color.parseColor("#121316") : Color.parseColor("#F4F5F7");
        card = dark ? Color.parseColor("#1D1F23") : Color.WHITE;
        text = dark ? Color.parseColor("#ECEDEF") : Color.parseColor("#1F2023");
        dim = dark ? Color.parseColor("#A2A6AD") : Color.parseColor("#5F6368");
        line = dark ? Color.parseColor("#2C2F35") : Color.parseColor("#E4E6EA");
        // the screens follow the chosen keyboard style's accent for the same light/dark mode
        accent = kb.accent;
        onAccent = kb.accentText;
    }

    static void applyWindowTheme(Activity a) {
        boolean dark = Theme.isDark(a, Prefs.get(a));
        a.setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
    }

    void styleWindow() {
        Window w = a.getWindow();
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bg));
        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15+ draws apps edge-to-edge: keep content clear of the status bar, navigation bar,
            // display cutout and the on-screen keyboard (the "Gõ thử" box must stay visible while typing).
            View content = w.getDecorView().findViewById(android.R.id.content);
            content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets in) {
                    int types = android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout()
                            | android.view.WindowInsets.Type.ime();
                    android.graphics.Insets s = in.getInsets(types);
                    v.setPadding(s.left, s.top, s.right, s.bottom);
                    return android.view.WindowInsets.CONSUMED;
                }
            });
            content.requestApplyInsets();
            android.view.WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(dark ? 0 : light, light);
            }
            return;
        }
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= 23) {
            int f = w.getDecorView().getSystemUiVisibility();
            if (!dark) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; else f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                if (!dark) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; else f &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            w.getDecorView().setSystemUiVisibility(f);
        }
    }

    int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics()));
    }

    GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    GradientDrawable outline(int fill, int stroke, float radiusDp, float strokeDp) {
        GradientDrawable g = round(fill, radiusDp);
        g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    TextView text(String s, float sp, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    TextView title(String s) {
        TextView t = text(s, 13, accent);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.04f);
        t.setPadding(dp(4), dp(22), dp(4), dp(8));
        return t;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(card, 16));
        c.setPadding(dp(16), dp(6), dp(16), dp(6));
        return c;
    }

    View divider() {
        View v = new View(a);
        v.setBackgroundColor(line);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f))));
        return v;
    }

    LinearLayout.LayoutParams lp(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    Button button(String s, boolean primary) {
        Button b = new Button(a);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTextColor(primary ? onAccent : accent);
        b.setBackground(primary ? round(accent, 12) : outline(card, accent, 12, 1.2f));
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setStateListAnimator(null);
        return b;
    }

    /** A row with title, optional subtitle and a switch on the right. */
    Switch switchRow(LinearLayout parent, String title, String sub, boolean checked) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 16, text));
        if (sub != null) {
            TextView s = text(sub, 13, dim);
            s.setPadding(0, dp(2), 0, 0);
            texts.addView(s);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch sw = new Switch(a);
        sw.setChecked(checked);
        tint(sw);
        row.addView(sw);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sw.toggle(); }
        });
        parent.addView(row);
        return sw;
    }

    void tint(Switch sw) {
        if (Build.VERSION.SDK_INT < 23) return;
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{accent, dark ? Color.parseColor("#BDBDBD") : Color.parseColor("#F1F1F1")}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{(accent & 0x00FFFFFF) | 0x80000000, dark ? Color.parseColor("#5A5D63") : Color.parseColor("#B9BCC2")}));
    }

    interface OnPick { void pick(int index); }

    /** Segmented choice (e.g. Sáng / Tối / Theo hệ thống). */
    LinearLayout segmented(String[] labels, int selected, final OnPick onPick) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(round(dark ? Color.parseColor("#2A2D33") : Color.parseColor("#ECEEF1"), 12));
        row.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = text(labels[i], 14, i == selected ? onAccent : text);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(6), dp(10), dp(6), dp(10));
            if (i == selected) {
                t.setBackground(round(accent, 10));
                t.setTypeface(Typeface.DEFAULT_BOLD);
            }
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onPick.pick(idx); }
            });
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }
}

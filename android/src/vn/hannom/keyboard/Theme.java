package vn.hannom.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;

/** Keyboard colour schemes. Each style has a light and a dark palette. */
public final class Theme {
    public final int bg, bar, key, keyFn, keyOn, pressed, accent, accentText, text, textDim, highlight;

    private Theme(String bg, String bar, String key, String keyFn, String keyOn, String pressed,
                  String accent, String accentText, String text, String textDim, String highlight) {
        this.bg = Color.parseColor(bg);
        this.bar = Color.parseColor(bar);
        this.key = Color.parseColor(key);
        this.keyFn = Color.parseColor(keyFn);
        this.keyOn = Color.parseColor(keyOn);
        this.pressed = Color.parseColor(pressed);
        this.accent = Color.parseColor(accent);
        this.accentText = Color.parseColor(accentText);
        this.text = Color.parseColor(text);
        this.textDim = Color.parseColor(textDim);
        this.highlight = Color.parseColor(highlight);
    }

    public static final String[] STYLE_IDS = {"classic", "lacquer", "paper", "jade", "indigo"};
    public static final String[] STYLE_NAMES = {"Cổ điển", "Son thếp vàng", "Giấy dó", "Ngọc bích", "Lam ngọc"};

    //                         bg         bar        key        keyFn      keyOn      pressed    accent     accTxt     text       dim        highlight
    private static final Theme[] DARK = {
            new Theme("#1E2126", "#262A30", "#3B4048", "#2A2E34", "#525A66", "#636B78", "#C62828", "#FFFFFF", "#FFFFFF", "#A0A6B0", "#F2C94C"),
            new Theme("#2A0E0E", "#3A1414", "#5A1F1F", "#431818", "#7A2A2A", "#8E3A3A", "#D4A017", "#2A0E0E", "#FBEFD5", "#C9A88A", "#F2C94C"),
            new Theme("#2B2621", "#342E28", "#4A4239", "#3A332C", "#5E5448", "#6B6052", "#9E2A2B", "#FFFFFF", "#F3EAD8", "#B5A893", "#E8C07A"),
            new Theme("#0F2A25", "#143630", "#1E4D44", "#173E37", "#2A6559", "#337566", "#2E9E7E", "#FFFFFF", "#E8F5F0", "#9CC5B8", "#F2C94C"),
            new Theme("#141A2E", "#1A2240", "#27325A", "#1F2849", "#34437A", "#3E4F8C", "#4F6BD8", "#FFFFFF", "#EEF1FB", "#A3ADD0", "#F2C94C"),
    };
    private static final Theme[] LIGHT = {
            new Theme("#E3E5E9", "#EEF0F3", "#FFFFFF", "#CDD1D8", "#B4BAC4", "#C3C8D0", "#C62828", "#FFFFFF", "#202124", "#5F6368", "#C62828"),
            new Theme("#F3E3D1", "#F8ECDD", "#FFF8EE", "#E6CBAE", "#D9B48C", "#E3C4A2", "#A61B1B", "#FFF3D6", "#3B1010", "#8A5A44", "#A61B1B"),
            new Theme("#E9DFC9", "#F1E8D5", "#FBF6EA", "#D9CAAC", "#C9B48B", "#D6C39D", "#3E3A36", "#F3EAD8", "#2B2622", "#7A6E60", "#9E2A2B"),
            new Theme("#D6EAE3", "#E4F2ED", "#FFFFFF", "#BCD9CF", "#9FC9BA", "#B0D3C6", "#1F7A60", "#FFFFFF", "#10322B", "#4F7D71", "#1F7A60"),
            new Theme("#DDE3F2", "#E9EEF9", "#FFFFFF", "#C3CDE8", "#A9B7DF", "#B8C4E5", "#3550B8", "#FFFFFF", "#141A2E", "#56608A", "#3550B8"),
    };

    public static int styleIndex(String id) {
        for (int i = 0; i < STYLE_IDS.length; i++) if (STYLE_IDS[i].equals(id)) return i;
        return 0;
    }

    public static Theme of(int style, boolean dark) {
        if (style < 0 || style >= DARK.length) style = 0;
        return dark ? DARK[style] : LIGHT[style];
    }

    public static boolean systemDark(Context c) {
        int m = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Resolve the "sáng / tối / theo hệ thống" choice. */
    public static boolean isDark(Context c, SharedPreferences p) {
        String mode = p.getString(Prefs.APPEARANCE, Prefs.APPEARANCE_SYSTEM);
        if (Prefs.APPEARANCE_DARK.equals(mode)) return true;
        if (Prefs.APPEARANCE_LIGHT.equals(mode)) return false;
        return systemDark(c);
    }

    public static Theme current(Context c) {
        SharedPreferences p = Prefs.get(c);
        return of(styleIndex(p.getString(Prefs.STYLE, "classic")), isDark(c, p));
    }
}

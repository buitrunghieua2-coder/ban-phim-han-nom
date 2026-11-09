package vn.hannom.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings keys. The file name and "mode"/"vibrate" keys are kept from v1.0. */
public final class Prefs {
    static final String FILE = "settings";

    static final String MODE = "mode";                 // int: 0 Hán Nôm, 1 Tiếng Việt, 2 English
    static final String APPEARANCE = "appearance";     // light | dark | system
    static final String APPEARANCE_LIGHT = "light";
    static final String APPEARANCE_DARK = "dark";
    static final String APPEARANCE_SYSTEM = "system";
    static final String STYLE = "style";               // Theme.STYLE_IDS
    static final String KEY_HEIGHT = "key_height";     // int 0 thấp, 1 vừa, 2 cao
    static final String NUMBER_ROW = "number_row";     // boolean
    static final String VIBRATE = "vibrate";           // boolean
    static final String VIBRATE_STRENGTH = "vibrate_strength"; // int 0 nhẹ, 1 vừa, 2 mạnh
    static final String SOUND = "sound";               // boolean
    static final String SOUND_VOLUME = "sound_volume"; // int 0..100
    static final String LEARN = "learn";               // boolean

    static final int[] KEY_HEIGHT_DP = {44, 50, 57};

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}

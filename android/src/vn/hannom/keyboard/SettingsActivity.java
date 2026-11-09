package vn.hannom.keyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** "Cài đặt" — opened from the gear on the keyboard, the app, or system keyboard settings. */
public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private Ui ui;
    private ScrollView scroll;
    private TextView learnedInfo;

    @Override
    protected void onCreate(Bundle state) {
        Ui.applyWindowTheme(this);
        super.onCreate(state);
        prefs = Prefs.get(this);
        build(state != null ? state.getInt("scroll", 0) : 0);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (scroll != null) out.putInt("scroll", scroll.getScrollY());
    }

    /** Light/dark switches need a new window theme; everything else just rebuilds the views. */
    private void rebuild(boolean recreateWindow) {
        if (recreateWindow) {
            recreate();
        } else {
            build(scroll != null ? scroll.getScrollY() : 0);
        }
    }

    private void build(final int scrollY) {
        ui = new Ui(this);
        ui.styleWindow();
        scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(ui.bg);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(32));
        scroll.addView(col);

        // ---- header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(android.content.res.ColorStateList.valueOf(ui.text));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setContentDescription("Quay lại");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        header.addView(back, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(52)));
        TextView h = ui.text("Cài đặt", 24, ui.text);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(h);
        col.addView(header);

        // ---- appearance
        col.addView(ui.title("GIAO DIỆN"));
        LinearLayout c1 = ui.card();
        c1.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(16));
        c1.addView(ui.text("Chế độ màu", 16, ui.text));
        final String[] modes = {Prefs.APPEARANCE_LIGHT, Prefs.APPEARANCE_DARK, Prefs.APPEARANCE_SYSTEM};
        String cur = prefs.getString(Prefs.APPEARANCE, Prefs.APPEARANCE_SYSTEM);
        int sel = 2;
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(cur)) sel = i;
        LinearLayout seg = ui.segmented(new String[]{"Sáng", "Tối", "Theo hệ thống"}, sel, new Ui.OnPick() {
            @Override public void pick(int i) {
                boolean wasDark = ui.dark;
                prefs.edit().putString(Prefs.APPEARANCE, modes[i]).apply();
                rebuild(Theme.isDark(SettingsActivity.this, prefs) != wasDark);
            }
        });
        c1.addView(seg, ui.lp(10));

        TextView styleLabel = ui.text("Kiểu bàn phím", 16, ui.text);
        c1.addView(styleLabel, ui.lp(20));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(tiles);
        int curStyle = Theme.styleIndex(prefs.getString(Prefs.STYLE, "classic"));
        for (int i = 0; i < Theme.STYLE_IDS.length; i++) tiles.addView(styleTile(i, i == curStyle));
        c1.addView(hs, ui.lp(10));

        c1.addView(ui.text("Chiều cao phím", 16, ui.text), ui.lp(20));
        c1.addView(ui.segmented(new String[]{"Thấp", "Vừa", "Cao"}, prefs.getInt(Prefs.KEY_HEIGHT, 1), new Ui.OnPick() {
            @Override public void pick(int i) {
                prefs.edit().putInt(Prefs.KEY_HEIGHT, i).apply();
                rebuild(false);
            }
        }), ui.lp(10));
        col.addView(c1);

        LinearLayout c1b = ui.card();
        Switch numRow = ui.switchRow(c1b, "Hàng số", "Hiện hàng phím 1–0 phía trên chữ cái", prefs.getBoolean(Prefs.NUMBER_ROW, true));
        numRow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { prefs.edit().putBoolean(Prefs.NUMBER_ROW, on).apply(); }
        });
        col.addView(c1b, ui.lp(10));

        // ---- feedback
        col.addView(ui.title("RUNG & ÂM THANH"));
        LinearLayout c2 = ui.card();
        final boolean vib = prefs.getBoolean(Prefs.VIBRATE, true);
        Switch swVib = ui.switchRow(c2, "Rung khi gõ phím", null, vib);
        final LinearLayout vibStrength = ui.segmented(new String[]{"Nhẹ", "Vừa", "Mạnh"}, prefs.getInt(Prefs.VIBRATE_STRENGTH, 1), new Ui.OnPick() {
            @Override public void pick(int i) {
                prefs.edit().putInt(Prefs.VIBRATE_STRENGTH, i).apply();
                rebuild(false);
                preview();
            }
        });
        LinearLayout.LayoutParams vlp = ui.lp(0);
        vlp.bottomMargin = ui.dp(12);
        c2.addView(vibStrength, vlp);
        setEnabledDeep(vibStrength, vib);
        swVib.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                prefs.edit().putBoolean(Prefs.VIBRATE, on).apply();
                setEnabledDeep(vibStrength, on);
                if (on) preview();
            }
        });
        c2.addView(ui.divider());

        final boolean snd = prefs.getBoolean(Prefs.SOUND, false);
        Switch swSnd = ui.switchRow(c2, "Âm thanh khi gõ phím", "Theo âm lượng hệ thống; tắt khi máy ở chế độ im lặng", snd);
        LinearLayout volRow = new LinearLayout(this);
        volRow.setOrientation(LinearLayout.HORIZONTAL);
        volRow.setGravity(Gravity.CENTER_VERTICAL);
        volRow.addView(ui.text("Âm lượng", 14, ui.dim));
        final SeekBar vol = new SeekBar(this);
        vol.setMax(100);
        vol.setProgress(prefs.getInt(Prefs.SOUND_VOLUME, 50));
        vol.setProgressTintList(android.content.res.ColorStateList.valueOf(ui.accent));
        vol.setThumbTintList(android.content.res.ColorStateList.valueOf(ui.accent));
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) prefs.edit().putInt(Prefs.SOUND_VOLUME, p).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { preview(); }
        });
        volRow.addView(vol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final LinearLayout volWrap = volRow;
        LinearLayout.LayoutParams vrl = ui.lp(0);
        vrl.bottomMargin = ui.dp(12);
        c2.addView(volRow, vrl);
        setEnabledDeep(volWrap, snd);
        swSnd.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                prefs.edit().putBoolean(Prefs.SOUND, on).apply();
                setEnabledDeep(volWrap, on);
                if (on) preview();
            }
        });
        col.addView(c2);

        // ---- suggestions
        col.addView(ui.title("GỢI Ý CHỮ"));
        LinearLayout c3 = ui.card();
        Switch swLearn = ui.switchRow(c3, "Học theo thói quen gõ",
                "Chữ bạn hay chọn được đưa lên trước; các chữ thường gõ liền nhau sẽ được gợi ý và ghép thành từ. Dữ liệu chỉ lưu trên máy này.",
                prefs.getBoolean(Prefs.LEARN, true));
        swLearn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { prefs.edit().putBoolean(Prefs.LEARN, on).apply(); }
        });
        c3.addView(ui.divider());
        learnedInfo = ui.text("…", 13, ui.dim);
        learnedInfo.setPadding(0, ui.dp(12), 0, ui.dp(10));
        c3.addView(learnedInfo);
        android.widget.Button clear = ui.button("Xoá dữ liệu đã học", false);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClear(); }
        });
        LinearLayout.LayoutParams clp = ui.lp(0);
        clp.bottomMargin = ui.dp(12);
        c3.addView(clear, clp);
        TextView rankNote = ui.text("Thứ tự gợi ý mặc định ưu tiên chữ phổ biến (theo tần suất chữ Nôm, âm tiết tiếng Việt và chữ Hán), âm đọc chính đứng trước âm đọc phụ.", 13, ui.dim);
        rankNote.setPadding(0, 0, 0, ui.dp(12));
        c3.addView(rankNote);
        col.addView(c3);
        refreshLearnedInfo();

        // ---- try it
        col.addView(ui.title("GÕ THỬ"));
        LinearLayout c4 = ui.card();
        c4.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        EditText et = new EditText(this);
        et.setTypeface(KeyboardService.nomTypeface(this));
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        et.setTextColor(ui.text);
        et.setHintTextColor(ui.dim);
        et.setHint("vieejtnam → 越南");
        et.setMinLines(2);
        et.setGravity(Gravity.TOP);
        et.setBackground(null);
        c4.addView(et);
        col.addView(c4);

        // ---- about & licences
        col.addView(ui.title("GIỚI THIỆU"));
        LinearLayout c5 = ui.card();
        TextView about = ui.text("Bàn Phím Hán Nôm là phần mềm tự do, miễn phí, mã nguồn mở theo giấy phép GPL-3.0.\n"
                + "© 2026 Bùi Trung Hiếu và Dự án Bàn phím Hán Nôm.\n"
                + "Dữ liệu: Hán Việt tự điển (Thiều Chửu), Wiktionary, Unicode Unihan, rime-ime-han-nom-data. Phông Nôm Na Tống.", 13, ui.dim);
        about.setPadding(0, ui.dp(12), 0, ui.dp(10));
        c5.addView(about);
        android.widget.Button lic = ui.button("Nguồn dữ liệu và giấy phép", false);
        lic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showLicences(); }
        });
        LinearLayout.LayoutParams llp = ui.lp(0);
        llp.bottomMargin = ui.dp(8);
        c5.addView(lic, llp);
        android.widget.Button src = ui.button("Mã nguồn trên GitHub", false);
        src.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(SOURCE_URL)));
                } catch (Exception e) {
                    android.widget.Toast.makeText(SettingsActivity.this, SOURCE_URL, android.widget.Toast.LENGTH_LONG).show();
                }
            }
        });
        LinearLayout.LayoutParams slp = ui.lp(0);
        slp.bottomMargin = ui.dp(12);
        c5.addView(src, slp);
        col.addView(c5);

        TextView ver = ui.text("Bàn Phím Hán Nôm · phiên bản " + versionName(), 12, ui.dim);
        ver.setGravity(Gravity.CENTER);
        col.addView(ver, ui.lp(24));

        setContentView(scroll);
        scroll.post(new Runnable() {
            @Override public void run() { scroll.scrollTo(0, scrollY); }
        });
    }

    private View styleTile(final int index, boolean selected) {
        Theme t = Theme.of(index, ui.dark);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(8));
        tile.setBackground(selected ? ui.outline(ui.card, ui.accent, 12, 2) : ui.outline(ui.card, ui.line, 12, 1));

        // miniature keyboard
        LinearLayout mini = new LinearLayout(this);
        mini.setOrientation(LinearLayout.VERTICAL);
        mini.setBackground(ui.round(t.bg, 8));
        mini.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        LinearLayout barRow = new LinearLayout(this);
        barRow.setBackground(ui.round(t.bar, 3));
        barRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView cand = new TextView(this);
        cand.setText("越 南");
        cand.setTypeface(KeyboardService.nomTypeface(this));
        cand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        cand.setTextColor(t.highlight);
        cand.setPadding(ui.dp(4), 0, 0, 0);
        barRow.addView(cand);
        mini.addView(barRow, new LinearLayout.LayoutParams(ui.dp(96), ui.dp(14)));
        int[] counts = {6, 6, 5};
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_HORIZONTAL);
            for (int k = 0; k < counts[r]; k++) row.addView(miniKey(r == 2 && (k == 0 || k == 4) ? t.keyFn : t.key, 1));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ui.dp(96), ui.dp(12));
            p.topMargin = ui.dp(3);
            mini.addView(row, p);
        }
        LinearLayout last = new LinearLayout(this);
        last.setOrientation(LinearLayout.HORIZONTAL);
        last.addView(miniKey(t.keyFn, 1));
        last.addView(miniKey(t.accent, 1));
        last.addView(miniKey(t.key, 3));
        last.addView(miniKey(t.accent, 1.3f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(96), ui.dp(12));
        lp.topMargin = ui.dp(3);
        mini.addView(last, lp);
        tile.addView(mini);

        TextView name = ui.text(Theme.STYLE_NAMES[index], 13, selected ? ui.accent : ui.text);
        if (selected) name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setPadding(0, ui.dp(6), 0, 0);
        tile.addView(name);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().putString(Prefs.STYLE, Theme.STYLE_IDS[index]).apply();
                rebuild(false);
            }
        });
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.rightMargin = ui.dp(10);
        tile.setLayoutParams(tlp);
        return tile;
    }

    private View miniKey(int color, float weight) {
        View v = new View(this);
        v.setBackground(ui.round(color, 2));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        p.leftMargin = ui.dp(1.5f);
        p.rightMargin = ui.dp(1.5f);
        v.setLayoutParams(p);
        return v;
    }

    private static void setEnabledDeep(View v, boolean on) {
        v.setEnabled(on);
        v.setAlpha(on ? 1f : 0.4f);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                g.getChildAt(i).setEnabled(on);
            }
        }
    }

    private Feedback previewer;

    private void preview() {
        if (previewer == null) previewer = new Feedback(this);
        previewer.reload(prefs);
        previewer.key(scroll, Feedback.KIND_NORMAL);
    }

    private void refreshLearnedInfo() {
        HanNomData.load(this, new Runnable() {
            @Override public void run() {
                if (learnedInfo == null) return;
                UserHistory h = HanNomData.history();
                HanNomDict d = HanNomData.dict();
                int picks = h == null ? 0 : h.size();
                int pairs = h == null ? 0 : h.pairCount();
                int words = d == null ? 0 : d.userWordCount();
                learnedInfo.setText(picks == 0 && pairs == 0 ? "Chưa có dữ liệu học."
                        : "Đã nhớ " + picks + " chữ/từ hay chọn, " + pairs + " cặp chữ đi liền nhau, " + words + " từ ghép mới.");
            }
        });
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Xoá dữ liệu đã học?")
                .setMessage("Thứ tự gợi ý sẽ trở về mặc định và các từ ghép đã học sẽ bị xoá. Không thể hoàn tác.")
                .setNegativeButton("Huỷ", null)
                .setPositiveButton("Xoá", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        HanNomData.clearLearned(SettingsActivity.this);
                        refreshLearnedInfo();
                    }
                })
                .show();
    }

    static final String SOURCE_URL = "https://github.com/buitrunghieua2-coder/ban-phim-han-nom";

    private void showLicences() {
        String text;
        try {
            java.io.InputStream in = getAssets().open("GIAY-PHEP.txt");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) bo.write(buf, 0, n);
            in.close();
            text = new String(bo.toByteArray(), "UTF-8");
        } catch (Exception e) {
            text = "Không đọc được tệp giấy phép. Xem tại " + SOURCE_URL;
        }
        TextView tv = ui.text(text, 13, ui.text);
        tv.setTextIsSelectable(true);
        tv.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("Nguồn dữ liệu và giấy phép")
                .setView(sv)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.3";
        }
    }
}

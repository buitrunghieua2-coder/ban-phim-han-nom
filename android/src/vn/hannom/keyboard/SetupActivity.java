package vn.hannom.keyboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Launcher screen: enable/pick the keyboard, open settings, try typing. */
public class SetupActivity extends Activity {
    private Button btnEnable, btnPick;
    private TextView status;
    private Ui ui;
    private boolean wasDark;
    private String styleShown;

    @Override
    protected void onCreate(Bundle b) {
        Ui.applyWindowTheme(this);
        super.onCreate(b);
        build();
    }

    private void build() {
        ui = new Ui(this);
        wasDark = ui.dark;
        styleShown = Prefs.get(this).getString(Prefs.STYLE, "classic");
        ui.styleWindow();
        Typeface nom = KeyboardService.nomTypeface(this);
        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        sv.setBackgroundColor(ui.bg);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = ui.dp(20);
        col.setPadding(p, p, p, p);
        sv.addView(col);

        TextView logo = ui.text("漢喃", 48, ui.accent);
        logo.setTypeface(nom);
        col.addView(logo);
        TextView title = ui.text("Bàn Phím Hán Nôm", 24, ui.text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title);
        col.addView(ui.text("Gõ chữ Hán và chữ Nôm bằng âm Quốc ngữ kiểu Telex. Khoảng 16.800 chữ và 6.000 từ ghép, chạy hoàn toàn trên máy, không cần mạng.", 15, ui.dim), ui.lp(4));

        status = ui.text("", 15, ui.text);
        status.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        status.setBackground(ui.round(ui.card, 12));
        col.addView(status, ui.lp(16));

        btnEnable = ui.button("1. Bật bàn phím Hán Nôm trong Cài đặt", true);
        btnEnable.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)); }
        });
        col.addView(btnEnable, ui.lp(12));
        btnPick = ui.button("2. Chọn bàn phím Hán Nôm", true);
        btnPick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        });
        col.addView(btnPick, ui.lp(12));
        Button settings = ui.button("⚙  Cài đặt bàn phím", false);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(SetupActivity.this, SettingsActivity.class)); }
        });
        col.addView(settings, ui.lp(12));

        TextView tryLabel = ui.text("Gõ thử:", 16, ui.text);
        tryLabel.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(tryLabel, ui.lp(20));
        EditText et = new EditText(this);
        et.setTypeface(nom);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        et.setTextColor(ui.text);
        et.setHintTextColor(ui.dim);
        et.setHint("vieejtnam → 越南");
        et.setMinLines(3);
        et.setGravity(Gravity.TOP);
        et.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        et.setBackground(ui.round(ui.card, 12));
        col.addView(et, ui.lp(8));

        TextView how = ui.text("Cách gõ\n"
                + "• Gõ âm Quốc ngữ bằng Telex: aa→â, aw→ă, ee→ê, oo→ô, ow→ơ, uw→ư, dd→đ; dấu s sắc, f huyền, r hỏi, x ngã, j nặng.\n"
                + "• Chạm vào chữ trên thanh ứng viên để chọn. Phím cách chọn chữ đầu tiên (chữ được tô màu).\n"
                + "• Có thể gõ liền nhiều âm để ra từ ghép: vieejtnam → 越南, vawnhoas → 文化. Dùng dấu ' để tách âm khi cần.\n"
                + "• Không cần gõ dấu: \"nguoi\" vẫn ra 𠊛, nhưng gõ đủ dấu sẽ chính xác hơn.\n"
                + "• Chữ phổ biến hiện trước; chữ bạn hay chọn sẽ dần được đưa lên đầu.\n"
                + "• Nhấn giữ một chữ trên thanh gợi ý để xem âm đọc, nghĩa (Thiều Chửu) và các từ ghép; nhấn \"Chèn\" để chọn chữ đó.\n"
                + "• Nút ▼ mở bảng chọn đầy đủ, có ghi âm đọc dưới mỗi chữ.\n"
                + "• Sau khi chọn một chữ, thanh ứng viên gợi ý chữ tiếp theo thường đi kèm.\n"
                + "• Enter: giữ nguyên chữ Quốc ngữ đang gõ.\n"
                + "• Phím 漢 / VI / EN: chuyển giữa Hán Nôm, Tiếng Việt (Telex) và tiếng Anh.\n"
                + "• Bánh răng ⚙ bên trái thanh ứng viên: mở cài đặt. Giữ phím cách: đổi sang bàn phím khác.", 15, ui.text);
        how.setLineSpacing(ui.dp(3), 1f);
        col.addView(how, ui.lp(20));

        TextView font = ui.text("Lưu ý về phông chữ\nBàn phím có sẵn phông Nôm Na Tống nên luôn hiển thị được chữ. Nhưng ứng dụng bạn gõ vào dùng phông của chính nó: nhiều chữ Nôm hiếm (ngoài bảng mã cơ bản) có thể hiện thành ô vuông trên máy chưa có phông Hán Nôm. Văn bản vẫn đúng mã Unicode, mở trên máy có phông Nôm sẽ hiển thị bình thường.", 14, ui.dim);
        col.addView(font, ui.lp(16));
        TextView src = ui.text("Nguồn dữ liệu & giấy phép\n"
                + "• Từ điển: rime-ime-han-nom-data © 2022 Tuong-Minh (Mike) Vo, giấy phép MIT (tổng hợp từ chunom.org, Wiktionary, HVDic, Ủy ban Phục sinh Hán Nôm).\n"
                + "• Thứ tự gợi ý: tần suất từ wordfreq © Robyn Speer (dữ liệu CC BY-SA 4.0).\n"
                + "• Giải nghĩa: Thiều Chửu, Hán Việt tự điển (phạm vi công cộng); Unicode Unihan; nghĩa tiếng Anh của từ tiếng Việt từ Wiktionary qua Kaikki.org (CC BY-SA 4.0).\n"
                + "• Phông: Nom Na Tong © 2019 nomfoundation (Quỹ Bảo tồn Di sản Chữ Nôm), giấy phép MIT.\n"
                + "• Biến thể giản thể/phồn thể: Unicode Unihan Database.", 13, ui.dim);
        col.addView(src, ui.lp(16));
        setContentView(sv);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Theme.isDark(this, Prefs.get(this)) != wasDark) { recreate(); return; }
        if (!Prefs.get(this).getString(Prefs.STYLE, "classic").equals(styleShown)) { build(); return; }
        refresh();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) refresh();
    }

    private void refresh() {
        if (status == null) return;
        boolean enabled = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            for (InputMethodInfo i : imm.getEnabledInputMethodList()) {
                if (i.getPackageName().equals(getPackageName())) { enabled = true; break; }
            }
        }
        String cur = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (cur != null && cur.startsWith(getPackageName() + "/")) {
            status.setText("✅ Bàn phím Hán Nôm đang được sử dụng. Gõ thử bên dưới.");
        } else if (enabled) {
            status.setText("☑️ Đã bật. Bước tiếp: nhấn nút 2 để chọn bàn phím Hán Nôm.");
        } else {
            status.setText("⚠️ Chưa bật. Nhấn nút 1, rồi gạt công tắc \"Hán Nôm (漢喃)\".");
        }
        btnEnable.setEnabled(!enabled);
        btnEnable.setAlpha(enabled ? 0.5f : 1f);
        btnPick.setEnabled(enabled);
        btnPick.setAlpha(enabled ? 1f : 0.5f);
    }
}

package vn.hannom.keyboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class KeyboardService extends InputMethodService
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int K_SHIFT = -1, K_SYM = -2, K_LANG = -3, K_ENTER = -4, K_DEL = -5, K_SPACE = 32;
    static final int MODE_HANNOM = 0, MODE_VIET = 1, MODE_EN = 2;
    private static final int MAX_CANDS = 150, MAX_PREDICTIONS = 40;
    private static final int IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000;

    private static final String[] NUM_ROW = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
    private static final String[] L1 = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
    private static final String[] L2 = {"a", "s", "d", "f", "g", "h", "j", "k", "l"};
    private static final String[] L3 = {"z", "x", "c", "v", "b", "n", "m"};
    private static final String[] S1 = {"@", "#", "₫", "%", "&", "-", "+", "(", ")", "/"};
    private static final String[] S2 = {"、", "「", "」", "『", "』", "《", "》", "！", "？"};
    private static final String[] S3 = {"'", "\"", ":", ";", "*", "~", "…"};
    private static final String[] S0 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};

    private static Typeface nomFont;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TelexEngine engine = new TelexEngine();
    private final StringBuilder raw = new StringBuilder();
    private List<HanNomDict.Syllable> segs = new ArrayList<>();
    private List<HanNomDict.Candidate> cands = new ArrayList<>();
    private List<HanNomDict.Prediction> predictions = new ArrayList<>();

    private LinearLayout root, bar;
    private FrameLayout body;
    private LinearLayout candRow;
    private HorizontalScrollView candScroll;
    private TextView expandBtn;
    private ImageView gearBtn;

    private Theme theme;
    private Feedback feedback;
    private int keyHeightDp = 50;
    private boolean numberRow = true;
    private boolean learnEnabled = true;
    private boolean noPersonalLearning = false;

    private long lastOwnEdit = 0, lastShiftTap = 0;
    /** Length of our last commit, so a late cursor update caused by it is not taken as the user moving the cursor. */
    private int ownCommitLen = -1;
    private long ownCommitAt = 0;
    private boolean expanded, symbols, shifted, capsLock, textField = true;
    private int mode = MODE_HANNOM;

    /** Definition panel: a stack so tapping a compound / a character inside it can go back. */
    private static final class DefItem {
        final String text, readingKey;
        final Runnable insert;   // only for the item that was long-pressed in the bar

        DefItem(String text, String readingKey, Runnable insert) {
            this.text = text;
            this.readingKey = readingKey;
            this.insert = insert;
        }
    }
    private final List<DefItem> defStack = new ArrayList<>();

    /** Last thing picked from the candidate bar, for learning pairs; null breaks the chain. */
    private String lastPickText, lastPickReading;

    // ------------------------------------------------------------ lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        nomTypeface(this);
        feedback = new Feedback(this);
        Prefs.get(this).registerOnSharedPreferenceChangeListener(this);
        readSettings();
        HanNomData.load(this, new Runnable() {
            @Override public void run() {
                if (raw.length() > 0) refreshHanNom(); else renderCandidateBar();
            }
        });
    }

    @Override
    public void onDestroy() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this);
        HanNomData.saveNow(this);
        super.onDestroy();
    }

    static Typeface nomTypeface(Context c) {
        if (nomFont == null) {
            try {
                nomFont = Typeface.createFromAsset(c.getAssets(), "nom.ttf");
            } catch (RuntimeException e) {
                nomFont = Typeface.DEFAULT;
            }
        }
        return nomFont;
    }

    private void readSettings() {
        SharedPreferences p = Prefs.get(this);
        mode = p.getInt(Prefs.MODE, MODE_HANNOM);
        theme = Theme.current(this);
        int h = p.getInt(Prefs.KEY_HEIGHT, 1);
        keyHeightDp = Prefs.KEY_HEIGHT_DP[Math.max(0, Math.min(2, h))];
        numberRow = p.getBoolean(Prefs.NUMBER_ROW, true);
        learnEnabled = p.getBoolean(Prefs.LEARN, true);
        feedback.reload(p);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (Prefs.MODE.equals(key)) return; // we wrote it ourselves
        handler.post(new Runnable() {
            @Override public void run() {
                readSettings();
                if (root != null) {
                    applyThemeToChrome();
                    buildBody();
                    renderCandidateBar();
                }
            }
        });
    }

    @Override
    public View onCreateInputView() {
        readSettings();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        gearBtn = new ImageView(this);
        gearBtn.setImageResource(R.drawable.ic_settings);
        gearBtn.setScaleType(ImageView.ScaleType.CENTER);
        gearBtn.setContentDescription("Cài đặt bàn phím");
        gearBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                feedback.key(v, Feedback.KIND_NORMAL);
                openSettings();
            }
        });
        bar.addView(gearBtn, new LinearLayout.LayoutParams(dp(44), dp(48)));

        candScroll = new HorizontalScrollView(this);
        candScroll.setHorizontalScrollBarEnabled(false);
        candRow = new LinearLayout(this);
        candRow.setOrientation(LinearLayout.HORIZONTAL);
        candRow.setGravity(Gravity.CENTER_VERTICAL);
        candScroll.addView(candRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bar.addView(candScroll, new LinearLayout.LayoutParams(0, dp(48), 1f));

        expandBtn = new TextView(this);
        expandBtn.setText("▼");
        expandBtn.setGravity(Gravity.CENTER);
        expandBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        expandBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!defStack.isEmpty()) {
                    closeDefinition();
                    return;
                }
                if (expanded || !cands.isEmpty() || !predictions.isEmpty()) {
                    expanded = !expanded;
                    buildBody();
                    renderCandidateBar();
                }
            }
        });
        bar.addView(expandBtn, new LinearLayout.LayoutParams(dp(44), dp(48)));
        root.addView(bar);

        body = new FrameLayout(this);
        body.setPadding(dp(3), dp(3), dp(3), dp(6));
        root.addView(body);
        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15+ draws the keyboard window edge-to-edge: keep the keys clear of the gesture handle /
            // 3-button navigation bar (like Gboard), the theme background still fills the area underneath.
            root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                    android.graphics.Insets nav = in.getInsets(WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
                    v.setPadding(nav.left, 0, nav.right, nav.bottom);
                    return in;
                }
            });
            root.requestApplyInsets();
        }
        applyThemeToChrome();
        buildBody();
        renderCandidateBar();
        return root;
    }

    private void applyThemeToChrome() {
        if (root == null) return;
        root.setBackgroundColor(theme.bg);
        bar.setBackgroundColor(theme.bar);
        expandBtn.setTextColor(theme.text);
        if (Build.VERSION.SDK_INT >= 21) gearBtn.setImageTintList(ColorStateList.valueOf(theme.textDim));
    }

    private void openSettings() {
        if (raw.length() > 0) commitPreedit();
        finishVietWord();
        requestHideSelf(0);
        Intent i = new Intent(this, SettingsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        Theme before = theme;
        readSettings();
        if (theme != before) applyThemeToChrome();
        resetComposing();
        predictions.clear();
        breakChain();
        defStack.clear();
        capsLock = false;
        expanded = false;
        int cls = info.inputType & EditorInfo.TYPE_MASK_CLASS;
        int var = info.inputType & EditorInfo.TYPE_MASK_VARIATION;
        symbols = cls == EditorInfo.TYPE_CLASS_NUMBER || cls == EditorInfo.TYPE_CLASS_PHONE || cls == EditorInfo.TYPE_CLASS_DATETIME;
        textField = cls == EditorInfo.TYPE_CLASS_TEXT
                && var != EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                && var != EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                && var != EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
                && var != EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                && var != EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                && var != EditorInfo.TYPE_TEXT_VARIATION_URI;
        noPersonalLearning = (info.imeOptions & IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
        shifted = false;
        updateAutoShift(false);
        buildBody();
        renderCandidateBar();
    }

    @Override
    public void onFinishInputView(boolean finishing) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && (raw.length() > 0 || !engine.isEmpty())) ic.finishComposingText();
        resetComposing();
        breakChain();
        defStack.clear();
        HanNomData.saveNow(this);
        super.onFinishInputView(finishing);
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candStart, int candEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd);
        if (SystemClock.uptimeMillis() - lastOwnEdit < 600) return;
        boolean composing = raw.length() > 0 || !engine.isEmpty();
        if (!composing && ownCommitLen >= 0 && SystemClock.uptimeMillis() - ownCommitAt < 3000
                && newSelStart == newSelEnd && newSelStart - oldSelStart == ownCommitLen) {
            ownCommitLen = -1;   // just our own commit arriving late
            return;
        }
        if (composing && (candStart == -1 || newSelStart != candEnd || newSelEnd != candEnd)) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
            resetComposing();
            predictions.clear();
            breakChain();
            renderCandidateBar();
            return;
        }
        if (!composing && newSelStart != oldSelStart && newSelStart != oldSelEnd) {
            breakChain();
            if (!predictions.isEmpty()) {
                predictions.clear();
                renderCandidateBar();
            }
        }
    }

    private int effectiveMode() { return textField ? mode : MODE_EN; }

    private void resetComposing() {
        raw.setLength(0);
        segs = new ArrayList<>();
        cands = new ArrayList<>();
        engine.reset();
    }

    // ------------------------------------------------------------- learning

    private UserHistory activeHistory() {
        return learnEnabled ? HanNomData.history() : null;
    }

    private boolean learningAllowed() {
        return learnEnabled && !noPersonalLearning && textField && HanNomData.history() != null && HanNomData.dict() != null;
    }

    private void breakChain() {
        lastPickText = null;
        lastPickReading = null;
    }

    /** Record a pick of (text, reading) that followed the previous pick directly. */
    private void learn(String text, String reading) {
        if (learningAllowed()) {
            UserHistory h = HanNomData.history();
            HanNomDict d = HanNomData.dict();
            h.recordPick(text, reading);
            if (lastPickText != null) {
                UserHistory.Pair p = h.recordPair(lastPickText, lastPickReading, text, reading);
                String wr = p.wordReading();
                if (!wr.isEmpty()) {
                    HanNomDict.Entry w = d.findWord(p.wordText(), wr);
                    if (w != null && !w.user) {
                        h.recordPick(w.text, w.readingKey());      // known word: rank it higher
                    } else if (w == null && p.stat.count >= UserHistory.WORD_THRESHOLD) {
                        d.addUserWord(p.wordText(), wr);           // new word learned from habit
                    }
                }
            }
            HanNomData.scheduleSave(this);
        }
        lastPickText = text;
        lastPickReading = reading;
    }

    // -------------------------------------------------------- candidate bar

    private void renderCandidateBar() {
        if (candRow == null) return;
        candRow.removeAllViews();
        candScroll.scrollTo(0, 0);
        boolean any = true;
        HanNomDict dict = HanNomData.dict();
        boolean idle = raw.length() == 0 && predictions.isEmpty();
        gearBtn.setVisibility(idle ? View.VISIBLE : View.GONE);
        if (raw.length() > 0) {
            if (dict == null) {
                candRow.addView(hint("Đang nạp từ điển…"));
                any = false;
            } else if (cands.isEmpty()) {
                candRow.addView(hint("Không có chữ cho \"" + preedit() + "\" — Enter để nhập chữ Quốc ngữ"));
                any = false;
            } else {
                for (int i = 0; i < Math.min(cands.size(), 40); i++) {
                    final HanNomDict.Candidate c = cands.get(i);
                    candRow.addView(candView(c.entry.text, i == 0, new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            feedback.key(v, Feedback.KIND_NORMAL);
                            choose(c);
                        }
                    }, longPressFor(c, null)));
                }
            }
        } else if (!predictions.isEmpty()) {
            for (final HanNomDict.Prediction p : predictions) {
                candRow.addView(candView(p.text, false, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        feedback.key(v, Feedback.KIND_NORMAL);
                        choosePrediction(p);
                    }
                }, longPressFor(null, p)));
            }
        } else {
            String s = effectiveMode() == MODE_HANNOM ? "漢喃 · Gõ âm Quốc ngữ kiểu Telex, ví dụ: vieejt → 越"
                    : effectiveMode() == MODE_VIET ? "Tiếng Việt (Telex)" : "English";
            candRow.addView(hint(s));
            any = false;
        }
        expandBtn.setText(!defStack.isEmpty() ? "⌨" : expanded ? "▲" : "▼");
        expandBtn.setAlpha(any || expanded || !defStack.isEmpty() ? 1f : 0.3f);
        if (!defStack.isEmpty()) return;
        if (expanded && !any) {
            expanded = false;
            buildBody();
        } else if (expanded) {
            buildBody();
        }
    }

    private TextView hint(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(theme.textDim);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(nomTypeface(this));
        t.setPadding(dp(6), 0, dp(12), 0);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setSingleLine(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return t;
    }

    private TextView candView(String s, boolean first, View.OnClickListener l, View.OnLongClickListener ll) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(nomTypeface(this));
        t.setTextColor(first ? theme.highlight : theme.text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, s.codePointCount(0, s.length()) > 3 ? 19 : 24);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(44));
        t.setPadding(dp(10), 0, dp(10), 0);
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        t.setOnClickListener(l);
        t.setOnLongClickListener(ll);
        t.setSoundEffectsEnabled(false);
        return t;
    }

    /** Long-press on a candidate or a suggestion opens its definition. */
    private View.OnLongClickListener longPressFor(final HanNomDict.Candidate c, final HanNomDict.Prediction p) {
        return new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                defStack.clear();
                expanded = false;
                if (c != null) {
                    defStack.add(new DefItem(c.entry.text, c.entry.readingKey(), new Runnable() {
                        @Override public void run() { choose(c); }
                    }));
                } else {
                    defStack.add(new DefItem(p.text, p.reading, new Runnable() {
                        @Override public void run() { choosePrediction(p); }
                    }));
                }
                buildBody();
                renderCandidateBar();
                return true;
            }
        };
    }

    private void closeDefinition() {
        defStack.clear();
        buildBody();
        renderCandidateBar();
    }

    private View buildGrid() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        LinearLayout row = null;
        int used = 0;
        final boolean pred = raw.length() == 0;
        int n = pred ? predictions.size() : cands.size();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final String s = pred ? predictions.get(i).text : cands.get(i).entry.text;
            String reading = pred ? "" : cands.get(i).entry.reading();
            int len = s.codePointCount(0, s.length());
            int span = len <= 1 ? 1 : (len <= 3 ? 2 : 3);
            if (row == null || used + span > 6) {
                if (row != null) fillRow(row, 6 - used);
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                col.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
                used = 0;
            }
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(keyBg(theme.key));
            TextView t = new TextView(this);
            t.setText(s);
            t.setTypeface(nomTypeface(this));
            t.setTextColor(i == 0 && !pred ? theme.highlight : theme.text);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, len > 3 ? 18 : 24);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            cell.addView(t);
            if (!reading.isEmpty()) {
                TextView r = new TextView(this);
                r.setText(reading);
                r.setTextColor(theme.textDim);
                r.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                r.setGravity(Gravity.CENTER);
                r.setSingleLine(true);
                r.setEllipsize(TextUtils.TruncateAt.END);
                cell.addView(r);
            }
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    feedback.key(v, Feedback.KIND_NORMAL);
                    expanded = false;
                    if (pred) choosePrediction(predictions.get(idx));
                    else choose(cands.get(idx));
                    buildBody();
                    renderCandidateBar();
                }
            });
            cell.setOnLongClickListener(pred ? longPressFor(null, predictions.get(idx)) : longPressFor(cands.get(idx), null));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, span);
            int m = dp(3);
            lp.setMargins(m, m, m, m);
            row.addView(cell, lp);
            used += span;
        }
        if (row != null) fillRow(row, 6 - used);
        return sv;
    }

    private void fillRow(LinearLayout row, int weight) {
        if (weight > 0) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, weight));
    }

    // ------------------------------------------------------ definition panel

    private View buildDefinitionPanel() {
        final DefItem item = defStack.get(defStack.size() - 1);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(keyBg(theme.key));
        panel.setPadding(dp(12), dp(8), dp(8), dp(4));

        Definitions defs = HanNomData.defs();
        if (defs == null) {
            panel.addView(hint("Đang tải từ điển giải nghĩa…"));
            HanNomData.loadDefs(this, new Runnable() {
                @Override public void run() { if (!defStack.isEmpty()) buildBody(); }
            });
            return panel;
        }
        Definitions.Card c = defs.card(HanNomData.dict(), item.text, item.readingKey);

        // ---- header: [‹] 越  việt / Chữ Hán · U+8D8A        [Chèn] [✕]
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        if (defStack.size() > 1) {
            TextView back = panelButton("‹", false);
            back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            back.setContentDescription("Quay lại");
            back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    defStack.remove(defStack.size() - 1);
                    buildBody();
                }
            });
            head.addView(back, new LinearLayout.LayoutParams(dp(36), dp(44)));
        }
        TextView big = new TextView(this);
        big.setText(c.text);
        big.setTypeface(nomTypeface(this));
        big.setTextColor(theme.highlight);
        big.setTextSize(TypedValue.COMPLEX_UNIT_SP, c.word ? 30 : 42);
        big.setSingleLine(true);
        big.setPadding(0, 0, dp(10), 0);
        head.addView(big);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView rd = new TextView(this);
        rd.setText(c.mainReading.isEmpty() ? "—" : c.mainReading);
        rd.setTextColor(theme.text);
        rd.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        rd.setTypeface(Typeface.DEFAULT_BOLD);
        rd.setSingleLine(true);
        rd.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(rd);
        TextView kind = new TextView(this);
        kind.setText(c.kind);
        kind.setTextColor(theme.textDim);
        kind.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        kind.setMaxLines(2);
        kind.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(kind);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (item.insert != null) {
            TextView ins = panelButton("Chèn", true);
            ins.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    feedback.key(v, Feedback.KIND_NORMAL);
                    item.insert.run();   // choose() / choosePrediction() close the panel
                }
            });
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
            ilp.leftMargin = dp(6);
            head.addView(ins, ilp);
        }
        TextView close = panelButton("✕", false);
        close.setContentDescription("Đóng");
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeDefinition(); }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(40), dp(38));
        clp.leftMargin = dp(4);
        head.addView(close, clp);
        panel.addView(head);

        // ---- scrolling content
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, dp(4), dp(8));
        sv.addView(col);
        panel.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        boolean any = false;
        if (!c.hanViet.isEmpty()) {
            col.addView(defLine("Âm Hán Việt", TextUtils.join(", ", c.hanViet)));
            any = true;
        }
        if (!c.otherReadings.isEmpty()) {
            col.addView(defLine(c.hanViet.isEmpty() ? "Âm đọc" : "Âm Nôm", TextUtils.join(", ", c.otherReadings)));
            any = true;
        }
        if (c.wordGloss != null) {
            col.addView(defTitle("Nghĩa · tiếng Anh, Wiktionary"));
            col.addView(defText(c.wordGloss.replace(" | ", "\n")));
            any = true;
        }
        if (!c.senses.isEmpty()) {
            col.addView(defTitle("Nghĩa Hán Việt · Thiều Chửu"));
            for (int i = 0; i < c.senses.size(); i++) {
                col.addView(defText((c.senses.size() > 1 ? (i + 1) + ". " : "") + c.senses.get(i)));
            }
            any = true;
        }
        if (!c.nomGlosses.isEmpty()) {
            col.addView(defTitle(c.senses.isEmpty() ? "Nghĩa · tiếng Anh, Wiktionary"
                    : "Khi dùng làm chữ Nôm · tiếng Anh, Wiktionary"));
            for (String[] g : c.nomGlosses) col.addView(defText("«" + g[0] + "»  " + g[1].replace(" | ", "; ")));
            any = true;
        }
        if (c.unihan != null) {
            col.addView(defTitle("Nghĩa chữ Hán · tiếng Anh, Unihan"));
            col.addView(defText(c.unihan));
            any = true;
        }
        if (!c.parts.isEmpty()) {
            col.addView(defTitle("Từng chữ"));
            for (final String[] p : c.parts) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(4), 0, dp(4));
                TextView ch = new TextView(this);
                ch.setText(p[0]);
                ch.setTypeface(nomTypeface(this));
                ch.setTextColor(theme.text);
                ch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                ch.setPadding(0, 0, dp(10), 0);
                row.addView(ch);
                TextView m = defText((p[1].isEmpty() ? "" : p[1] + " — ") + (p[2].isEmpty() ? "…" : p[2]));
                m.setPadding(0, 0, 0, 0);
                row.addView(m, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView arrow = new TextView(this);
                arrow.setText("›");
                arrow.setTextColor(theme.textDim);
                arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                row.addView(arrow);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        pushDefinition(p[0], p[1].isEmpty() ? "" : Definitions.syllableKey(p[1]));
                    }
                });
                col.addView(row);
            }
            any = true;
        }
        if (!c.compounds.isEmpty()) {
            col.addView(defTitle("Từ ghép có chữ này"));
            LinearLayout row = null;
            int used = 0;
            for (final String[] w : c.compounds) {
                int len = w[0].codePointCount(0, w[0].length());
                int span = len <= 2 ? 1 : 2;
                if (row == null || used + span > 3) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    col.addView(row);
                    used = 0;
                }
                LinearLayout chip = new LinearLayout(this);
                chip.setOrientation(LinearLayout.VERTICAL);
                chip.setGravity(Gravity.CENTER);
                chip.setBackground(keyBg(theme.keyFn));
                chip.setPadding(dp(4), dp(5), dp(4), dp(5));
                TextView wt = new TextView(this);
                wt.setText(w[0]);
                wt.setTypeface(nomTypeface(this));
                wt.setTextColor(theme.text);
                wt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                wt.setSingleLine(true);
                chip.addView(wt);
                TextView wr = new TextView(this);
                wr.setText(w[1]);
                wr.setTextColor(theme.textDim);
                wr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                wr.setSingleLine(true);
                wr.setEllipsize(TextUtils.TruncateAt.END);
                chip.addView(wr);
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { pushDefinition(w[0], readingKeyOf(w[1])); }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, span);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(chip, lp);
                used += span;
            }
            if (row != null && used < 3) fillRow(row, 3 - used);
            any = true;
        }
        if (!any) col.addView(defText("Chưa có dữ liệu giải nghĩa cho chữ này."));
        TextView src = new TextView(this);
        src.setText("Nguồn: Thiều Chửu, Hán Việt tự điển · Unicode Unihan · Wiktionary (CC BY-SA 4.0)");
        src.setTextColor(theme.textDim);
        src.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        src.setPadding(0, dp(10), 0, 0);
        col.addView(src);
        return panel;
    }

    private void pushDefinition(String text, String readingKey) {
        if (defStack.size() >= 12) defStack.remove(1);
        defStack.add(new DefItem(text, readingKey, null));
        buildBody();
    }

    private static String readingKeyOf(String rendered) {
        StringBuilder sb = new StringBuilder();
        for (String s : rendered.split(" ")) {
            if (s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Definitions.syllableKey(s));
        }
        return sb.toString();
    }

    private TextView panelButton(String label, boolean primary) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(primary ? theme.accentText : theme.text);
        b.setBackground(keyBg(primary ? theme.accent : theme.keyFn));
        b.setPadding(dp(14), 0, dp(14), 0);
        if (primary) b.setTypeface(Typeface.DEFAULT_BOLD);
        return b;
    }

    private TextView defTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(theme.highlight);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(10), 0, dp(3));
        return t;
    }

    private TextView defText(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(nomTypeface(this));
        t.setTextColor(theme.text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setLineSpacing(dp(2), 1f);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private View defLine(String label, String value) {
        TextView t = defText("");
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        sb.append(label).append(":  ");
        sb.setSpan(new android.text.style.ForegroundColorSpan(theme.textDim), 0, sb.length(), 0);
        sb.append(value);
        t.setText(sb);
        t.setPadding(0, dp(4), 0, 0);
        return t;
    }

    // ----------------------------------------------------------------- keys

    private int numRowHeight() { return dp(keyHeightDp - 8); }

    private int rowHeight() { return dp(keyHeightDp); }

    private void buildBody() {
        if (body == null) return;
        body.removeAllViews();
        if (!defStack.isEmpty()) {
            int h = rowHeight() * 4 + (numberRow ? numRowHeight() : 0) + dp(6);
            body.addView(buildDefinitionPanel(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
            return;
        }
        if (expanded) {
            int h = rowHeight() * 4 + (numberRow ? numRowHeight() : 0) + dp(6);
            body.addView(buildGrid(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
            return;
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        body.addView(col);
        boolean han = effectiveMode() == MODE_HANNOM;
        if (numberRow) addRow(col, NUM_ROW, 0f, numRowHeight());
        if (!symbols) {
            addRow(col, L1, 0f, rowHeight());
            addRow(col, L2, 0.5f, rowHeight());
            LinearLayout r = newRow(rowHeight());
            addKey(r, capsLock ? "⇪" : "⇧", K_SHIFT, 1.5f, true, shifted || capsLock);
            for (String s : L3) addLetterKey(r, s);
            addKey(r, "⌫", K_DEL, 1.5f, true, false);
            col.addView(r);
        } else {
            if (!numberRow) addRow(col, S0, 0f, rowHeight());
            addRow(col, S1, 0f, rowHeight());
            addRow(col, S2, 0.5f, rowHeight());
            LinearLayout r = newRow(rowHeight());
            addKey(r, "·", '·', 1.5f, true, false);
            for (String s : S3) addKey(r, s, s.charAt(0), 1f, false, false);
            addKey(r, "⌫", K_DEL, 1.5f, true, false);
            col.addView(r);
        }
        LinearLayout r = newRow(rowHeight());
        addKey(r, symbols ? "ABC" : "?123", K_SYM, 1.5f, true, false);
        addKey(r, han ? "，" : ",", han ? '，' : ',', 1f, false, false);
        String lang = effectiveMode() == MODE_HANNOM ? "漢" : effectiveMode() == MODE_VIET ? "VI" : "EN";
        addKey(r, lang, K_LANG, 1f, true, true);
        String space = effectiveMode() == MODE_HANNOM ? "Hán Nôm" : effectiveMode() == MODE_VIET ? "Tiếng Việt" : "English";
        addKey(r, space, K_SPACE, 4f, false, false);
        addKey(r, han ? "。" : ".", han ? '。' : '.', 1f, false, false);
        addKey(r, enterLabel(), K_ENTER, 1.5f, true, true);
        col.addView(r);
    }

    private String enterLabel() {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "↵";
        switch (ei.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_GO: return "Đi";
            case EditorInfo.IME_ACTION_SEARCH: return "Tìm";
            case EditorInfo.IME_ACTION_SEND: return "Gửi";
            case EditorInfo.IME_ACTION_NEXT: return "Tiếp";
            case EditorInfo.IME_ACTION_DONE: return "Xong";
            default: return "↵";
        }
    }

    private LinearLayout newRow(int h) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        return r;
    }

    private void addRow(LinearLayout parent, String[] keys, float pad, int h) {
        LinearLayout r = newRow(h);
        if (pad > 0) r.addView(new View(this), new LinearLayout.LayoutParams(0, 1, pad));
        for (String s : keys) {
            if (!symbols && s.length() == 1 && Character.isLetter(s.charAt(0))) addLetterKey(r, s);
            else addKey(r, s, s.charAt(0), 1f, false, false);
        }
        if (pad > 0) r.addView(new View(this), new LinearLayout.LayoutParams(0, 1, pad));
        parent.addView(r);
    }

    private void addLetterKey(LinearLayout r, String s) {
        String label = shifted || capsLock ? s.toUpperCase() : s;
        addKey(r, label, label.charAt(0), 1f, false, false);
    }

    private GradientDrawable keyBg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(6));
        return g;
    }

    private void addKey(LinearLayout r, String label, final int code, float weight, boolean fn, boolean on) {
        final TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        if (code == K_LANG && effectiveMode() == MODE_HANNOM) t.setTypeface(nomTypeface(this));
        float size = label.length() > 2 ? 15 : (fn ? 18 : 21);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        final boolean accent = on && (code == K_ENTER || code == K_LANG);
        final int bg = accent ? theme.accent : on ? theme.keyOn : fn ? theme.keyFn : theme.key;
        t.setTextColor(accent ? theme.accentText : theme.text);
        t.setBackground(keyBg(bg));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        int m = dp(3);
        lp.setMargins(m, m, m, m);
        t.setLayoutParams(lp);
        final int kind = code == K_DEL ? Feedback.KIND_DELETE : code == K_SPACE ? Feedback.KIND_SPACE
                : code == K_ENTER ? Feedback.KIND_RETURN : Feedback.KIND_NORMAL;
        t.setOnTouchListener(new View.OnTouchListener() {
            private Runnable repeat, longPress;
            private boolean longFired;

            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        t.setBackground(keyBg(theme.pressed));
                        feedback.key(v, kind);
                        longFired = false;
                        if (code == K_DEL) {
                            onKey(code);
                            repeat = new Runnable() {
                                @Override public void run() {
                                    onKey(K_DEL);
                                    handler.postDelayed(this, 50);
                                }
                            };
                            handler.postDelayed(repeat, 400);
                        } else if (code == K_SPACE || code == K_LANG) {
                            longPress = new Runnable() {
                                @Override public void run() {
                                    longFired = true;
                                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                                    if (imm != null) imm.showInputMethodPicker();
                                }
                            };
                            handler.postDelayed(longPress, 600);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        t.setBackground(keyBg(bg));
                        if (repeat != null) { handler.removeCallbacks(repeat); repeat = null; }
                        if (longPress != null) { handler.removeCallbacks(longPress); longPress = null; }
                        if (e.getActionMasked() == MotionEvent.ACTION_UP && code != K_DEL && !longFired) onKey(code);
                        if (code == K_DEL) updateAutoShift(true);
                        return true;
                    default:
                        return false;
                }
            }
        });
        r.addView(t);
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    // -------------------------------------------------------------- composing

    private String preedit() {
        if (segs.isEmpty()) return raw.toString();
        StringBuilder sb = new StringBuilder();
        for (HanNomDict.Syllable s : segs) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(s.display);
        }
        return sb.toString();
    }

    private void refreshHanNom() {
        InputConnection ic = getCurrentInputConnection();
        HanNomDict dict = HanNomData.dict();
        if (raw.length() == 0) {
            segs = new ArrayList<>();
            cands = new ArrayList<>();
        } else if (dict != null) {
            segs = dict.segment(raw.toString());
            cands = dict.lookup(segs, MAX_CANDS, activeHistory());
        } else {
            segs = new ArrayList<>();
            cands = new ArrayList<>();
            if (ic != null) {
                lastOwnEdit = SystemClock.uptimeMillis();
                ic.setComposingText(TelexEngine.parse(raw).render(), 1);
            }
            renderCandidateBar();
            return;
        }
        if (ic != null) {
            lastOwnEdit = SystemClock.uptimeMillis();
            if (raw.length() == 0) {
                ic.setComposingText("", 1);
                ic.finishComposingText();
            } else {
                ic.setComposingText(preedit(), 1);
            }
        }
        renderCandidateBar();
    }

    private void choose(HanNomDict.Candidate c) {
        InputConnection ic = getCurrentInputConnection();
        HanNomDict dict = HanNomData.dict();
        if (ic == null || dict == null) return;
        int used = c.syllables <= segs.size() ? segs.get(c.syllables - 1).end : raw.length();
        String rest = raw.substring(Math.min(used, raw.length()));
        while (rest.startsWith("'")) rest = rest.substring(1);
        lastOwnEdit = SystemClock.uptimeMillis();
        boolean hadPanel = !defStack.isEmpty();
        defStack.clear();
        ic.beginBatchEdit();
        ic.commitText(c.entry.text, 1);
        ownCommitLen = c.entry.text.length();
        ownCommitAt = SystemClock.uptimeMillis();
        learn(c.entry.text, c.entry.readingKey());
        raw.setLength(0);
        raw.append(rest);
        if (raw.length() > 0) {
            segs = dict.segment(raw.toString());
            cands = dict.lookup(segs, MAX_CANDS, activeHistory());
            ic.setComposingText(preedit(), 1);
            predictions.clear();
        } else {
            segs = new ArrayList<>();
            cands = new ArrayList<>();
            predictions = dict.predict(c.entry.text, c.entry.readingKey(), MAX_PREDICTIONS, activeHistory());
        }
        ic.endBatchEdit();
        if (hadPanel) buildBody();
        renderCandidateBar();
    }

    private void choosePrediction(HanNomDict.Prediction p) {
        InputConnection ic = getCurrentInputConnection();
        HanNomDict dict = HanNomData.dict();
        if (ic == null) return;
        lastOwnEdit = SystemClock.uptimeMillis();
        if (!defStack.isEmpty()) {
            defStack.clear();
            buildBody();
        }
        ic.commitText(p.text, 1);
        ownCommitLen = p.text.length();
        ownCommitAt = SystemClock.uptimeMillis();
        if (learningAllowed()) {
            UserHistory h = HanNomData.history();
            h.recordPick(p.text, p.reading);
            h.recordPair(p.prevText, p.prevReading, p.text, p.reading);
            if (p.word != null) h.recordPick(p.word.text, p.word.readingKey());
            HanNomData.scheduleSave(this);
        }
        lastPickText = p.text;
        lastPickReading = p.reading;
        predictions = dict == null ? new ArrayList<HanNomDict.Prediction>()
                : dict.predict(p.text, p.reading, MAX_PREDICTIONS, activeHistory());
        renderCandidateBar();
    }

    private void commitFirstOrPreedit() {
        if (raw.length() == 0) return;
        for (int guard = 0; raw.length() > 0 && !cands.isEmpty() && guard < 20; guard++) choose(cands.get(0));
        if (raw.length() > 0) commitPreedit();
    }

    private void commitPreedit() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        lastOwnEdit = SystemClock.uptimeMillis();
        ic.commitText(preedit(), 1);
        resetComposing();
        predictions.clear();
        breakChain();
        renderCandidateBar();
    }

    private void updateAutoShift(boolean rebuild) {
        if (capsLock || symbols || effectiveMode() == MODE_HANNOM) {
            if (effectiveMode() == MODE_HANNOM && shifted && !capsLock) {
                shifted = false;
                if (rebuild) buildBody();
            }
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        boolean want = ic != null && ei != null && ei.inputType != 0 && ic.getCursorCapsMode(ei.inputType) != 0;
        if (want != shifted) {
            shifted = want;
            if (rebuild) buildBody();
        }
    }

    private void finishVietWord() {
        if (engine.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
        engine.reset();
    }

    private void clearPredictions() {
        if (!predictions.isEmpty()) {
            predictions.clear();
            renderCandidateBar();
        }
    }

    private void onKey(int code) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        int em = effectiveMode();
        switch (code) {
            case K_DEL: {
                if (raw.length() > 0) {
                    raw.setLength(raw.length() - 1);
                    refreshHanNom();
                } else if (!engine.isEmpty()) {
                    engine.backspace();
                    if (engine.isEmpty()) {
                        ic.setComposingText("", 1);
                        ic.finishComposingText();
                    } else {
                        ic.setComposingText(engine.render(), 1);
                    }
                } else {
                    clearPredictions();
                    breakChain();
                    CharSequence sel = ic.getSelectedText(0);
                    if (sel != null && sel.length() > 0) {
                        ic.commitText("", 1);
                    } else {
                        CharSequence before = ic.getTextBeforeCursor(2, 0);
                        int n = 1;
                        if (before != null && before.length() == 2 && Character.isHighSurrogate(before.charAt(0))
                                && Character.isLowSurrogate(before.charAt(1))) n = 2;
                        ic.deleteSurroundingText(n, 0);
                    }
                }
                break;
            }
            case K_ENTER: {
                if (raw.length() > 0) {
                    commitPreedit();
                    break;
                }
                finishVietWord();
                clearPredictions();
                breakChain();
                EditorInfo ei = getCurrentInputEditorInfo();
                int action = ei == null ? EditorInfo.IME_ACTION_NONE : ei.imeOptions & EditorInfo.IME_MASK_ACTION;
                boolean newline = ei == null || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                        || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED;
                if (newline) {
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                } else {
                    ic.performEditorAction(action);
                }
                updateAutoShift(true);
                break;
            }
            case K_LANG: {
                if (raw.length() > 0) commitPreedit();
                finishVietWord();
                breakChain();
                mode = (mode + 1) % 3;
                Prefs.get(this).edit().putInt(Prefs.MODE, mode).apply();
                predictions.clear();
                shifted = false;
                updateAutoShift(false);
                buildBody();
                renderCandidateBar();
                break;
            }
            case K_SYM:
                symbols = !symbols;
                buildBody();
                break;
            case K_SHIFT: {
                long now = System.currentTimeMillis();
                if (capsLock) {
                    capsLock = false;
                    shifted = false;
                } else if (shifted && now - lastShiftTap < 400) {
                    capsLock = true;
                } else {
                    shifted = !shifted;
                }
                lastShiftTap = now;
                buildBody();
                break;
            }
            case K_SPACE: {
                if (raw.length() > 0) {
                    if (!cands.isEmpty()) {
                        commitFirstOrPreedit();
                    } else {
                        commitPreedit();
                        ic.commitText(" ", 1);
                    }
                    break;
                }
                finishVietWord();
                breakChain();
                if (!predictions.isEmpty()) {
                    predictions.clear();
                    renderCandidateBar();
                    if (em == MODE_HANNOM) break;   // first tap only dismisses suggestions
                }
                ic.commitText(" ", 1);
                updateAutoShift(true);
                break;
            }
            default: {
                char c = (char) code;
                boolean letter = Character.isLetter(c) && c < 0x2000 && !symbols;
                if (em == MODE_HANNOM && (letter || (c == '\'' && raw.length() > 0))) {
                    if (c == '\'' && raw.charAt(raw.length() - 1) == '\'') break;
                    if (raw.length() < 60) raw.append(c);
                    predictions.clear();
                    refreshHanNom();
                    if (shifted && !capsLock) {
                        shifted = false;
                        buildBody();
                    }
                } else if (em == MODE_VIET && letter) {
                    engine.input(c);
                    ic.setComposingText(engine.render(), 1);
                    if (shifted && !capsLock) {
                        shifted = false;
                        buildBody();
                    }
                } else if (letter) {
                    ic.commitText(String.valueOf(c), 1);
                    if (shifted && !capsLock) {
                        shifted = false;
                        buildBody();
                    }
                } else {
                    if (raw.length() > 0) commitFirstOrPreedit();
                    finishVietWord();
                    breakChain();
                    clearPredictions();
                    ic.commitText(String.valueOf(c), 1);
                    updateAutoShift(true);
                }
                break;
            }
        }
    }
}

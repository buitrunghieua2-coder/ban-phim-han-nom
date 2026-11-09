package vn.hannom.keyboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Process-wide dictionary + learned history, shared by the keyboard and the
 * settings screen. Loaded once on a background thread.
 */
final class HanNomData {
    private static final String HISTORY_FILE = "history.tsv";
    private static volatile HanNomDict dict;
    private static volatile UserHistory history;
    private static boolean loading;
    private static final List<Runnable> waiting = new ArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Runnable pendingSave;
    private static volatile Definitions defs;
    private static boolean defsLoading;
    private static final List<Runnable> defsWaiting = new ArrayList<>();

    private HanNomData() {}

    static HanNomDict dict() { return dict; }

    static UserHistory history() { return history; }

    static Definitions defs() { return defs; }

    /** Load the definitions (assets/nghia.txt) on first use; {@code ready} runs on the main thread. */
    static void loadDefs(final Context ctx, Runnable ready) {
        if (defs != null) {
            if (ready != null) ready.run();
            return;
        }
        if (ready != null) defsWaiting.add(ready);
        if (defsLoading) return;
        defsLoading = true;
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                Definitions d = new Definitions();
                try {
                    d.load(app.getAssets().open("nghia.txt"));
                } catch (Exception e) {
                    // keep an empty store: the panel still shows readings and compounds
                }
                final Definitions fd = d;
                main.post(new Runnable() {
                    @Override public void run() {
                        defsLoading = false;
                        defs = fd;
                        List<Runnable> rs = new ArrayList<>(defsWaiting);
                        defsWaiting.clear();
                        for (Runnable r : rs) r.run();
                    }
                });
            }
        }, "hannom-defs").start();
    }

    /** Load if needed; {@code ready} runs on the main thread once both are available. */
    static void load(final Context ctx, Runnable ready) {
        if (dict != null && history != null) {
            if (ready != null) ready.run();
            return;
        }
        if (ready != null) waiting.add(ready);
        if (loading) return;
        loading = true;
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                HanNomDict d = null;
                UserHistory h = new UserHistory();
                try {
                    h.load(new File(app.getFilesDir(), HISTORY_FILE));
                    d = new HanNomDict();
                    d.load(app.getAssets().open("dict.txt"));
                    d.syncUserWords(h);
                } catch (Exception e) {
                    d = null;
                }
                final HanNomDict fd = d;
                final UserHistory fh = h;
                main.post(new Runnable() {
                    @Override public void run() {
                        loading = false;
                        history = fh;
                        if (fd != null) dict = fd;
                        List<Runnable> rs = new ArrayList<>(waiting);
                        waiting.clear();
                        for (Runnable r : rs) r.run();
                    }
                });
            }
        }, "hannom-load").start();
    }

    /** Debounced save (main thread); written off the UI thread. */
    static void scheduleSave(final Context ctx) {
        if (history == null) return;
        if (pendingSave != null) main.removeCallbacks(pendingSave);
        final File f = new File(ctx.getApplicationContext().getFilesDir(), HISTORY_FILE);
        pendingSave = new Runnable() {
            @Override public void run() {
                pendingSave = null;
                if (history == null) return;
                final String data = history.serialize();
                new Thread(new Runnable() {
                    @Override public void run() {
                        synchronized (HanNomData.class) { UserHistory.write(f, data); }
                    }
                }, "hannom-save").start();
            }
        };
        main.postDelayed(pendingSave, 2500);
    }

    static void saveNow(Context ctx) {
        if (pendingSave != null) {
            main.removeCallbacks(pendingSave);
            pendingSave.run();
        }
    }

    static void clearLearned(Context ctx) {
        if (pendingSave != null) {
            main.removeCallbacks(pendingSave);
            pendingSave = null;
        }
        if (history != null) history.clear();
        if (dict != null) dict.removeUserWords();
        final File f = new File(ctx.getApplicationContext().getFilesDir(), HISTORY_FILE);
        new Thread(new Runnable() {
            @Override public void run() {
                synchronized (HanNomData.class) { f.delete(); }
            }
        }, "hannom-clear").start();
    }
}

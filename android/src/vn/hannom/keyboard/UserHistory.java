package vn.hannom.keyboard;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the user actually picks, stored only on the device (no network).
 * - picks: how often / how recently each (character-or-word, reading) was chosen
 * - pairs: which item was chosen right after which, used for next-word
 *   suggestions and to learn new compound words.
 * Pure Java so it can be unit-tested on a desktop JVM.
 */
public class UserHistory {
    static final int MAX_PICKS = 6000;
    static final int MAX_PAIRS = 6000;
    /** A pair seen this many times becomes a typeable compound word. */
    static final int WORD_THRESHOLD = 2;

    public static final class Stat {
        int count;
        long last;
    }

    public static final class Pair {
        final String aText, aReading, bText, bReading;
        final Stat stat = new Stat();

        Pair(String aText, String aReading, String bText, String bReading) {
            this.aText = aText;
            this.aReading = aReading;
            this.bText = bText;
            this.bReading = bReading;
        }

        String key() { return aText + '\u0001' + aReading + '\u0001' + bText + '\u0001' + bReading; }

        public String wordText() { return aText + bText; }

        public String wordReading() {
            if (aReading.isEmpty() || bReading.isEmpty()) return "";
            return aReading + " " + bReading;
        }
    }

    private final Map<String, Stat> picks = new HashMap<>();
    private final Map<String, Pair> pairs = new LinkedHashMap<>();
    private final Map<Integer, List<Pair>> pairsByLastChar = new HashMap<>();
    private long tick = 0;

    public static String key(String text, String reading) { return text + '\t' + reading; }

    // ---------------------------------------------------------------- scoring

    /** Bonus subtracted from a candidate's cost (higher = shown earlier). */
    public int bonus(String key) { return bonus(picks.get(key)); }

    int bonus(Stat s) {
        if (s == null || s.count <= 0) return 0;
        double b = Math.min(2000, 500 + 400 * Math.log(s.count));
        long age = tick - s.last;
        if (age <= 20) b += 400;
        else if (age < 200) b += 400.0 * (200 - age) / 180.0;
        return (int) b;
    }

    /**
     * Bonus for a learned pair in next-word suggestions. Weaker than {@link #bonus}:
     * one accidental pair should not push a common dictionary word aside,
     * a pair typed twice or more should.
     */
    public int pairBonus(Pair p) {
        Stat s = p.stat;
        if (s.count <= 0) return 0;
        double b = Math.min(1600, 450 * Math.log(1 + s.count));
        long age = tick - s.last;
        if (age <= 20) b += 150;
        else if (age < 200) b += 150.0 * (200 - age) / 180.0;
        return (int) b;
    }

    public int size() { return picks.size(); }

    public int pairCount() { return pairs.size(); }

    /** Pairs whose first item ends with the given code point (for next-word suggestions). */
    public List<Pair> pairsAfter(int codePoint) {
        List<Pair> l = pairsByLastChar.get(codePoint);
        return l == null ? Collections.<Pair>emptyList() : l;
    }

    /** Pairs that have been seen often enough to act as compound words. */
    public List<Pair> learnedWords() {
        List<Pair> out = new ArrayList<>();
        for (Pair p : pairs.values()) {
            if (p.stat.count >= WORD_THRESHOLD && !p.wordReading().isEmpty()) out.add(p);
        }
        return out;
    }

    // --------------------------------------------------------------- learning

    public void recordPick(String text, String reading) {
        tick++;
        Stat s = picks.get(key(text, reading));
        if (s == null) {
            s = new Stat();
            picks.put(key(text, reading), s);
        }
        s.count++;
        s.last = tick;
        if (picks.size() > MAX_PICKS) trimPicks();
    }

    /** @return the pair (with its updated count) */
    public Pair recordPair(String aText, String aReading, String bText, String bReading) {
        Pair probe = new Pair(aText, aReading, bText, bReading);
        Pair p = pairs.get(probe.key());
        if (p == null) {
            p = probe;
            pairs.put(p.key(), p);
            index(p);
        }
        p.stat.count++;
        p.stat.last = tick;
        if (pairs.size() > MAX_PAIRS) trimPairs();
        return p;
    }

    public void clear() {
        picks.clear();
        pairs.clear();
        pairsByLastChar.clear();
        tick = 0;
    }

    private void index(Pair p) {
        if (p.aText.isEmpty()) return;
        int cp = p.aText.codePointBefore(p.aText.length());
        List<Pair> l = pairsByLastChar.get(cp);
        if (l == null) {
            l = new ArrayList<>();
            pairsByLastChar.put(cp, l);
        }
        l.add(p);
    }

    private static double keep(Stat s, long tick) {
        return Math.log(1 + s.count) - (tick - s.last) / 3000.0;
    }

    private void trimPicks() {
        List<Map.Entry<String, Stat>> all = new ArrayList<>(picks.entrySet());
        final long t = tick;
        Collections.sort(all, new Comparator<Map.Entry<String, Stat>>() {
            @Override public int compare(Map.Entry<String, Stat> a, Map.Entry<String, Stat> b) {
                return Double.compare(keep(a.getValue(), t), keep(b.getValue(), t));
            }
        });
        for (int i = 0; i < all.size() - MAX_PICKS * 9 / 10; i++) picks.remove(all.get(i).getKey());
    }

    private void trimPairs() {
        List<Pair> all = new ArrayList<>(pairs.values());
        final long t = tick;
        Collections.sort(all, new Comparator<Pair>() {
            @Override public int compare(Pair a, Pair b) {
                return Double.compare(keep(a.stat, t), keep(b.stat, t));
            }
        });
        for (int i = 0; i < all.size() - MAX_PAIRS * 9 / 10; i++) pairs.remove(all.get(i).key());
        pairsByLastChar.clear();
        for (Pair p : pairs.values()) index(p);
    }

    // ------------------------------------------------------------ persistence

    private static String esc(String s) { return s.replace('\t', ' ').replace('\n', ' '); }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("#hannom-history\t1\t").append(tick).append('\n');
        for (Map.Entry<String, Stat> e : picks.entrySet()) {
            String[] k = e.getKey().split("\t", -1);
            sb.append("P\t").append(esc(k[0])).append('\t').append(esc(k.length > 1 ? k[1] : ""))
              .append('\t').append(e.getValue().count).append('\t').append(e.getValue().last).append('\n');
        }
        for (Pair p : pairs.values()) {
            sb.append("R\t").append(esc(p.aText)).append('\t').append(esc(p.aReading)).append('\t')
              .append(esc(p.bText)).append('\t').append(esc(p.bReading)).append('\t')
              .append(p.stat.count).append('\t').append(p.stat.last).append('\n');
        }
        return sb.toString();
    }

    public void parse(BufferedReader r) throws IOException {
        clear();
        String line;
        while ((line = r.readLine()) != null) {
            String[] f = line.split("\t", -1);
            try {
                if (f[0].equals("#hannom-history") && f.length >= 3) {
                    tick = Long.parseLong(f[2]);
                } else if (f[0].equals("P") && f.length >= 5) {
                    Stat s = new Stat();
                    s.count = Integer.parseInt(f[3]);
                    s.last = Long.parseLong(f[4]);
                    picks.put(key(f[1], f[2]), s);
                } else if (f[0].equals("R") && f.length >= 7) {
                    Pair p = new Pair(f[1], f[2], f[3], f[4]);
                    p.stat.count = Integer.parseInt(f[5]);
                    p.stat.last = Long.parseLong(f[6]);
                    pairs.put(p.key(), p);
                    index(p);
                }
            } catch (NumberFormatException ignored) {
                // skip a damaged line, keep the rest
            }
        }
    }

    public void load(File f) {
        if (!f.exists()) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            parse(r);
        } catch (IOException e) {
            clear();
        }
    }

    /** Atomic write: temp file then rename, so a crash never leaves a half file. */
    public static void write(File f, String data) {
        File tmp = new File(f.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            w.write(data);
        } catch (IOException e) {
            return;
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            tmp.renameTo(f);
        }
    }
}

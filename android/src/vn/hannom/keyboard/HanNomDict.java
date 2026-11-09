package vn.hannom.keyboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dictionary, syllable segmentation and candidate ranking.
 *
 * v1.1 ranking: each entry carries a pre-computed cost (lower = more common;
 * see tools/build_dict.py). At lookup time we add a match cost (missing tone,
 * missing diacritics, unfinished syllable) and subtract the user's learned
 * bonus from {@link UserHistory}.
 */
public class HanNomDict {
    /** Cost of a syllable match: exact, tone left out, marks left out, prefix only. */
    static final int[] MATCH_COST = {0, 250, 450, 700};
    /** Base cost for compound words learned from the user's own typing. */
    static final int USER_WORD_COST = 900;
    static final int USER_PAIR_COST = 1900;

    private final Map<String, List<Entry>> byFirstAscii = new HashMap<>();
    private final Map<Integer, List<Entry>> wordsByFirstChar = new HashMap<>();
    private final Map<String, Entry> wordsByKey = new HashMap<>();
    private final Map<String, List<Entry>> byText = new HashMap<>();
    private final Map<Integer, List<Entry>> wordsByChar = new HashMap<>();
    private final List<Entry> userWords = new ArrayList<>();
    private final Set<String> bases = new HashSet<>();
    private final Set<String> asciis = new HashSet<>();
    private final Set<String> basePrefixes = new HashSet<>();
    private final Set<String> asciiPrefixes = new HashSet<>();

    public static class Entry {
        public final String text;
        final String readingKey;   // "viêt:5 nam:0"
        final String[] bases;
        final int[] tones;
        final String[] asciis;
        final int cost;
        final boolean user;

        Entry(String text, String readingKey, String[] bases, int[] tones, int cost, boolean user) {
            this.text = text;
            this.readingKey = readingKey;
            this.bases = bases;
            this.tones = tones;
            this.cost = cost;
            this.user = user;
            this.asciis = new String[bases.length];
            for (int i = 0; i < bases.length; i++) asciis[i] = TelexEngine.ascii(bases[i]);
        }

        public int length() { return bases.length; }

        public String key() { return UserHistory.key(text, readingKey); }

        public String readingKey() { return readingKey; }

        /** Human-readable reading, e.g. "việt nam". */
        public String reading() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bases.length; i++) {
                if (i > 0) sb.append(' ');
                sb.append(TelexEngine.renderSyllable(bases[i], tones[i]));
            }
            return sb.toString();
        }

        /** Reading of syllables [from, length). */
        String readingKeyFrom(int from) {
            StringBuilder sb = new StringBuilder();
            for (int i = from; i < bases.length; i++) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(bases[i]).append(':').append(tones[i]);
            }
            return sb.toString();
        }
    }

    public static class Syllable {
        public final int start, end;
        public final String base, ascii, display;
        public final int tone;
        public final boolean complete;

        Syllable(int start, int end, TelexEngine e, boolean complete) {
            this.start = start;
            this.end = end;
            this.base = e.base();
            this.ascii = TelexEngine.ascii(base);
            this.tone = e.tone();
            this.complete = complete;
            this.display = e.render();
        }

        private Syllable(Syllable s, int shift) {
            start = s.start + shift;
            end = s.end + shift;
            base = s.base;
            ascii = s.ascii;
            tone = s.tone;
            complete = s.complete;
            display = s.display;
        }

        Syllable shift(int d) { return d == 0 ? this : new Syllable(this, d); }
    }

    public static class Candidate {
        public final Entry entry;
        public final int syllables;
        final int score;

        Candidate(Entry entry, int syllables, int score) {
            this.entry = entry;
            this.syllables = syllables;
            this.score = score;
        }
    }

    /** A next-word suggestion shown after a character is committed. */
    public static class Prediction {
        public final String text;      // the part to insert
        final String reading;          // reading of {@link #text}, may be ""
        final String prevText;         // the committed char it follows
        final String prevReading;
        final Entry word;              // dictionary word prev+text, or null
        final int score;

        Prediction(String text, String reading, String prevText, String prevReading, Entry word, int score) {
            this.text = text;
            this.reading = reading;
            this.prevText = prevText;
            this.prevReading = prevReading;
            this.word = word;
            this.score = score;
        }
    }

    private static final Comparator<Candidate> ORDER = new Comparator<Candidate>() {
        @Override public int compare(Candidate a, Candidate b) {
            if (a.syllables != b.syllables) return b.syllables - a.syllables;
            return Integer.compare(a.score, b.score);
        }
    };

    // ------------------------------------------------------------------ load

    public void load(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 65536);
        String line;
        while ((line = r.readLine()) != null) {
            int t1 = line.indexOf('\t');
            int t2 = line.indexOf('\t', t1 + 1);
            if (t1 < 0 || t2 < 0) continue;
            Entry e = parseEntry(line.substring(0, t1), line.substring(t1 + 1, t2),
                    Integer.parseInt(line.substring(t2 + 1).trim()), false);
            if (e != null) add(e);
        }
        r.close();
    }

    private static Entry parseEntry(String text, String readingKey, int cost, boolean user) {
        String[] parts = readingKey.split(" ");
        String[] b = new String[parts.length];
        int[] t = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            int c = parts[i].indexOf(':');
            if (c <= 0 || c + 1 >= parts[i].length()) return null;
            b[i] = parts[i].substring(0, c);
            t[i] = parts[i].charAt(c + 1) - '0';
            if (t[i] < 0 || t[i] > 5) return null;
        }
        return new Entry(text, readingKey, b, t, cost, user);
    }

    private void add(Entry e) {
        addTo(byFirstAscii, e.asciis[0], e);
        addTo(byText, e.text, e);
        if (e.length() > 1) {
            Set<Integer> cps = new HashSet<>();
            for (int off = 0; off < e.text.length(); ) {
                int cp = e.text.codePointAt(off);
                off += Character.charCount(cp);
                if (cps.add(cp)) {
                    List<Entry> wl = wordsByChar.get(cp);
                    if (wl == null) {
                        wl = new ArrayList<>();
                        wordsByChar.put(cp, wl);
                    }
                    wl.add(e);
                }
            }
            int cp = e.text.codePointAt(0);
            List<Entry> l = wordsByFirstChar.get(cp);
            if (l == null) {
                l = new ArrayList<>();
                wordsByFirstChar.put(cp, l);
            }
            l.add(e);
            wordsByKey.put(e.key(), e);
        }
        for (int i = 0; i < e.bases.length; i++) {
            if (bases.add(e.bases[i])) {
                for (int k = 1; k <= e.bases[i].length(); k++) basePrefixes.add(e.bases[i].substring(0, k));
            }
            if (asciis.add(e.asciis[i])) {
                for (int k = 1; k <= e.asciis[i].length(); k++) asciiPrefixes.add(e.asciis[i].substring(0, k));
            }
        }
    }

    private static void addTo(Map<String, List<Entry>> m, String k, Entry e) {
        List<Entry> l = m.get(k);
        if (l == null) {
            l = new ArrayList<>();
            m.put(k, l);
        }
        l.add(e);
    }

    // ------------------------------------------------------- learned words

    public Entry findWord(String text, String readingKey) {
        return wordsByKey.get(UserHistory.key(text, readingKey));
    }

    /** Make a compound the user keeps typing available as a single candidate. */
    public boolean addUserWord(String text, String readingKey) {
        if (readingKey.isEmpty() || findWord(text, readingKey) != null) return false;
        Entry e = parseEntry(text, readingKey, USER_WORD_COST, true);
        if (e == null || e.length() < 2 || e.length() > 4) return false;
        if (text.codePointCount(0, text.length()) != e.length()) return false;
        add(e);
        userWords.add(e);
        return true;
    }

    public void syncUserWords(UserHistory h) {
        for (UserHistory.Pair p : h.learnedWords()) addUserWord(p.wordText(), p.wordReading());
    }

    public void removeUserWords() {
        for (Entry e : userWords) {
            List<Entry> l = byFirstAscii.get(e.asciis[0]);
            if (l != null) l.remove(e);
            l = wordsByFirstChar.get(e.text.codePointAt(0));
            if (l != null) l.remove(e);
            wordsByKey.remove(e.key());
            l = byText.get(e.text);
            if (l != null) l.remove(e);
            for (int off = 0; off < e.text.length(); ) {
                int cp = e.text.codePointAt(off);
                off += Character.charCount(cp);
                l = wordsByChar.get(cp);
                if (l != null) l.remove(e);
            }
        }
        userWords.clear();
    }

    public int userWordCount() { return userWords.size(); }

    // ------------------------------------------------------------ for the definition panel

    private static final Comparator<Entry> BY_COST = new Comparator<Entry>() {
        @Override public int compare(Entry a, Entry b) { return Integer.compare(a.cost, b.cost); }
    };

    /** All dictionary entries written exactly as {@code text}, most common reading first. */
    public List<Entry> entriesFor(String text) {
        List<Entry> l = byText.get(text);
        if (l == null) return new ArrayList<>();
        List<Entry> out = new ArrayList<>(l);
        Collections.sort(out, BY_COST);
        return out;
    }

    /** Compound words containing the character, most common first. */
    public List<Entry> wordsContaining(String ch, int limit) {
        List<Entry> out = new ArrayList<>();
        if (ch.isEmpty()) return out;
        List<Entry> l = wordsByChar.get(ch.codePointAt(0));
        if (l == null) return out;
        out.addAll(l);
        Collections.sort(out, BY_COST);
        Set<String> seen = new HashSet<>();
        List<Entry> uniq = new ArrayList<>();
        for (Entry e : out) {
            if (seen.add(e.text)) uniq.add(e);
            if (uniq.size() >= limit) break;
        }
        return uniq;
    }

    // ---------------------------------------------------------- segmentation

    private static boolean hasMarks(String s) { return !s.equals(TelexEngine.ascii(s)); }

    private boolean isComplete(TelexEngine e) {
        if (e.isRaw()) return false;
        String b = e.base();
        return hasMarks(b) ? bases.contains(b) : asciis.contains(b);
    }

    private boolean isPrefix(TelexEngine e) {
        if (e.isRaw()) return false;
        String b = e.base();
        return hasMarks(b) ? basePrefixes.contains(b) : asciiPrefixes.contains(b);
    }

    /** Split raw Telex input into syllables; an apostrophe forces a boundary. */
    public List<Syllable> segment(String s) {
        List<Syllable> out = new ArrayList<>();
        int from = 0;
        while (from <= s.length()) {
            int q = s.indexOf('\'', from);
            int to = q < 0 ? s.length() : q;
            if (to > from) {
                for (Syllable y : segmentPart(s.substring(from, to))) out.add(y.shift(from));
            }
            if (q < 0) break;
            from = q + 1;
        }
        return out;
    }

    private static final int INF = 1 << 20;

    private List<Syllable> segmentPart(String s) {
        int n = s.length();
        List<Syllable> out = new ArrayList<>();
        if (n == 0) return out;
        String lo = s.toLowerCase();
        int[] cost = new int[n + 1];
        int[] back = new int[n + 1];
        Arrays.fill(cost, INF);
        cost[0] = 0;
        // fewest syllables wins; starting a syllable with a vowel is discouraged
        for (int j = 1; j <= n; j++) {
            for (int i = 0; i < j; i++) {
                if (cost[i] >= INF || j - i > 10) continue;
                TelexEngine e = TelexEngine.parse(lo.substring(i, j));
                int c = cost[i] + 4 + (i > 0 && TelexEngine.isVowel(lo.charAt(i)) ? 1 : 0);
                if (isComplete(e) && c <= cost[j]) {
                    cost[j] = c;
                    back[j] = i;
                }
            }
        }
        int bestStart = -1, best = INF;
        boolean bestComplete = false;
        for (int i = 0; i < n; i++) {
            if (cost[i] >= INF || n - i > 10) continue;
            TelexEngine e = TelexEngine.parse(lo.substring(i, n));
            int c = cost[i] + (i > 0 && TelexEngine.isVowel(lo.charAt(i)) ? 1 : 0);
            boolean complete;
            if (isComplete(e)) { c += 4; complete = true; }
            else if (isPrefix(e)) { c += 6; complete = false; }
            else continue;
            if (c <= best) {
                best = c;
                bestStart = i;
                bestComplete = complete;
            }
        }
        if (bestStart < 0) {
            out.add(new Syllable(0, n, TelexEngine.parse(s), false));
            return out;
        }
        List<int[]> spans = new ArrayList<>();
        spans.add(new int[]{bestStart, n, bestComplete ? 1 : 0});
        for (int i = bestStart; i > 0; i = back[i]) spans.add(new int[]{back[i], i, 1});
        Collections.reverse(spans);
        for (int[] sp : spans) {
            out.add(new Syllable(sp[0], sp[1], TelexEngine.parse(s.substring(sp[0], sp[1])), sp[2] == 1));
        }
        return out;
    }

    // ---------------------------------------------------------------- lookup

    /** @return match level 0..3 (see MATCH_COST) or -1 if it does not match. */
    static int match(Syllable y, Entry e, int i, boolean last) {
        String b = e.bases[i];
        int t = e.tones[i];
        boolean marks = hasMarks(y.base);
        if (y.base.equals(b)) {
            if (y.tone == t) return 0;
            return y.tone == 0 ? 1 : -1;
        }
        if (!marks && y.ascii.equals(e.asciis[i])) {
            if (y.tone == t) return 1;
            return y.tone == 0 ? 2 : -1;
        }
        if (last && !y.complete) {
            boolean pre = marks ? b.startsWith(y.base) : e.asciis[i].startsWith(y.ascii);
            if (pre && (y.tone == 0 || y.tone == t)) return 3;
        }
        return -1;
    }

    /**
     * @param history may be null (learning switched off)
     */
    public List<Candidate> lookup(List<Syllable> segs, int limit, UserHistory history) {
        List<Candidate> words = new ArrayList<>();
        List<Candidate> singles = new ArrayList<>();
        if (segs.isEmpty()) return new ArrayList<>();
        int n = segs.size();
        Syllable first = segs.get(0);
        boolean only = n == 1;
        List<List<Entry>> buckets = new ArrayList<>();
        if (first.complete || !only) {
            List<Entry> l = byFirstAscii.get(first.ascii);
            if (l != null) buckets.add(l);
        } else {
            for (Map.Entry<String, List<Entry>> me : byFirstAscii.entrySet()) {
                if (me.getKey().startsWith(first.ascii)) buckets.add(me.getValue());
            }
        }
        for (List<Entry> bucket : buckets) {
            for (Entry e : bucket) {
                if (e.length() == 1) {
                    int m = match(first, e, 0, only);
                    if (m >= 0) singles.add(new Candidate(e, 1, score(e, MATCH_COST[m], history)));
                } else if (e.length() == n) {
                    int total = 0;
                    for (int i = 0; i < n && total >= 0; i++) {
                        int m = match(segs.get(i), e, i, i == n - 1);
                        total = m < 0 ? -1 : total + MATCH_COST[m];
                    }
                    if (total >= 0) words.add(new Candidate(e, n, score(e, total, history)));
                }
            }
        }
        Collections.sort(words, ORDER);
        Collections.sort(singles, ORDER);
        List<Candidate> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Candidate c : words) {
            if (out.size() >= limit / 3) break;
            if (seen.add(c.entry.text)) out.add(c);
        }
        for (Candidate c : singles) {
            if (out.size() >= limit) break;
            if (seen.add(c.entry.text)) out.add(c);
        }
        return out;
    }

    private static int score(Entry e, int matchCost, UserHistory h) {
        int s = e.cost + matchCost;
        if (h != null) s -= h.bonus(e.key());
        return s;
    }

    // ------------------------------------------------------------ prediction

    /**
     * Next-word suggestions after committing {@code prev} (whose reading is
     * {@code prevReading}, may be ""). Mixes dictionary compounds with pairs
     * the user has typed before.
     */
    public List<Prediction> predict(String prev, String prevReading, int limit, UserHistory h) {
        List<Prediction> all = new ArrayList<>();
        if (prev == null || prev.isEmpty()) return all;
        int cp = prev.codePointBefore(prev.length());
        String lastChar = new String(Character.toChars(cp));
        String lastReading = lastSyllable(prevReading);
        List<Entry> l = wordsByFirstChar.get(cp);
        if (l != null) {
            int cc = Character.charCount(cp);
            for (Entry e : l) {
                String rest = e.text.substring(cc);
                if (rest.isEmpty()) continue;
                int s = e.cost - (h != null ? h.bonus(e.key()) : 0);
                // the word's first syllable matches how the char was actually read
                if (!lastReading.isEmpty() && !lastReading.equals(e.readingKeyFrom(0).split(" ")[0])) s += 300;
                all.add(new Prediction(rest, e.readingKeyFrom(1), lastChar, e.readingKeyFrom(0).split(" ")[0], e, s));
            }
        }
        if (h != null) {
            for (UserHistory.Pair p : h.pairsAfter(cp)) {
                int s = USER_PAIR_COST - h.pairBonus(p);
                Entry w = p.wordReading().isEmpty() ? null : findWord(p.wordText(), p.wordReading());
                all.add(new Prediction(p.bText, p.bReading, lastChar, lastSyllable(p.aReading), w, s));
            }
        }
        Collections.sort(all, new Comparator<Prediction>() {
            @Override public int compare(Prediction a, Prediction b) { return Integer.compare(a.score, b.score); }
        });
        List<Prediction> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Prediction p : all) {
            if (seen.add(p.text)) out.add(p);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static String lastSyllable(String readingKey) {
        if (readingKey == null || readingKey.isEmpty()) return "";
        int sp = readingKey.lastIndexOf(' ');
        return sp < 0 ? readingKey : readingKey.substring(sp + 1);
    }
}

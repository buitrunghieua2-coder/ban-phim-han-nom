package vn.hannom.keyboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline definitions for the long-press panel (assets/nghia.txt, built by
 * tools/build_nghia.py):
 *   Thiều Chửu "Hán Việt tự điển" (Vietnamese), Unihan kDefinition (English)
 *   and Wiktionary glosses for Vietnamese words (English), keyed by reading.
 * Pure Java so it can be tested on a desktop JVM.
 */
public class Definitions {
    private final Map<String, String[]> han = new HashMap<>();   // char -> {hv readings, definition}
    private final Map<String, String> unihan = new HashMap<>();  // char -> english definition
    private final Map<String, String> gloss = new HashMap<>();   // reading key -> english gloss

    public void load(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 65536);
        String line;
        while ((line = r.readLine()) != null) {
            String[] f = line.split("\t", -1);
            if (f.length < 3) continue;
            switch (f[0]) {
                case "H": if (f.length >= 4) han.put(f[1], new String[]{f[2], f[3]}); break;
                case "U": unihan.put(f[1], f[2]); break;
                case "W": gloss.put(f[1], f[2]); break;
                default: break;
            }
        }
        r.close();
    }

    public int size() { return han.size() + unihan.size() + gloss.size(); }

    // ------------------------------------------------------------ helpers

    private static final String[] TONED = {"aàáảãạ", "ăằắẳẵặ", "âầấẩẫậ", "eèéẻẽẹ", "êềếểễệ", "iìíỉĩị",
            "oòóỏõọ", "ôồốổỗộ", "ơờớởỡợ", "uùúủũụ", "ưừứửữự", "yỳýỷỹỵ"};

    /** "người" -> "ngươi:1" (same key format as the dictionary). */
    static String syllableKey(String syl) {
        StringBuilder b = new StringBuilder();
        int tone = 0;
        String s = syl.trim().toLowerCase();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean found = false;
            for (String row : TONED) {
                int t = row.indexOf(c);
                if (t >= 0) {
                    if (t > 0) tone = t;
                    b.append(row.charAt(0));
                    found = true;
                    break;
                }
            }
            if (!found) b.append(c);
        }
        return b.length() == 0 ? "" : b + ":" + tone;
    }

    private static final Pattern SENSE = Pattern.compile("(^|\\s)(\\d{1,2}) : ");

    /** Split "1 : Một… 2 : Cùng…" into numbered senses (only 1,2,3… in order). */
    static List<String> senses(String def) {
        List<String> out = new ArrayList<>();
        Matcher m = SENSE.matcher(def);
        int expect = 1, lastStart = -1;
        while (m.find()) {
            if (Integer.parseInt(m.group(2)) != expect) continue;
            if (lastStart >= 0) out.add(def.substring(lastStart, m.start()).trim());
            lastStart = m.end();
            expect++;
        }
        if (lastStart < 0) out.add(def.trim());
        else out.add(def.substring(lastStart).trim());
        return out;
    }

    static String blockName(int cp) {
        if (cp >= 0x4E00 && cp <= 0x9FFF) return "CJK cơ bản";
        if (cp >= 0x3400 && cp <= 0x4DBF) return "CJK mở rộng A";
        if (cp >= 0x20000 && cp <= 0x2A6DF) return "CJK mở rộng B";
        if (cp >= 0x2A700 && cp <= 0x2B73F) return "CJK mở rộng C";
        if (cp >= 0x2B740 && cp <= 0x2B81F) return "CJK mở rộng D";
        if (cp >= 0x2B820 && cp <= 0x2CEAF) return "CJK mở rộng E";
        if (cp >= 0x2CEB0 && cp <= 0x2EBEF) return "CJK mở rộng F";
        if (cp >= 0x2EBF0 && cp <= 0x2EE5F) return "CJK mở rộng I";
        if (cp >= 0x30000 && cp <= 0x3134F) return "CJK mở rộng G";
        if (cp >= 0x31350 && cp <= 0x323AF) return "CJK mở rộng H";
        if ((cp >= 0xF900 && cp <= 0xFAFF) || (cp >= 0x2F800 && cp <= 0x2FA1F)) return "chữ tương thích";
        return "";
    }

    static String codePoint(int cp) { return String.format("U+%04X", cp); }

    private static String render(String readingKey) {
        StringBuilder sb = new StringBuilder();
        for (String p : readingKey.split(" ")) {
            int c = p.indexOf(':');
            if (c <= 0) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(TelexEngine.renderSyllable(p.substring(0, c), p.charAt(c + 1) - '0'));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------- result

    /** What the panel shows for one character or word. */
    public static final class Card {
        public String text;
        public boolean word;
        public String kind = "";                        // "Chữ Hán · U+8D8A · CJK cơ bản"
        public String mainReading = "";                 // reading the user typed / first reading
        public final List<String> hanViet = new ArrayList<>();
        public final List<String> otherReadings = new ArrayList<>();   // Nôm readings
        public final List<String> senses = new ArrayList<>();          // Thiều Chửu
        public String unihan;                                          // English, fallback
        public final List<String[]> nomGlosses = new ArrayList<>();    // {reading, english gloss}
        public String wordGloss;                                       // English gloss for a word
        public final List<String[]> parts = new ArrayList<>();         // word: {char, reading, short meaning}
        public final List<String[]> compounds = new ArrayList<>();     // {word, reading}
    }

    /**
     * @param readingKey the reading the item was picked with (may be null/empty)
     */
    public Card card(HanNomDict dict, String text, String readingKey) {
        Card c = new Card();
        c.text = text;
        int n = text.codePointCount(0, text.length());
        c.word = n > 1;
        if (c.word) return wordCard(dict, c, readingKey);

        int cp = text.codePointAt(0);
        String[] h = han.get(text);
        Set<String> hvKeys = new HashSet<>();
        if (h != null) {
            for (String r : h[0].split(",")) {
                String t = r.trim();
                if (t.isEmpty()) continue;
                c.hanViet.add(t);
                hvKeys.add(syllableKey(t));
            }
            c.senses.addAll(senses(h[1]));
        }
        List<HanNomDict.Entry> es = dict == null ? new ArrayList<HanNomDict.Entry>() : dict.entriesFor(text);
        Set<String> other = new LinkedHashSet<>();
        for (HanNomDict.Entry e : es) {
            if (!hvKeys.contains(e.readingKey())) other.add(e.readingKey());
        }
        for (String k : other) {
            String disp = render(k);
            c.otherReadings.add(disp);
            String g = gloss.get(k);
            if (g != null && c.nomGlosses.size() < 4) c.nomGlosses.add(new String[]{disp, g});
        }
        if (h == null) c.unihan = unihan.get(text);

        if (readingKey != null && !readingKey.isEmpty()) c.mainReading = render(readingKey);
        else if (!c.hanViet.isEmpty()) c.mainReading = c.hanViet.get(0);
        else if (!c.otherReadings.isEmpty()) c.mainReading = c.otherReadings.get(0);

        String type;
        if (h != null && !c.otherReadings.isEmpty()) type = "Chữ Hán · cũng dùng làm chữ Nôm";
        else if (h != null) type = "Chữ Hán";
        else if (c.unihan != null && !c.otherReadings.isEmpty()) type = "Chữ Hán / Nôm";
        else type = "Chữ Nôm";
        String block = blockName(cp);
        c.kind = type + " · " + codePoint(cp) + (block.isEmpty() ? "" : " · " + block);

        if (dict != null) {
            for (HanNomDict.Entry w : dict.wordsContaining(text, 14)) c.compounds.add(new String[]{w.text, w.reading()});
        }
        return c;
    }

    private Card wordCard(HanNomDict dict, Card c, String readingKey) {
        String key = readingKey;
        if ((key == null || key.isEmpty()) && dict != null) {
            List<HanNomDict.Entry> es = dict.entriesFor(c.text);
            if (!es.isEmpty()) key = es.get(0).readingKey();
        }
        String[] syl = key == null || key.isEmpty() ? new String[0] : key.split(" ");
        if (key != null && !key.isEmpty()) {
            c.mainReading = render(key);
            c.wordGloss = gloss.get(key);
        }
        c.kind = "Từ ghép · " + c.text.codePointCount(0, c.text.length()) + " chữ";
        int i = 0;
        for (int off = 0; off < c.text.length(); ) {
            int cp = c.text.codePointAt(off);
            String ch = new String(Character.toChars(cp));
            off += Character.charCount(cp);
            String r = i < syl.length ? render(syl[i]) : "";
            String meaning = "";
            String[] h = han.get(ch);
            if (h != null) {
                List<String> ss = senses(h[1]);
                meaning = ss.isEmpty() ? "" : ss.get(0);
            } else if (i < syl.length && gloss.get(syl[i]) != null) {
                meaning = gloss.get(syl[i]);
            } else if (unihan.get(ch) != null) {
                meaning = unihan.get(ch);
            }
            if (meaning.length() > 90) meaning = meaning.substring(0, 88).trim() + "…";
            c.parts.add(new String[]{ch, r, meaning});
            i++;
        }
        return c;
    }
}

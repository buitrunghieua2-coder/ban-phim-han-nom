package vn.hannom.keyboard;

/**
 * Telex composer for a single Vietnamese syllable.
 * Behaviour reconstructed 1:1 from v1.0.
 * Tones: 0 ngang, 1 huyền (f), 2 sắc (s), 3 hỏi (r), 4 ngã (x), 5 nặng (j).
 */
public class TelexEngine {
    private static final String MODIFIED = "ăâêôơư";
    private static final String[] TONED = {"aàáảãạ", "ăằắẳẵặ", "âầấẩẫậ", "eèéẻẽẹ", "êềếểễệ", "iìíỉĩị",
            "oòóỏõọ", "ôồốổỗộ", "ơờớởỡợ", "uùúủũụ", "ưừứửữự", "yỳýỷỹỵ"};
    private static final String VOWELS = "aăâeêioôơuưy";

    private final StringBuilder chars = new StringBuilder();
    private int tone = 0;
    private boolean raw = false;

    public String base() { return chars.toString().toLowerCase(); }

    public int tone() { return findVowelCluster() == null ? 0 : tone; }

    public boolean isRaw() { return raw; }

    public boolean isEmpty() { return chars.length() == 0; }

    public void reset() { chars.setLength(0); tone = 0; raw = false; }

    public static TelexEngine parse(CharSequence s) {
        TelexEngine e = new TelexEngine();
        for (int i = 0; i < s.length(); i++) e.input(s.charAt(i));
        return e;
    }

    /** Strip vowel modifiers and đ: "ngươi" -> "nguoi". */
    public static String ascii(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case 'â': case 'ă': c = 'a'; break;
                case 'ê': c = 'e'; break;
                case 'ô': case 'ơ': c = 'o'; break;
                case 'đ': c = 'd'; break;
                case 'ư': c = 'u'; break;
                default: break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static String renderSyllable(String base, int tone) {
        TelexEngine e = new TelexEngine();
        e.chars.append(base);
        e.tone = tone;
        return e.render();
    }

    static boolean isVowel(char c) { return VOWELS.indexOf(Character.toLowerCase(c)) >= 0; }

    private static int toneOf(char c) {
        switch (c) {
            case 'f': return 1;
            case 's': return 2;
            case 'r': return 3;
            case 'x': return 4;
            case 'j': return 5;
            case 'z': return 0;
            default: return -1;
        }
    }

    private char lowerAt(int i) { return Character.toLowerCase(chars.charAt(i)); }

    private void set(int i, char c) {
        chars.setCharAt(i, Character.isUpperCase(chars.charAt(i)) ? Character.toUpperCase(c) : c);
    }

    private void appendRaw(char c) { raw = true; chars.append(c); }

    private int[] findVowelCluster() {
        int end = -1;
        for (int i = chars.length() - 1; i >= 0; i--) {
            if (isVowel(chars.charAt(i))) { end = i + 1; break; }
        }
        if (end < 0) return null;
        int start = end - 1;
        while (start - 1 >= 0 && isVowel(chars.charAt(start - 1))) start--;
        if (end - start > 1 && start > 0) {
            char p = lowerAt(start - 1), q = lowerAt(start);
            if ((p == 'q' && q == 'u') || (p == 'g' && q == 'i')) start++;
        }
        return new int[]{start, end};
    }

    private int tonePosition(int[] v) {
        int start = v[0], end = v[1];
        for (int i = end - 1; i >= start; i--) {
            if (MODIFIED.indexOf(lowerAt(i)) >= 0) return i;
        }
        int n = end - start;
        if (n == 1) return start;
        if (n == 2) return end < chars.length() ? start + 1 : start;
        return start + 1;
    }

    public void input(char c) {
        char lc = Character.toLowerCase(c);
        if (raw || !Character.isLetter(c)) { chars.append(c); return; }

        int t = toneOf(lc);
        if (t >= 0 && findVowelCluster() != null) {
            if (t == 0) {
                if (tone != 0) { tone = 0; return; }
            } else if (tone == t) {
                tone = 0; appendRaw(c); return;
            } else {
                tone = t; return;
            }
        }

        if (lc == 'a' || lc == 'e' || lc == 'o') {
            char hat = lc == 'a' ? 'â' : (lc == 'e' ? 'ê' : 'ô');
            int[] v = findVowelCluster();
            if (v != null) {
                for (int i = v[1] - 1; i >= v[0]; i--) {
                    char x = lowerAt(i);
                    if (x == lc || (lc == 'a' && x == 'ă') || (lc == 'o' && x == 'ơ')) { set(i, hat); return; }
                    if (x == hat) { set(i, lc); appendRaw(c); return; }
                }
            }
        }

        if (lc == 'w') {
            int[] v = findVowelCluster();
            if (v == null) { chars.append(Character.isUpperCase(c) ? 'Ư' : 'ư'); return; }
            String cl = chars.substring(v[0], v[1]).toLowerCase();
            int k = cl.indexOf("uo");
            if (k < 0) k = cl.indexOf("uô");
            if (k >= 0) { set(v[0] + k, 'ư'); set(v[0] + k + 1, 'ơ'); return; }
            k = cl.indexOf("ươ");
            if (k >= 0) { set(v[0] + k, 'u'); set(v[0] + k + 1, 'o'); appendRaw(c); return; }
            for (int i = v[1] - 1; i >= v[0]; i--) {
                char x = lowerAt(i);
                if (x == 'o' || x == 'ô') { set(i, 'ơ'); return; }
                if (x == 'a' || x == 'â') { set(i, 'ă'); return; }
                if (x == 'u') { set(i, 'ư'); return; }
                if (x == 'ơ') { set(i, 'o'); appendRaw(c); return; }
                if (x == 'ă') { set(i, 'a'); appendRaw(c); return; }
                if (x == 'ư') {
                    if (i == chars.length() - 1 && i == v[0]) { chars.setCharAt(i, c); raw = true; return; }
                    set(i, 'u'); appendRaw(c); return;
                }
            }
            chars.append(c);
            return;
        }

        if (lc == 'd' && chars.length() > 0) {
            char x = lowerAt(0);
            if (x == 'd') { set(0, 'đ'); return; }
            if (x == 'đ') { set(0, 'd'); appendRaw(c); return; }
        }
        chars.append(c);
    }

    public void backspace() {
        if (chars.length() > 0) chars.deleteCharAt(chars.length() - 1);
        if (chars.length() == 0) { tone = 0; raw = false; }
        else if (findVowelCluster() == null) tone = 0;
    }

    public String render() {
        StringBuilder sb = new StringBuilder(chars);
        int n = sb.length();
        if (n >= 2 && Character.toLowerCase(sb.charAt(n - 2)) == 'ư' && Character.toLowerCase(sb.charAt(n - 1)) == 'ơ') {
            sb.setCharAt(n - 2, Character.isUpperCase(sb.charAt(n - 2)) ? 'U' : 'u');
        }
        int[] v;
        if (tone > 0 && (v = findVowelCluster()) != null) {
            int p = tonePosition(v);
            char ch = sb.charAt(p);
            int idx = VOWELS.indexOf(Character.toLowerCase(ch));
            if (idx >= 0) {
                char r = TONED[idx].charAt(tone);
                sb.setCharAt(p, Character.isUpperCase(ch) ? Character.toUpperCase(r) : r);
            }
        }
        return sb.toString();
    }
}

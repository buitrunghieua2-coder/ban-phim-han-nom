import vn.hannom.keyboard.TelexEngine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Re-rank the Rime dictionary (rime-ime-han-nom-data) with the keyboard's v1.1 costs.
 * usage: MakeRimeDict <orig hannom-telex.dict.yaml> <assets/dict.txt> <out hannom.dict.yaml>
 * Each Telex code is decoded with the keyboard's own TelexEngine into "base:tone" so it
 * matches the Android dictionary exactly. Weight = 1e8 * exp(-cost/400) (Rime: higher = earlier).
 */
public class MakeRimeDict {
    public static void main(String[] a) throws IOException {
        Map<String, Integer> cost = new HashMap<>();
        try (BufferedReader r = reader(a[1])) {
            for (String l; (l = r.readLine()) != null; ) {
                String[] f = l.split("\t");
                if (f.length == 3) cost.put(f[0] + "\t" + f[1], Integer.parseInt(f[2]));
            }
        }
        List<String[]> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, Integer> chosen = new HashMap<>();
        int found = 0, missing = 0;
        boolean body = false;
        try (BufferedReader r = reader(a[0])) {
            for (String l; (l = r.readLine()) != null; ) {
                if (!body) { if (l.trim().equals("...")) body = true; continue; }
                if (l.isEmpty() || l.startsWith("#")) continue;
                String[] f = l.split("\t");
                if (f.length < 2) continue;
                if (!seen.add(f[0] + "\t" + f[1].trim())) continue;   // the source repeats ~11k lines
                StringBuilder key = new StringBuilder();
                for (String syl : f[1].trim().split(" ")) {
                    if (syl.isEmpty()) continue;
                    // a trailing 'z' marks the level tone in the source codes
                    TelexEngine e = TelexEngine.parse(syl.endsWith("z") && syl.length() > 1 ? syl.substring(0, syl.length() - 1) : syl);
                    if (key.length() > 0) key.append(' ');
                    key.append(e.base()).append(':').append(e.tone());
                }
                String canon = canonical(key.toString());
                String code = f[1].trim();
                String id = f[0] + "\t" + key;
                Integer prev = chosen.get(id);
                if (prev != null) {
                    String[] p = rows.get(prev);
                    boolean prevCanon = p[1].equals(canon) || p[1].equals(canon.replace(" ", "z ") + "z");
                    boolean thisCanon = code.equals(canon) || code.equals(canon.replace(" ", "z ") + "z")
                            || code.replace("z", "").equals(canon);
                    if (!prevCanon && thisCanon) p[1] = code;
                    continue;
                }
                Integer c = cost.get(f[0] + "\t" + key);
                if (c == null) { missing++; c = 6000; } else found++;
                chosen.put(id, rows.size());
                rows.add(new String[]{f[0], code, String.valueOf(c)});
            }
        }
        rows.sort(Comparator.comparingInt(x -> Integer.parseInt(x[2])));   // stable: ties keep source order
        try (Writer w = new OutputStreamWriter(new FileOutputStream(a[2]), StandardCharsets.UTF_8)) {
            w.write("# Rime dictionary\n# encoding: utf-8\n#\n"
                    + "# Bàn Phím Hán Nôm — từ điển cho Rime / Weasel.\n"
                    + "# Dữ liệu chữ: rime-ime-han-nom-data © Mike Vo (MIT), tổng hợp từ chunom.org, Wiktionary, HVDic,\n"
                    + "# Han-Nom Revival Committee. Trọng số xếp hạng lại theo Bàn Phím Hán Nôm 1.1\n"
                    + "# (thứ hạng chunom.org + tần suất âm tiết tiếng Việt + tần suất chữ Hán; wordfreq, CC BY-SA 4.0).\n\n"
                    + "---\nname: hannom\nversion: \"1.1\"\nsort: by_weight\nuse_preset_vocabulary: false\n...\n\n");
            for (String[] x : rows) {
                long weight = Math.max(1, Math.round(1e8 * Math.exp(-Integer.parseInt(x[2]) / 400.0)));
                w.write(x[0] + "\t" + x[1] + "\t" + weight + "\n");
            }
        }
        System.out.println("entries " + rows.size() + ", matched " + found + ", unmatched " + missing);
    }

    /** "ngươi:1 ta:0" -> "nguwowif ta" (full Telex, tone key last). */
    static String canonical(String key) {
        StringBuilder sb = new StringBuilder();
        for (String syl : key.split(" ")) {
            int c = syl.indexOf(':');
            String base = syl.substring(0, c);
            int tone = syl.charAt(c + 1) - '0';
            if (sb.length() > 0) sb.append(' ');
            for (char ch : base.toCharArray()) {
                switch (ch) {
                    case 'â': sb.append("aa"); break;
                    case 'ă': sb.append("aw"); break;
                    case 'ê': sb.append("ee"); break;
                    case 'ô': sb.append("oo"); break;
                    case 'ơ': sb.append("ow"); break;
                    case 'ư': sb.append("uw"); break;
                    case 'đ': sb.append("dd"); break;
                    default: sb.append(ch);
                }
            }
            sb.append(" fsrxj".charAt(tone) == ' ' ? "" : String.valueOf(" fsrxj".charAt(tone)));
        }
        return sb.toString();
    }

    static BufferedReader reader(String p) throws IOException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(p), StandardCharsets.UTF_8));
    }
}

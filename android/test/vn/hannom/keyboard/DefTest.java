package vn.hannom.keyboard;

import java.io.FileInputStream;
import java.util.List;

/** Desktop checks + printout for the definition panel data. */
public class DefTest {
    static int fails = 0, checks = 0;

    static void ok(String what, boolean cond) {
        checks++;
        if (!cond) { fails++; System.out.println("FAIL " + what); }
    }

    static void print(Definitions.Card c) {
        System.out.println("──── " + c.text + "  [" + c.mainReading + "]  " + c.kind);
        if (!c.hanViet.isEmpty()) System.out.println("  Âm Hán Việt: " + String.join(", ", c.hanViet));
        if (!c.otherReadings.isEmpty()) System.out.println("  Âm Nôm: " + String.join(", ", c.otherReadings));
        for (int i = 0; i < Math.min(3, c.senses.size()); i++) System.out.println("  " + (i + 1) + ". " + cut(c.senses.get(i)));
        if (c.unihan != null) System.out.println("  Unihan: " + c.unihan);
        for (String[] g : c.nomGlosses) System.out.println("  Nôm «" + g[0] + "»: " + cut(g[1]));
        if (c.wordGloss != null) System.out.println("  Nghĩa: " + c.wordGloss);
        for (String[] p : c.parts) System.out.println("  · " + p[0] + " " + p[1] + " — " + p[2]);
        StringBuilder sb = new StringBuilder();
        for (String[] w : c.compounds) sb.append(w[0]).append('(').append(w[1]).append(") ");
        if (sb.length() > 0) System.out.println("  Từ ghép: " + sb);
    }

    static String cut(String s) { return s.length() > 110 ? s.substring(0, 108) + "…" : s; }

    public static void main(String[] a) throws Exception {
        HanNomDict d = new HanNomDict();
        d.load(new FileInputStream(a[0]));
        Definitions defs = new Definitions();
        long t = System.nanoTime();
        defs.load(new FileInputStream(a[1]));
        System.out.println("loaded " + defs.size() + " records in " + (System.nanoTime() - t) / 1000000 + " ms");

        // sense splitting keeps "18 tuổi" inside a sense, splits only 1,2,3…
        List<String> s = Definitions.senses("1 : Can Đinh. 2 : Người đến 18 tuổi. 3 : Răn.");
        ok("senses split into 3", s.size() == 3 && s.get(1).contains("18 tuổi"));
        ok("syllableKey người", Definitions.syllableKey("người").equals("ngươi:1"));
        ok("syllableKey hoà==hòa", Definitions.syllableKey("hoà").equals(Definitions.syllableKey("hòa")));

        Definitions.Card viet = defs.card(d, "越", "viêt:5");
        ok("越 has Thiều Chửu senses", viet.senses.size() >= 3 && viet.hanViet.contains("việt"));
        ok("越 kind is Chữ Hán", viet.kind.startsWith("Chữ Hán"));
        boolean hasVN = false;
        for (String[] w : viet.compounds) hasVN |= w[0].equals("越南");
        ok("越 compounds include 越南", hasVN);

        Definitions.Card nguoi = defs.card(d, "𠊛", "ngươi:1");
        ok("𠊛 is Chữ Nôm, ext B", nguoi.kind.startsWith("Chữ Nôm") && nguoi.kind.contains("mở rộng B"));
        ok("𠊛 has gloss for người", !nguoi.nomGlosses.isEmpty() && nguoi.nomGlosses.get(0)[1].contains("person"));

        Definitions.Card co = defs.card(d, "固", "co:2");
        ok("固 Hán + used as Nôm có", co.kind.contains("Nôm") && co.otherReadings.contains("có"));

        Definitions.Card vn = defs.card(d, "越南", "viêt:5 nam:0");
        ok("越南 is a word with gloss", vn.word && vn.wordGloss != null && vn.wordGloss.contains("Vietnam"));
        ok("越南 has 2 parts", vn.parts.size() == 2 && vn.parts.get(1)[1].equals("nam"));

        Definitions.Card un = defs.card(d, "𠊛𠊛", null);
        ok("unknown word does not crash", un.parts.size() == 2);
        Definitions.Card empty = defs.card(null, "Ｘ", null);
        ok("no dict / odd char does not crash", empty.text.equals("Ｘ"));

        for (String[] q : new String[][]{{"越", "viêt:5"}, {"𠊛", "ngươi:1"}, {"固", "co:2"}, {"越南", "viêt:5 nam:0"},
                {"碎", "tôi:0"}, {"㦖", "mên:2"}, {"文化", null}, {"𠫾", "đi:0"}}) {
            print(defs.card(d, q[0], q[1]));
        }
        System.out.println(fails == 0 ? "ALL " + checks + " CHECKS PASSED" : fails + " of " + checks + " checks FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}

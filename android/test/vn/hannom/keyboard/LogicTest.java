package vn.hannom.keyboard;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.StringReader;
import java.util.List;

/** Desktop checks for the pure-Java parts (no Android needed). */
public class LogicTest {
    static int fails = 0, checks = 0;

    static void eq(String what, Object got, Object want) {
        checks++;
        if (!String.valueOf(got).equals(String.valueOf(want))) {
            fails++;
            System.out.println("FAIL " + what + ": got " + got + ", want " + want);
        }
    }

    static void ok(String what, boolean cond) {
        checks++;
        if (!cond) { fails++; System.out.println("FAIL " + what); }
    }

    static String top(HanNomDict d, String in, UserHistory h, int n) {
        List<HanNomDict.Candidate> c = d.lookup(d.segment(in), 150, h);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, c.size()); i++) sb.append(c.get(i).entry.text).append(i + 1 < Math.min(n, c.size()) ? " " : "");
        return sb.toString();
    }

    static HanNomDict.Candidate find(HanNomDict d, String in, String text) {
        for (HanNomDict.Candidate c : d.lookup(d.segment(in), 150, null)) if (c.entry.text.equals(text)) return c;
        return null;
    }

    public static void main(String[] a) throws Exception {
        // ---- Telex (same rules as v1.0)
        String[][] telex = {
                {"vieejt", "việt"}, {"nguowif", "người"}, {"dduwowngf", "đường"}, {"hoaf", "hòa"},
                {"thuowngr", "thưởng"}, {"aa", "â"}, {"aaa", "aa"}, {"w", "ư"}, {"ww", "w"},
                {"giaf", "già"}, {"quaanf", "quần"}, {"Vieejt", "Việt"}, {"tieengs", "tiếng"},
                {"muaf", "mùa"}, {"cuar", "của"}, {"ddd", "dd"}, {"ass", "as"}, {"hoanf", "hoàn"},
                {"uow", "uơ"}, {"uowc", "ươc"}, {"chuwx", "chữ"}, {"nhaf", "nhà"}, {"khoong", "không"}, {"xinz", "xinz"}, {"xinsz", "xin"},
        };
        for (String[] t : telex) eq("telex " + t[0], TelexEngine.parse(t[0]).render(), t[1]);

        // ---- dictionary + ranking
        HanNomDict d = new HanNomDict();
        d.load(new FileInputStream(a[0]));
        eq("segment vieejtnam", d.segment("vieejtnam").size(), 2);
        eq("top vieejtnam", top(d, "vieejtnam", null, 1), "越南");
        eq("top vawnhoas", top(d, "vawnhoas", null, 1), "文化");
        eq("top cos", top(d, "cos", null, 1), "𣎏");
        eq("top nguoi (no marks)", top(d, "nguoi", null, 1), "𠊛");
        eq("top ddi", top(d, "ddi", null, 1), "𠫾");
        ok("phu: main reading 夫 before alternate 伏",
                find(d, "phu", "夫").score < find(d, "phu", "伏").score);
        ok("ha: 下 (há is an alternate reading) not first", !top(d, "has", null, 1).equals("下"));

        // ---- learning: a pick moves a character up
        UserHistory h = new UserHistory();
        String before = top(d, "phu", h, 1);
        HanNomDict.Candidate fu = find(d, "phu", "夫");
        h.recordPick(fu.entry.text, fu.entry.readingKey());
        h.recordPick(fu.entry.text, fu.entry.readingKey());
        eq("after picking 夫 twice, 'phu' starts with it (was " + before + ")", top(d, "phu", h, 1), "夫");
        ok("learning off (null history) keeps default order", top(d, "phu", null, 1).equals(before));

        // ---- learning: pairs become words and next-word suggestions
        HanNomDict.Candidate toi = find(d, "toi", "碎");
        HanNomDict.Candidate di = find(d, "ddi", "𠫾");
        ok("found 碎 / 𠫾", toi != null && di != null);
        ok("碎𠫾 not a dictionary word", d.findWord("碎𠫾", toi.entry.readingKey() + " " + di.entry.readingKey()) == null);
        for (int i = 0; i < UserHistory.WORD_THRESHOLD; i++) {
            UserHistory.Pair p = h.recordPair(toi.entry.text, toi.entry.readingKey(), di.entry.text, di.entry.readingKey());
            if (p.stat.count >= UserHistory.WORD_THRESHOLD) d.addUserWord(p.wordText(), p.wordReading());
        }
        eq("learned word for toiddi", top(d, "toiddi", h, 1), "碎𠫾");
        List<HanNomDict.Prediction> pr = d.predict("碎", toi.entry.readingKey(), 10, h);
        ok("prediction after 碎 offers 𠫾", !pr.isEmpty() && pr.get(0).text.equals("𠫾"));
        List<HanNomDict.Prediction> pv = d.predict("越", "viêt:5", 10, null);
        boolean hasNam = false;
        for (HanNomDict.Prediction p : pv) hasNam |= p.text.equals("南");
        ok("prediction after 越 offers 南", hasNam);

        // one accidental pair does not outrank a common dictionary word; two do
        UserHistory hv = new UserHistory();
        hv.recordPick("越", "viêt:5");
        hv.recordPair("越", "viêt:5", "碎", "tôi:0");
        ok("after one 越→碎, 南 still first", d.predict("越", "viêt:5", 5, hv).get(0).text.equals("南"));
        hv.recordPair("越", "viêt:5", "碎", "tôi:0");
        ok("after two 越→碎, 碎 first", d.predict("越", "viêt:5", 5, hv).get(0).text.equals("碎"));

        // ---- persistence round trip
        String ser = h.serialize();
        UserHistory h2 = new UserHistory();
        h2.parse(new BufferedReader(new StringReader(ser)));
        eq("history picks survive", h2.size(), h.size());
        eq("history pairs survive", h2.pairCount(), h.pairCount());
        eq("bonus survives", h2.bonus(fu.entry.key()), h.bonus(fu.entry.key()));
        HanNomDict d2 = new HanNomDict();
        d2.load(new FileInputStream(a[0]));
        d2.syncUserWords(h2);
        eq("learned word restored on load", top(d2, "toiddi", h2, 1), "碎𠫾");
        d2.removeUserWords();
        h2.clear();
        ok("clear removes learned word", !top(d2, "toiddi", h2, 1).equals("碎𠫾"));
        h2.parse(new BufferedReader(new StringReader("garbage\nP\tx\n#hannom-history\t1\tzz\n")));
        ok("damaged history file does not crash", h2.size() == 0);

        System.out.println(fails == 0 ? "ALL " + checks + " CHECKS PASSED" : fails + " of " + checks + " checks FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}

package vn.hannom.keyboard;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Prints top candidates under v1.0 ordering (old dict) vs v1.1 ordering (new dict). */
public class RankDemo {
    public static void main(String[] a) throws Exception {
        HanNomDict oldD = new HanNomDict();
        oldD.load(new FileInputStream(a[0]));
        HanNomDict newD = new HanNomDict();
        newD.load(new FileInputStream(a[1]));
        int top = 6;
        for (int i = 2; i < a.length; i++) {
            String in = a[i];
            List<HanNomDict.Candidate> o = new ArrayList<>(oldD.lookup(oldD.segment(in), 3000, null));
            final List<HanNomDict.Syllable> segs = oldD.segment(in);
            // v1.0 rule: weight + penalty * 5000, penalty = sum of match levels
            Collections.sort(o, new Comparator<HanNomDict.Candidate>() {
                int old(HanNomDict.Candidate c) {
                    int p = 0;
                    for (int k = 0; k < c.syllables; k++) p += HanNomDict.match(segs.get(k), c.entry, k, k == segs.size() - 1 || c.syllables == 1);
                    return c.entry.cost + p * 5000;
                }
                @Override public int compare(HanNomDict.Candidate x, HanNomDict.Candidate y) {
                    if (x.syllables != y.syllables) return y.syllables - x.syllables;
                    return Integer.compare(old(x), old(y));
                }
            });
            List<HanNomDict.Candidate> n = newD.lookup(newD.segment(in), 150, null);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-12s 1.0: ", in));
            for (int k = 0; k < Math.min(top, o.size()); k++) sb.append(o.get(k).entry.text).append('(').append(o.get(k).entry.reading()).append(") ");
            sb.append("\n             1.1: ");
            for (int k = 0; k < Math.min(top, n.size()); k++) sb.append(n.get(k).entry.text).append('(').append(n.get(k).entry.reading()).append(") ");
            System.out.println(sb);
        }
    }
}

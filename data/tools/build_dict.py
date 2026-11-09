"""Re-rank the Han Nom dictionary for v1.1.

Input : original dict.txt from v1.0   (text \t base:tone[ base:tone...] \t weight)
Output: new dict.txt with the same 3 columns, the 3rd column now a COST
        (lower = shown earlier).  The keyboard adds match penalties and the
        user's learned bonus on top of this cost at run time.

Cost model (roughly -log probability, 100 units ~ factor e):
  cost = tier/rank cost of the (character, reading) pair
       + tie-break by general character frequency (Chinese corpus proxy)
       - bonus when that reading is used in many compound words
       + cost of the Vietnamese syllable itself (how common the sound is)

Key fix vs v1.0: weight 5 in the source marks ALTERNATE readings
(2054 of 2055 such entries belong to characters that have a main reading
elsewhere). v1.0 treated 5 as "very common", pushing rare readings to the
top (e.g. 伏 before 夫 for "phu", 下 before Nôm chars for "há").
"""
import math, sys, collections
from wordfreq import get_frequency_dict
from opencc import OpenCC

SRC, DST = sys.argv[1], sys.argv[2]
t2s = OpenCC('t2s')

TONED = ["aàáảãạ", "ăằắẳẵặ", "âầấẩẫậ", "eèéẻẽẹ", "êềếểễệ", "iìíỉĩị",
         "oòóỏõọ", "ôồốổỗộ", "ơờớởỡợ", "uùúủũụ", "ưừứửữự", "yỳýỷỹỵ"]
DECOMP = {}
for row in TONED:
    for t, ch in enumerate(row):
        DECOMP[ch] = (row[0], t)


def vi_key(tok):
    """'người' -> ('ngươi', 1); placement-independent (hoà == hòa)."""
    base, tone = [], 0
    for ch in tok.lower():
        if ch in DECOMP:
            b, t = DECOMP[ch]
            if t:
                if tone:
                    return None
                tone = t
            base.append(b)
        elif ch.isalpha():
            base.append(ch)
        else:
            return None
    return ''.join(base), tone


# --- Vietnamese syllable frequencies -------------------------------------
vi = collections.Counter()
for tok, f in get_frequency_dict('vi').items():
    k = vi_key(tok)
    if k:
        vi[k] += f

# --- Chinese character frequencies (sum over words containing the char) ---
zh_words = get_frequency_dict('zh')
zh_char = collections.Counter()
for w, f in zh_words.items():
    for ch in w:
        zh_char[ch] += f


def LN(f):
    return -100.0 * math.log(f)


def clamp(x, lo, hi):
    return max(lo, min(hi, x))


def syl_cost(base, tone):
    f = vi.get((base, tone), 0.0)
    return clamp(LN(f) - 400, 0, 1450) if f > 0 else 1450


def zh_freq_of(text):
    s = t2s.convert(text)
    if len(text) == 1:
        return zh_char.get(s) or zh_char.get(text) or 0.0
    return zh_words.get(s) or zh_words.get(text) or 0.0


def char_tie(ch):
    f = zh_freq_of(ch)
    if f > 0:
        return clamp((LN(f) - 450) * 0.5, 0, 700)
    cp = ord(ch)
    if 0x4E00 <= cp <= 0x9FFF:
        return 650          # basic CJK but absent from modern corpus: rare
    if 0xF900 <= cp <= 0xFAFF or 0x2F800 <= cp <= 0x2FA1F:
        return 750          # compatibility ideographs: almost never wanted
    return 450              # Nôm-specific (Ext A/B/...): neutral


rows = []
for line in open(SRC, encoding='utf-8'):
    t, r, w = line.rstrip('\n').split('\t')
    syls = [(s.split(':')[0], int(s.split(':')[1])) for s in r.split(' ')]
    rows.append((t, r, int(w), syls))

# how many compound words use character c with reading (base, tone)
in_words = collections.Counter()
for t, r, w, syls in rows:
    if len(syls) > 1 and len(t) == len(syls):
        for ch, s in zip(t, syls):
            in_words[(ch, s)] += 1


def ranked(w):
    return 1 <= w <= 4 or 6 <= w <= 9998 or 10000 < w < 100000


out = []
stats = collections.Counter()
for t, r, w, syls in rows:
    if len(syls) == 1:
        s = syls[0]
        if ranked(w):
            rank = w - 10000 if w > 10000 else w   # 10303 / 10488 = rank 303 / 488
            base = 100 * math.log(min(rank, 9000)); stats['ranked'] += 1
        else:
            tier = {9999: 1000, 10000: 1000, 5: 2000, 100000: 2100,
                    600000: 3200, 9999999: 3500}.get(w)
            if tier is None:
                tier = 3000  # 5xxxxx: simplified-form variants
            bonus = min(300, 110 * math.log1p(in_words[(t, s)])) if len(t) == 1 else 0
            base = tier + char_tie(t[0]) - bonus
            stats['tier%d' % tier] += 1
        cost = base + syl_cost(*s)
    else:
        if ranked(w):
            base = 100 * math.log(min(w, 9000))
        else:
            tier = 1000 if w in (9999, 10000) else 3000
            fz = zh_freq_of(t)
            fv = 1.0 / sum(1.0 / max(vi.get(s, 1e-9), 1e-9) for s in syls)
            f = max(fz, fv * 0.1)
            base = tier + clamp((LN(f) - 500) * 0.5, 0, 800)
        cost = base + 0.5 * sum(syl_cost(*s) for s in syls) / len(syls)
    out.append('%s\t%s\t%d\n' % (t, r, round(cost)))

with open(DST, 'w', encoding='utf-8') as fh:
    fh.writelines(out)
print(dict(stats), 'vi syllables:', len(vi), 'zh chars:', len(zh_char))

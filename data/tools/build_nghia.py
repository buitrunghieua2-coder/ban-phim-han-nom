"""Build assets/nghia.txt — offline definitions for the long-press panel.

Line formats (tab separated, one record per line):
  H <char> <Hán-Việt readings, comma separated> <definition>   Thiều Chửu, Hán Việt tự điển
  U <char> <English definition>                                  Unicode Unihan kDefinition
  W <reading key> <English gloss>                                Wiktionary (Vietnamese words)

<reading key> uses the keyboard's own syllable format, e.g. "ngươi:1" or
"viêt:5 nam:0", so the app can look up a Nôm character / word by the reading
it was typed with, independent of tone-mark placement (hoà == hòa).

Usage: build_nghia.py THIEU_CHUU.csv UNIHAN_VIETNAMESE.csv WIKTIONARY_WORDS_DIR DICT.txt OUT.txt
"""
import csv, glob, json, re, sys

TC, UH, WK, DICT, OUT = sys.argv[1:6]

TONED = ["aàáảãạ", "ăằắẳẵặ", "âầấẩẫậ", "eèéẻẽẹ", "êềếểễệ", "iìíỉĩị",
         "oòóỏõọ", "ôồốổỗộ", "ơờớởỡợ", "uùúủũụ", "ưừứửữự", "yỳýỷỹỵ"]
DECOMP = {ch: (row[0], t) for row in TONED for t, ch in enumerate(row)}


def syl_key(tok):
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
    return '%s:%d' % (''.join(base), tone) if base else None


def reading_key(text):
    parts = [syl_key(s) for s in text.replace('-', ' ').split()]
    return ' '.join(parts) if parts and all(parts) else None


def clean(s):
    s = s.replace('Ð', 'Đ').replace('ð', 'đ')          # eth -> đ (OCR/encoding slip)
    s = re.sub(r'[\t\r\n]+', ' ', s)
    return re.sub(r' {2,}', ' ', s).strip()


# --- what the keyboard can actually produce
chars, readings = set(), set()
for line in open(DICT, encoding='utf-8'):
    t, r, _ = line.rstrip('\n').split('\t')
    readings.add(r)
    if ' ' not in r:
        chars.add(t)

out = []
tc_chars = set()
for row in csv.DictReader(open(TC, encoding='utf-8-sig')):
    c = row['char'].strip()
    if not c or c in tc_chars:
        continue
    tc_chars.add(c)
    hv = ', '.join(x.strip() for x in clean(row['pronunciation']).split(',') if x.strip())
    out.append('H\t%s\t%s\t%s\n' % (c, hv, clean(row['definition'])))

n_u = 0
for row in csv.DictReader(open(UH, encoding='utf-8-sig')):
    c = row['char']
    d = clean(row.get('kDefinition') or '')
    if d and c in chars and c not in tc_chars:
        out.append('U\t%s\t%s\n' % (c, d))
        n_u += 1

# --- Wiktionary glosses for every reading the dictionary uses
words = {}
for f in sorted(glob.glob(WK + '/*.json')):
    for w, entries in json.load(open(f, encoding='utf-8')).items():
        k = reading_key(w)
        if k and k in readings:
            words.setdefault(k, []).extend(entries)

# notes about spelling, not meaning
NOISE = re.compile(r'^(Traditional tone placement spelling|Modern tone placement spelling|obsolete spelling|'
                   r'Nonstandard spelling|Misspelling|Alternative spelling)', re.I)
POS = {'noun': 'danh từ', 'verb': 'động từ', 'adj': 'tính từ', 'adv': 'phó từ', 'pron': 'đại từ',
       'num': 'số từ', 'particle': 'trợ từ', 'prep': 'giới từ', 'conj': 'liên từ', 'intj': 'thán từ',
       'classifier': 'loại từ', 'name': 'tên riêng', 'phrase': 'cụm từ', 'suffix': 'hậu tố', 'prefix': 'tiền tố'}
n_w = 0
for k, entries in sorted(words.items()):
    parts = []
    entries.sort(key=lambda e: -(e.get('frequency') or 0))
    for e in entries[:3]:
        gl = []
        for s in e.get('senses', [])[:3]:
            g = '; '.join(clean(x) for x in s.get('glosses', []) if x and not NOISE.match(x))
            if g and g not in gl:
                gl.append(g)
        if gl:
            pos = POS.get(e.get('pos'), e.get('pos') or '')
            part = ('(%s) ' % pos if pos else '') + ' · '.join(gl)
            if part not in parts:
                parts.append(part)
    if parts:
        g = ' | '.join(parts)
        if len(g) > 360:
            g = g[:357].rsplit(' ', 1)[0] + '…'
        out.append('W\t%s\t%s\n' % (k, g))
        n_w += 1

with open(OUT, 'w', encoding='utf-8') as fh:
    fh.writelines(out)
print('Thiều Chửu', len(tc_chars), 'Unihan', n_u, 'Wiktionary', n_w, 'lines', len(out))

"""Make hannom.schema.yaml from rime-ime-han-nom-data's hannom-telex.schema.yaml.

Changes: own id/name, no cangjie5 / OpenCC (hn.ini) dependency, Vietnamese switch labels,
markless + toneless typing ("nguoi" -> 𠊛) as lower-credibility spellings, user dictionary on.
usage: make_rime_schema.py SRC_SCHEMA OUT_SCHEMA [derive|fuzz]
"""
import re, sys

src, out = sys.argv[1], sys.argv[2]
mode = sys.argv[3] if len(sys.argv) > 3 else 'fuzz'
text = open(src, encoding='utf-8').read()

# split into top-level blocks
blocks, cur, name = [], [], None
for line in text.split('\n'):
    m = re.match(r'^([A-Za-z_]+):', line)
    if m:
        if cur: blocks.append((name, cur))
        name, cur = m.group(1), [line]
    else:
        cur.append(line)
blocks.append((name, cur))
B = dict(blocks)
order = [n for n, _ in blocks]

B['schema'] = '''schema:
  schema_id: hannom
  name: "Hán Nôm 漢喃"
  version: "1.1"
  author:
    - Bàn Phím Hán Nôm
    - Mike Vo 武祥明 <info@miketvo.com> (rime-ime-han-nom-data, MIT)
    - Keepout2010 <Keepout2010@163.com>
  description: |
    Gõ chữ Hán và chữ Nôm bằng Telex. Thứ tự chữ theo Bàn Phím Hán Nôm 1.1:
    chữ phổ biến trước, âm đọc chính trước âm đọc phụ; gõ không dấu vẫn được.
'''.split('\n')

B['switches'] = '''switches:
  - name: ascii_mode
    reset: 0
    states: [ 漢喃, ABC ]
  - name: full_shape
    states: [ Nửa khổ, Toàn khổ ]
'''.split('\n')

eng = '\n'.join(B['engine'])
eng = eng.replace('    - reverse_lookup_translator\n', '').replace('    - simplifier\n', '')
eng = eng.replace('    - uniquifier\n', '    - uniquifier\n    - lua_filter@*hannom_exact_first\n')
assert 'hannom_exact_first' in eng
B['engine'] = eng.split('\n')

sp = '\n'.join(B['speller'])
op = mode  # derive = same rank as exact; fuzz = lower credibility
extra = f'''    # --- Bàn Phím Hán Nôm: gõ không dấu / không thanh vẫn ra chữ (xếp sau cách gõ đủ dấu)
    - derive/uwow/uow/
    - {op}/^(.+?)[sfrxj]$/$1/
    - {op}/^([^aeiouy]*[aeiouy]+[^aeiouy]*)[sfrxj]([^aeiouy]+)$/$1$2/
    - {op}/aa/a/
    - {op}/ee/e/
    - {op}/oo/o/
    - {op}/w//
    - {op}/^dd/d/
    # Generate uppercase input aliases from the lowercase dictionary spellings.'''
sp = sp.replace('    # Generate uppercase input aliases from the lowercase dictionary spellings.', extra, 1)
assert op + '/aa/a/' in sp
B['speller'] = sp.split('\n')

tr = '\n'.join(B['translator'])
tr = tr.replace('  dictionary: hannom-telex\n  prism: hannom-telex\n',
                '  dictionary: hannom\n  prism: hannom\n  enable_user_dict: true\n  enable_sentence: true\n')
assert 'dictionary: hannom\n' in tr
B['translator'] = tr.split('\n')

rec = '\n'.join(B['recognizer'])
rec = rec.replace('    reverse_lookup: "`[a-z]*\'?$"\n', '')
B['recognizer'] = rec.split('\n')

B['menu'] = ['menu:', '  page_size: 9', '']
order = [n for n in order if n not in ('reverse_lookup', 'simplifier')]
if 'menu' not in order:
    order.insert(order.index('translator'), 'menu')
head = '# Rime schema — Bàn Phím Hán Nôm cho Windows (Weasel)\n# encoding: utf-8\n'
body = '\n'.join('\n'.join(B[n]).rstrip('\n') + '\n' for n in order if n)
open(out, 'w', encoding='utf-8').write(head + body)
print('written', out, mode)

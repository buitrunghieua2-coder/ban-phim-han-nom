#include "core.hpp"
#include <algorithm>
#include <cstdio>
#include <fstream>
#include <set>
#include <sstream>
#include <unordered_set>

namespace hn {

// ----------------------------------------------------------------- UTF helpers
std::u32string u32(const std::string &s) {
    std::u32string out;
    out.reserve(s.size());
    for (size_t i = 0; i < s.size();) {
        unsigned char c = s[i];
        char32_t cp;
        int n;
        if (c < 0x80) { cp = c; n = 1; }
        else if ((c >> 5) == 6) { cp = c & 0x1F; n = 2; }
        else if ((c >> 4) == 14) { cp = c & 0x0F; n = 3; }
        else if ((c >> 3) == 30) { cp = c & 0x07; n = 4; }
        else { i++; continue; }
        if (i + n > s.size()) break;
        for (int k = 1; k < n; k++) cp = (cp << 6) | (static_cast<unsigned char>(s[i + k]) & 0x3F);
        out.push_back(cp);
        i += n;
    }
    return out;
}

std::string u8(const std::u32string &s) {
    std::string o;
    for (char32_t c : s) {
        if (c < 0x80) o += static_cast<char>(c);
        else if (c < 0x800) { o += static_cast<char>(0xC0 | (c >> 6)); o += static_cast<char>(0x80 | (c & 0x3F)); }
        else if (c < 0x10000) {
            o += static_cast<char>(0xE0 | (c >> 12)); o += static_cast<char>(0x80 | ((c >> 6) & 0x3F));
            o += static_cast<char>(0x80 | (c & 0x3F));
        } else {
            o += static_cast<char>(0xF0 | (c >> 18)); o += static_cast<char>(0x80 | ((c >> 12) & 0x3F));
            o += static_cast<char>(0x80 | ((c >> 6) & 0x3F)); o += static_cast<char>(0x80 | (c & 0x3F));
        }
    }
    return o;
}

std::u16string u16(const std::u32string &s) {
    std::u16string o;
    for (char32_t c : s) {
        if (c < 0x10000) o += static_cast<char16_t>(c);
        else { c -= 0x10000; o += static_cast<char16_t>(0xD800 + (c >> 10)); o += static_cast<char16_t>(0xDC00 + (c & 0x3FF)); }
    }
    return o;
}

bool isHan(char32_t c) {
    return (c >= 0x3400 && c <= 0x4DBF) || (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0xF900 && c <= 0xFAFF) ||
           (c >= 0x20000 && c <= 0x323AF) || (c >= 0x2E80 && c <= 0x2FDF);
}

static std::vector<std::string> split(const std::string &s, char sep) {
    std::vector<std::string> v;
    size_t a = 0;
    for (size_t b; (b = s.find(sep, a)) != std::string::npos; a = b + 1) v.push_back(s.substr(a, b - a));
    v.push_back(s.substr(a));
    return v;
}

static std::u32string join(const std::vector<std::u32string> &v, const std::u32string &sep) {
    std::u32string o;
    for (size_t i = 0; i < v.size(); i++) { if (i) o += sep; o += v[i]; }
    return o;
}

// ------------------------------------------------------------ Vietnamese tones
static const std::u32string VOWELS = U"aăâeêioôơuưy";
static const std::u32string MODIFIED = U"ăâêôơư";
static const std::u32string TONED[12] = {U"aàáảãạ", U"ăằắẳẵặ", U"âầấẩẫậ", U"eèéẻẽẹ", U"êềếểễệ", U"iìíỉĩị",
                                         U"oòóỏõọ", U"ôồốổỗộ", U"ơờớởỡợ", U"uùúủũụ", U"ưừứửữự", U"yỳýỷỹỵ"};

static bool isVowel(char32_t c) { return VOWELS.find(c) != std::u32string::npos; }

static std::u32string renderSyllable(const std::u32string &chars, int tone) {
    std::u32string sb = chars;
    size_t n = sb.size();
    if (n >= 2 && sb[n - 2] == U'ư' && sb[n - 1] == U'ơ') sb[n - 2] = U'u';
    if (tone <= 0 || tone > 5) return sb;
    // vowel cluster on the original letters (same as TelexEngine.findVowelCluster)
    long end = -1;
    for (long i = static_cast<long>(chars.size()) - 1; i >= 0; i--) if (isVowel(chars[i])) { end = i + 1; break; }
    if (end < 0) return sb;
    long start = end - 1;
    while (start - 1 >= 0 && isVowel(chars[start - 1])) start--;
    if (end - start > 1 && start > 0) {
        char32_t p = chars[start - 1], q = chars[start];
        if ((p == U'q' && q == U'u') || (p == U'g' && q == U'i')) start++;
    }
    long pos = -1;
    for (long i = end - 1; i >= start; i--) if (MODIFIED.find(chars[i]) != std::u32string::npos) { pos = i; break; }
    if (pos < 0) {
        long k = end - start;
        if (k == 1) pos = start;
        else if (k == 2) pos = end < static_cast<long>(chars.size()) ? start + 1 : start;
        else pos = start + 1;
    }
    size_t idx = VOWELS.find(sb[pos]);
    if (idx != std::u32string::npos) sb[pos] = TONED[idx][tone];
    return sb;
}

std::u32string Data::renderReading(const std::string &key) {
    std::vector<std::u32string> parts;
    for (const std::string &p : split(key, ' ')) {
        size_t c = p.find(':');
        if (c == std::string::npos || c == 0 || c + 1 >= p.size()) continue;
        parts.push_back(renderSyllable(u32(p.substr(0, c)), p[c + 1] - '0'));
    }
    return join(parts, U" ");
}

std::string Data::syllableKey(const std::u32string &syl) {
    std::u32string b;
    int tone = 0;
    for (char32_t c : syl) {
        if (c >= U'A' && c <= U'Z') c = c - U'A' + U'a';
        bool found = false;
        for (const auto &row : TONED) {
            size_t t = row.find(c);
            if (t != std::u32string::npos) {
                if (t > 0) tone = static_cast<int>(t);
                b += row[0];
                found = true;
                break;
            }
        }
        if (!found) b += c;
    }
    if (b.empty()) return "";
    return u8(b) + ":" + std::to_string(tone);
}

// ------------------------------------------------------------------- loading
static bool readLines(const std::string &path, std::vector<std::string> &out) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return false;
    std::string line;
    while (std::getline(f, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        out.push_back(std::move(line));
    }
    return true;
}

bool Data::loadDict(const std::string &path) {
    std::vector<std::string> lines;
    if (!readLines(path, lines)) return false;
    entries.reserve(lines.size());
    for (const std::string &l : lines) {
        std::vector<std::string> f = split(l, '\t');
        if (f.size() != 3) continue;
        Entry e;
        e.text = u32(f[0]);
        e.readingKey = f[1];
        e.cost = std::atoi(f[2].c_str());
        if (e.text.empty()) continue;
        size_t idx = entries.size();
        entries.push_back(std::move(e));
        const Entry &en = entries.back();
        byText[en.text].push_back(idx);
        if (en.length() > 1) {
            std::unordered_set<char32_t> seen;
            for (char32_t c : en.text) if (seen.insert(c).second) wordsByChar[c].push_back(idx);
        }
    }
    return !entries.empty();
}

bool Data::loadDefs(const std::string &path) {
    std::vector<std::string> lines;
    if (!readLines(path, lines)) return false;
    for (const std::string &l : lines) {
        std::vector<std::string> f = split(l, '\t');
        if (f.size() < 3) continue;
        if (f[0] == "H" && f.size() >= 4) han[u32(f[1])] = {u32(f[2]), u32(f[3])};
        else if (f[0] == "U") unihan[u32(f[1])] = u32(f[2]);
        else if (f[0] == "W") gloss[f[1]] = u32(f[2]);
    }
    return true;
}

// ------------------------------------------------------------------- lookups
std::vector<const Entry *> Data::entriesFor(const std::u32string &text) const {
    std::vector<const Entry *> out;
    auto it = byText.find(text);
    if (it == byText.end()) return out;
    for (size_t i : it->second) out.push_back(&entries[i]);
    std::stable_sort(out.begin(), out.end(), [](const Entry *a, const Entry *b) { return a->cost < b->cost; });
    return out;
}

std::vector<const Entry *> Data::wordsContaining(char32_t ch, size_t limit) const {
    std::vector<const Entry *> all, out;
    auto it = wordsByChar.find(ch);
    if (it == wordsByChar.end()) return out;
    for (size_t i : it->second) all.push_back(&entries[i]);
    std::stable_sort(all.begin(), all.end(), [](const Entry *a, const Entry *b) { return a->cost < b->cost; });
    std::set<std::u32string> seen;
    for (const Entry *e : all) {
        if (seen.insert(e->text).second) out.push_back(e);
        if (out.size() >= limit) break;
    }
    return out;
}

const Entry *Data::wordAt(const std::u32string &ctx, size_t idx) const {
    if (idx >= ctx.size()) return nullptr;
    auto it = wordsByChar.find(ctx[idx]);
    if (it == wordsByChar.end()) return nullptr;
    const Entry *best = nullptr;
    for (size_t i : it->second) {
        const Entry &e = entries[i];
        size_t L = e.length();
        if (L < 2 || L > ctx.size()) continue;
        size_t lo = idx + 1 >= L ? idx + 1 - L : 0;
        for (size_t s = lo; s <= idx && s + L <= ctx.size(); s++) {
            if (ctx.compare(s, L, e.text) == 0) {
                if (!best || L > best->length() || (L == best->length() && e.cost < best->cost)) best = &e;
                break;
            }
        }
    }
    return best;
}

std::vector<std::u32string> Data::senses(const std::u32string &def) {
    // "1 : Một… 2 : Cùng…" -> numbered senses; only 1,2,3… in order count as markers
    std::vector<std::u32string> out;
    int expect = 1;
    long lastStart = -1;
    for (size_t i = 0; i < def.size(); i++) {
        if (!(i == 0 || def[i - 1] == U' ')) continue;
        size_t j = i;
        int num = 0, digits = 0;
        while (j < def.size() && def[j] >= U'0' && def[j] <= U'9' && digits < 2) { num = num * 10 + (def[j] - U'0'); j++; digits++; }
        if (!digits || j + 2 >= def.size() || def[j] != U' ' || def[j + 1] != U':' || def[j + 2] != U' ') continue;
        if (num != expect) continue;
        if (lastStart >= 0) {
            std::u32string s = def.substr(lastStart, i - lastStart);
            while (!s.empty() && s.back() == U' ') s.pop_back();
            out.push_back(s);
        }
        lastStart = static_cast<long>(j + 3);
        expect++;
        i = j + 2;
    }
    std::u32string rest = lastStart < 0 ? def : def.substr(lastStart);
    while (!rest.empty() && rest.back() == U' ') rest.pop_back();
    while (!rest.empty() && rest.front() == U' ') rest.erase(rest.begin());
    out.push_back(rest);
    return out;
}

std::u32string Data::blockName(char32_t cp) {
    if (cp >= 0x4E00 && cp <= 0x9FFF) return U"CJK cơ bản";
    if (cp >= 0x3400 && cp <= 0x4DBF) return U"CJK mở rộng A";
    if (cp >= 0x20000 && cp <= 0x2A6DF) return U"CJK mở rộng B";
    if (cp >= 0x2A700 && cp <= 0x2B73F) return U"CJK mở rộng C";
    if (cp >= 0x2B740 && cp <= 0x2B81F) return U"CJK mở rộng D";
    if (cp >= 0x2B820 && cp <= 0x2CEAF) return U"CJK mở rộng E";
    if (cp >= 0x2CEB0 && cp <= 0x2EBEF) return U"CJK mở rộng F";
    if (cp >= 0x2EBF0 && cp <= 0x2EE5F) return U"CJK mở rộng I";
    if (cp >= 0x30000 && cp <= 0x3134F) return U"CJK mở rộng G";
    if (cp >= 0x31350 && cp <= 0x323AF) return U"CJK mở rộng H";
    if ((cp >= 0xF900 && cp <= 0xFAFF) || (cp >= 0x2F800 && cp <= 0x2FA1F)) return U"chữ tương thích";
    return U"";
}

static std::u32string codePoint(char32_t cp) {
    char buf[16];
    std::snprintf(buf, sizeof buf, "U+%04X", static_cast<unsigned>(cp));
    return u32(buf);
}

static std::u32string cut(const std::u32string &s, size_t n) {
    if (s.size() <= n) return s;
    std::u32string o = s.substr(0, n - 1);
    while (!o.empty() && o.back() == U' ') o.pop_back();
    return o + U"…";
}

static std::u32string replaceAll(std::u32string s, const std::u32string &a, const std::u32string &b) {
    for (size_t p = 0; (p = s.find(a, p)) != std::u32string::npos; p += b.size()) s.replace(p, a.size(), b);
    return s;
}

// -------------------------------------------------------------------- cards
Card Data::card(const std::u32string &text, const std::string &readingKey) const {
    Card c;
    c.text = text;
    c.word = text.size() > 1;
    if (c.word) { wordCard(c, readingKey); return c; }

    char32_t cp = text.empty() ? 0 : text[0];
    auto h = han.find(text);
    std::set<std::string> hvKeys;
    if (h != han.end()) {
        for (const std::string &r : split(u8(h->second.first), ',')) {
            std::string t = r;
            while (!t.empty() && t.front() == ' ') t.erase(t.begin());
            while (!t.empty() && t.back() == ' ') t.pop_back();
            if (t.empty() || std::find(c.hanViet.begin(), c.hanViet.end(), u32(t)) != c.hanViet.end()) continue;
            c.hanViet.push_back(u32(t));
            hvKeys.insert(syllableKey(u32(t)));
        }
        c.senses = senses(h->second.second);
    }
    std::vector<std::string> other;
    for (const Entry *e : entriesFor(text)) {
        if (!hvKeys.count(e->readingKey) && std::find(other.begin(), other.end(), e->readingKey) == other.end())
            other.push_back(e->readingKey);
    }
    for (const std::string &k : other) {
        std::u32string disp = renderReading(k);
        c.otherReadings.push_back(disp);
        auto g = gloss.find(k);
        if (g != gloss.end() && c.nomGlosses.size() < 4) c.nomGlosses.push_back({disp, replaceAll(g->second, U" | ", U"; ")});
    }
    if (h == han.end()) {
        auto u = unihan.find(text);
        if (u != unihan.end()) c.unihan = u->second;
    }
    if (!readingKey.empty()) c.mainReading = renderReading(readingKey);
    else if (!c.hanViet.empty()) c.mainReading = c.hanViet[0];
    else if (!c.otherReadings.empty()) c.mainReading = c.otherReadings[0];

    std::u32string type;
    if (h != han.end() && !c.otherReadings.empty()) type = U"Chữ Hán · cũng dùng làm chữ Nôm";
    else if (h != han.end()) type = U"Chữ Hán";
    else if (!c.unihan.empty() && !c.otherReadings.empty()) type = U"Chữ Hán / Nôm";
    else if (!c.otherReadings.empty()) type = U"Chữ Nôm";
    else type = U"Chữ Hán";
    std::u32string block = blockName(cp);
    c.kind = type + U" · " + codePoint(cp) + (block.empty() ? U"" : U" · " + block);

    for (const Entry *w : wordsContaining(cp, 12)) c.compounds.push_back({w->text, renderReading(w->readingKey)});
    return c;
}

void Data::wordCard(Card &c, std::string key) const {
    if (key.empty()) {
        std::vector<const Entry *> es = entriesFor(c.text);
        if (!es.empty()) key = es[0]->readingKey;
    }
    std::vector<std::string> syl = key.empty() ? std::vector<std::string>() : split(key, ' ');
    if (!key.empty()) {
        c.mainReading = renderReading(key);
        auto g = gloss.find(key);
        if (g != gloss.end()) c.wordGloss = replaceAll(g->second, U" | ", U"\n");
    }
    c.kind = U"Từ ghép · " + u32(std::to_string(c.text.size())) + U" chữ";
    for (size_t i = 0; i < c.text.size(); i++) {
        std::u32string ch(1, c.text[i]);
        std::u32string r = i < syl.size() ? renderReading(syl[i]) : U"";
        std::u32string meaning;
        auto h = han.find(ch);
        if (h != han.end()) {
            std::vector<std::u32string> ss = senses(h->second.second);
            if (!ss.empty()) meaning = ss[0];
        } else if (i < syl.size() && gloss.count(syl[i])) {
            meaning = gloss.at(syl[i]);
        } else if (unihan.count(ch)) {
            meaning = unihan.at(ch);
        }
        c.parts.push_back({ch, r, cut(meaning, 90)});
    }
}

}  // namespace hn

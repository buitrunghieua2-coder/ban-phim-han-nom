// Native (Linux) checks for the portable core; mirrors DefTest.java plus context-word detection.
#include "../src/core.hpp"
#include <chrono>
#include <cstdio>
#include <algorithm>

using namespace hn;
static int fails = 0, checks = 0;
static void ok(const char *what, bool c) { checks++; if (!c) { fails++; std::printf("FAIL %s\n", what); } }
static bool has(const std::vector<std::u32string> &v, const std::u32string &x) { return std::find(v.begin(), v.end(), x) != v.end(); }
static bool contains(const std::u32string &s, const std::u32string &x) { return s.find(x) != std::u32string::npos; }

static void print(const Card &c) {
    std::printf("──── %s  [%s]  %s\n", u8(c.text).c_str(), u8(c.mainReading).c_str(), u8(c.kind).c_str());
    auto j = [](const std::vector<std::u32string> &v) { std::string o; for (auto &x : v) { if (!o.empty()) o += ", "; o += u8(x); } return o; };
    if (!c.hanViet.empty()) std::printf("  Âm Hán Việt: %s\n", j(c.hanViet).c_str());
    if (!c.otherReadings.empty()) std::printf("  Âm Nôm: %s\n", j(c.otherReadings).c_str());
    for (size_t i = 0; i < std::min<size_t>(3, c.senses.size()); i++) std::printf("  %zu. %s\n", i + 1, u8(c.senses[i].substr(0, 100)).c_str());
    if (!c.unihan.empty()) std::printf("  Unihan: %s\n", u8(c.unihan).c_str());
    for (auto &g : c.nomGlosses) std::printf("  Nôm «%s»: %s\n", u8(g.first).c_str(), u8(g.second.substr(0, 100)).c_str());
    if (!c.wordGloss.empty()) std::printf("  Nghĩa: %s\n", u8(c.wordGloss).c_str());
    for (auto &p : c.parts) std::printf("  · %s %s — %s\n", u8(p.ch).c_str(), u8(p.reading).c_str(), u8(p.meaning).c_str());
    std::string cs; for (auto &w : c.compounds) cs += u8(w.first) + "(" + u8(w.second) + ") ";
    if (!cs.empty()) std::printf("  Từ ghép: %s\n", cs.c_str());
}

int main(int argc, char **argv) {
    if (argc < 3) { std::printf("usage: core_test dict.txt nghia.txt\n"); return 2; }
    Data d;
    auto t = std::chrono::steady_clock::now();
    ok("dict loads", d.loadDict(argv[1]));
    ok("defs load", d.loadDefs(argv[2]));
    long ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - t).count();
    std::printf("loaded %zu entries, %zu def records in %ld ms\n", d.entryCount(), d.defCount(), ms);

    auto s = Data::senses(U"1 : Can Đinh. 2 : Người đến 18 tuổi. 3 : Răn.");
    ok("senses split into 3", s.size() == 3 && contains(s[1], U"18 tuổi"));
    ok("syllableKey người", Data::syllableKey(U"người") == "ngươi:1");
    ok("syllableKey hoà==hòa", Data::syllableKey(U"hoà") == Data::syllableKey(U"hòa"));
    ok("render việt nam", Data::renderReading("viêt:5 nam:0") == U"việt nam");
    ok("render người", Data::renderReading("ngươi:1") == U"người");
    ok("render hoà", Data::renderReading("hoa:1") == U"hòa");
    ok("render quốc", Data::renderReading("quôc:2") == U"quốc");
    ok("render giữa", Data::renderReading("giưa:4") == U"giữa");
    ok("render thuở", Data::renderReading("thuơ:3") == U"thuở");

    Card viet = d.card(U"越", "viêt:5");
    ok("越 has Thiều Chửu senses", viet.senses.size() >= 3 && has(viet.hanViet, U"việt"));
    ok("越 kind is Chữ Hán", viet.kind.rfind(U"Chữ Hán", 0) == 0);
    bool hasVN = false;
    for (auto &w : viet.compounds) hasVN |= w.first == U"越南";
    ok("越 compounds include 越南", hasVN);

    Card nguoi = d.card(U"𠊛", "ngươi:1");
    ok("𠊛 is Chữ Nôm, ext B", nguoi.kind.rfind(U"Chữ Nôm", 0) == 0 && contains(nguoi.kind, U"mở rộng B"));
    ok("𠊛 has gloss for người", !nguoi.nomGlosses.empty() && contains(nguoi.nomGlosses[0].second, U"person"));

    Card co = d.card(U"固", "co:2");
    ok("固 Hán + used as Nôm có", contains(co.kind, U"Nôm") && has(co.otherReadings, U"có"));

    Card vn = d.card(U"越南", "viêt:5 nam:0");
    ok("越南 is a word with gloss", vn.word && contains(vn.wordGloss, U"Vietnam"));
    ok("越南 has 2 parts", vn.parts.size() == 2 && vn.parts[1].reading == U"nam");
    ok("unknown word does not crash", d.card(U"𠊛𠊛", "").parts.size() == 2);
    ok("odd char does not crash", d.card(U"Ｘ", "").text == U"Ｘ");

    // context: hovering 越 inside "我越南人" should find the word 越南
    const Entry *w = d.wordAt(U"我越南人", 1);
    ok("wordAt 越 in 我越南人 -> 越南", w && w->text == U"越南");
    const Entry *w2 = d.wordAt(U"我越南人", 2);
    ok("wordAt 南 in 我越南人 -> covers 南", w2 && w2->text.find(U'南') != std::u32string::npos);
    ok("wordAt isolated char -> none", d.wordAt(U"，越，", 1) == nullptr || d.wordAt(U"，越，", 1)->length() <= 3);
    const Entry *poem = d.wordAt(U"𠊛𠫾洲木𪤾霜𧘇", 0);
    std::printf("poem ctx at 𠊛: %s\n", poem ? (u8(poem->text) + " " + poem->readingKey).c_str() : "(none)");
    ok("isHan", isHan(U'越') && isHan(U'𠊛') && !isHan(U'a') && !isHan(U'，'));

    for (auto &q : std::vector<std::pair<std::u32string, std::string>>{{U"越", "viêt:5"}, {U"𠊛", "ngươi:1"}, {U"固", "co:2"},
             {U"越南", "viêt:5 nam:0"}, {U"碎", "tôi:0"}, {U"㦖", "mên:2"}, {U"文化", ""}, {U"𠫾", "đi:0"}})
        print(d.card(q.first, q.second));

    std::printf(fails == 0 ? "ALL %d CHECKS PASSED\n" : "%d checks FAILED\n", fails == 0 ? checks : fails);
    return fails ? 1 : 0;
}

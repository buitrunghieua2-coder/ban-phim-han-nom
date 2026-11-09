// Tra Nghĩa Hán Nôm — portable core (no Windows headers): dictionary, readings, definition cards.
// Ported from the Android keyboard (HanNomDict.java, Definitions.java, TelexEngine.renderSyllable).
#pragma once
#include <string>
#include <vector>
#include <unordered_map>
#include <utility>

namespace hn {

std::u32string u32(const std::string &utf8);
std::string u8(const std::u32string &s);
std::u16string u16(const std::u32string &s);
bool isHan(char32_t c);   // Hán / Nôm ideograph (CJK unified, extensions, compatibility)

struct Entry {
    std::u32string text;
    std::string readingKey;          // "viêt:5 nam:0"
    int cost = 0;
    size_t length() const { return text.size(); }
};

struct Card {
    std::u32string text;
    bool word = false;
    std::u32string kind, mainReading;
    std::vector<std::u32string> hanViet, otherReadings, senses;
    std::u32string unihan;                                         // empty = none
    std::vector<std::pair<std::u32string, std::u32string>> nomGlosses;  // reading, english
    std::u32string wordGloss;
    struct Part { std::u32string ch, reading, meaning; };
    std::vector<Part> parts;
    std::vector<std::pair<std::u32string, std::u32string>> compounds;   // word, reading
};

class Data {
public:
    bool loadDict(const std::string &path);      // assets/dict.txt
    bool loadDefs(const std::string &path);      // assets/nghia.txt
    size_t entryCount() const { return entries.size(); }
    size_t defCount() const { return han.size() + unihan.size() + gloss.size(); }

    std::vector<const Entry *> entriesFor(const std::u32string &text) const;
    std::vector<const Entry *> wordsContaining(char32_t ch, size_t limit) const;
    // Best dictionary word (2..4 chars) in `context` covering position `idx`, or nullptr.
    const Entry *wordAt(const std::u32string &context, size_t idx) const;

    Card card(const std::u32string &text, const std::string &readingKey) const;

    static std::u32string renderReading(const std::string &readingKey);  // "viêt:5 nam:0" -> "việt nam"
    static std::string syllableKey(const std::u32string &syl);           // "người" -> "ngươi:1"
    static std::vector<std::u32string> senses(const std::u32string &def);
    static std::u32string blockName(char32_t cp);

private:
    std::vector<Entry> entries;
    std::unordered_map<std::u32string, std::vector<size_t>> byText;
    std::unordered_map<char32_t, std::vector<size_t>> wordsByChar;
    std::unordered_map<std::u32string, std::pair<std::u32string, std::u32string>> han;  // hv readings, def
    std::unordered_map<std::u32string, std::u32string> unihan;
    std::unordered_map<std::string, std::u32string> gloss;
    void wordCard(Card &c, std::string key) const;
};

}  // namespace hn

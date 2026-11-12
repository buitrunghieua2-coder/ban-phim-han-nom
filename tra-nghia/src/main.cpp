// Tra Nghĩa Hán Nôm — tray app: hold the mouse still on a Hán/Nôm character for ~0.5 s, a dictionary popup appears.
// Works across Windows apps through UI Automation (TextPattern). Rendering: Direct2D + DirectWrite.
#ifndef UNICODE
#define UNICODE
#endif
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <windowsx.h>
#include <shellapi.h>
#include <shlobj.h>
#include <shellscalingapi.h>
#include <dwmapi.h>
#include <d2d1.h>
#include <dwrite_3.h>
#include <uiautomation.h>
#include <atomic>
#include <cmath>
#include <cstdio>
#include <memory>
#include <string>
#include <vector>
#include "core.hpp"
#include "resource.h"

using hn::Card;
using hn::Data;
using hn::Entry;

// ------------------------------------------------------------------ globals
static const wchar_t *APP = L"Tra Nghĩa Hán Nôm";
static const wchar_t *REG_KEY = L"Software\\BanPhimHanNom\\TraNghia";
static const UINT WM_TRAY = WM_APP + 1, WM_HIT = WM_APP + 2, WM_LOADED = WM_APP + 3;
static const UINT_PTR TIMER_POLL = 1;
static const int HOTKEY_TOGGLE = 1;
enum { ID_ENABLE = 100, ID_CTRL, ID_STARTUP, ID_THEME_SYS, ID_THEME_LIGHT, ID_THEME_DARK, ID_DELAY_FAST, ID_DELAY_NORMAL,
       ID_DELAY_SLOW, ID_LOG, ID_ABOUT, ID_EXIT, ID_DELAY_INSTANT, ID_VERBOSE };

static HINSTANCE gInst;
static HWND gMain, gPopup;
static UINT gTaskbarCreated;
static NOTIFYICONDATAW gNid = {};
static Data gData;
static std::atomic<bool> gDataReady{false};
static std::wstring gExeDir, gLogPath;
static std::wstring gDemoCtx;      // --demo <text> <index>: show a popup without UI Automation (testing)
static int gDemoIdx = 0;
static bool gVerbose = false;      // --verbose or HKCU\...\TraNghia\Verbose=1: log every lookup, also misses

struct Settings {
    bool enabled = true, ctrlOnly = false;
    int theme = 0;      // 0 system, 1 light, 2 dark
    int delayMs = 250;
} gSet;

// ------------------------------------------------------------------ helpers
static std::wstring W(const std::u32string &s) {
    std::u16string a = hn::u16(s);
    return std::wstring(a.begin(), a.end());
}
static std::u32string U32(const std::wstring &w) {
    std::u32string o;
    for (size_t i = 0; i < w.size(); i++) {
        char32_t c = w[i];
        if (c >= 0xD800 && c <= 0xDBFF && i + 1 < w.size() && w[i + 1] >= 0xDC00 && w[i + 1] <= 0xDFFF) {
            c = 0x10000 + ((c - 0xD800) << 10) + (w[i + 1] - 0xDC00);
            i++;
        }
        o.push_back(c);
    }
    return o;
}
static std::string U8(const std::wstring &w) {
    if (w.empty()) return {};
    int n = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), nullptr, 0, nullptr, nullptr);
    std::string s(n, '\0');
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), &s[0], n, nullptr, nullptr);
    return s;
}

static void logf(const char *fmt, ...) {
    if (gLogPath.empty()) return;
    FILE *f = _wfopen(gLogPath.c_str(), L"ab");
    if (!f) return;
    SYSTEMTIME t;
    GetLocalTime(&t);
    fprintf(f, "%04d-%02d-%02d %02d:%02d:%02d.%03d ", t.wYear, t.wMonth, t.wDay, t.wHour, t.wMinute, t.wSecond, t.wMilliseconds);
    va_list ap;
    va_start(ap, fmt);
    vfprintf(f, fmt, ap);
    va_end(ap);
    fputs("\r\n", f);
    fclose(f);
}

static DWORD regGet(const wchar_t *name, DWORD def) {
    DWORD v = def, sz = sizeof v;
    RegGetValueW(HKEY_CURRENT_USER, REG_KEY, name, RRF_RT_REG_DWORD, nullptr, &v, &sz);
    return v;
}
static void regSet(const wchar_t *name, DWORD v) {
    HKEY k;
    if (RegCreateKeyExW(HKEY_CURRENT_USER, REG_KEY, 0, nullptr, 0, KEY_SET_VALUE, nullptr, &k, nullptr) == ERROR_SUCCESS) {
        RegSetValueExW(k, name, 0, REG_DWORD, (const BYTE *)&v, sizeof v);
        RegCloseKey(k);
    }
}
static void loadSettings() {
    gSet.enabled = regGet(L"Enabled", 1) != 0;
    gSet.ctrlOnly = regGet(L"CtrlOnly", 0) != 0;
    gSet.theme = (int)regGet(L"Theme", 0);
    gSet.delayMs = (int)regGet(L"Delay", 250);     // new key in 1.3 (old DelayMs defaulted to 0.5 s)
    if (gSet.delayMs < 100 || gSet.delayMs > 3000) gSet.delayMs = 250;
}
static void saveSettings() {
    regSet(L"Enabled", gSet.enabled);
    regSet(L"CtrlOnly", gSet.ctrlOnly);
    regSet(L"Theme", gSet.theme);
    regSet(L"Delay", gSet.delayMs);
}

static const wchar_t *RUN_KEY = L"Software\\Microsoft\\Windows\\CurrentVersion\\Run";
static const wchar_t *RUN_NAME = L"TraNghiaHanNom";
static bool startupEnabled() {
    return RegGetValueW(HKEY_CURRENT_USER, RUN_KEY, RUN_NAME, RRF_RT_REG_SZ, nullptr, nullptr, nullptr) == ERROR_SUCCESS;
}
static void setStartup(bool on) {
    HKEY k;
    if (RegOpenKeyExW(HKEY_CURRENT_USER, RUN_KEY, 0, KEY_SET_VALUE, &k) != ERROR_SUCCESS) return;
    if (on) {
        wchar_t path[MAX_PATH];
        GetModuleFileNameW(nullptr, path, MAX_PATH);
        std::wstring v = L"\"" + std::wstring(path) + L"\"";
        RegSetValueExW(k, RUN_NAME, 0, REG_SZ, (const BYTE *)v.c_str(), (DWORD)((v.size() + 1) * sizeof(wchar_t)));
    } else {
        RegDeleteValueW(k, RUN_NAME);
    }
    RegCloseKey(k);
}

static bool systemLight() {
    DWORD v = 1, sz = sizeof v;
    RegGetValueW(HKEY_CURRENT_USER, L"Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                 L"AppsUseLightTheme", RRF_RT_REG_DWORD, nullptr, &v, &sz);
    return v != 0;
}
static bool useLight() { return gSet.theme == 1 || (gSet.theme == 0 && systemLight()); }

static UINT dpiAt(POINT pt) {
    HMONITOR m = MonitorFromPoint(pt, MONITOR_DEFAULTTONEAREST);
    UINT x = 96, y = 96;
    typedef HRESULT(WINAPI * GetDpiForMonitorFn)(HMONITOR, MONITOR_DPI_TYPE, UINT *, UINT *);
    static GetDpiForMonitorFn fn = (GetDpiForMonitorFn)GetProcAddress(LoadLibraryW(L"shcore.dll"), "GetDpiForMonitor");
    if (fn && SUCCEEDED(fn(m, MDT_EFFECTIVE_DPI, &x, &y))) return x;
    HDC dc = GetDC(nullptr);
    x = GetDeviceCaps(dc, LOGPIXELSX);
    ReleaseDC(nullptr, dc);
    return x;
}

// ------------------------------------------------------------------ UIA worker
struct Hit {
    unsigned seq = 0;
    POINT pt{};
    std::u32string ctx;
    size_t idx = 0;
    RECT rc{};          // hovered character, screen px
    const char *via = "";
};

static CRITICAL_SECTION gReqLock;
static HANDLE gReqEvent, gQuitEvent;
static POINT gReqPt;
static unsigned gReqSeq = 0;
static bool gReqPending = false;

static bool rectsContain(SAFEARRAY *sa, POINT pt, RECT &out) {
    if (!sa) return false;
    double *d = nullptr;
    LONG lo = 0, hi = -1;
    SafeArrayGetLBound(sa, 1, &lo);
    SafeArrayGetUBound(sa, 1, &hi);
    LONG n = hi - lo + 1;
    bool hit = false;
    if (n >= 4 && SUCCEEDED(SafeArrayAccessData(sa, (void **)&d))) {
        for (LONG i = 0; i + 3 < n; i += 4) {
            double x = d[i], y = d[i + 1], w = d[i + 2], h = d[i + 3];
            if (w <= 0 || h <= 0) continue;
            if (pt.x >= x - 2 && pt.x <= x + w + 2 && pt.y >= y - 2 && pt.y <= y + h + 2) {
                out = {(LONG)x, (LONG)y, (LONG)(x + w), (LONG)(y + h)};
                hit = true;
                break;
            }
        }
        SafeArrayUnaccessData(sa);
    }
    SafeArrayDestroy(sa);
    return hit;
}

static std::wstring rangeText(IUIAutomationTextRange *r, int max) {
    BSTR b = nullptr;
    std::wstring s;
    if (SUCCEEDED(r->GetText(max, &b)) && b) { s.assign(b, SysStringLen(b)); SysFreeString(b); }
    return s;
}

static bool isHi(wchar_t c) { return c >= 0xD800 && c <= 0xDBFF; }
static bool isLo(wchar_t c) { return c >= 0xDC00 && c <= 0xDFFF; }

// Many providers count TextUnit_Character in UTF-16 units, so a character outside the BMP (CJK Ext B+, where
// most Nôm characters live: 𡗶 𠊛 𠫾 …) comes back as a lone high or low surrogate. Widen the range to the pair.
static std::wstring wholeChar(IUIAutomationTextRange *r) {
    std::wstring ch = rangeText(r, 4);
    int moved = 0;
    if (ch.size() == 1 && isHi(ch[0])) {
        r->MoveEndpointByUnit(TextPatternRangeEndpoint_End, TextUnit_Character, 1, &moved);
        ch = rangeText(r, 4);
    } else if (ch.size() == 1 && isLo(ch[0])) {
        r->MoveEndpointByUnit(TextPatternRangeEndpoint_Start, TextUnit_Character, -1, &moved);
        ch = rangeText(r, 4);
    }
    if (gVerbose) {
        std::string hex;
        char b[12];
        for (wchar_t c : ch) { snprintf(b, sizeof b, "%04X ", (unsigned)c); hex += b; }
        logf("range text [%s] moved=%d", hex.c_str(), moved);
    }
    return ch;
}


static bool fromTextPattern(IUIAutomationTextPattern *tp, POINT pt, Hit &out) {
    IUIAutomationTextRange *r = nullptr;
    HRESULT hr = tp->RangeFromPoint(pt, &r);
    if (FAILED(hr) || !r) { if (gVerbose) logf("miss: RangeFromPoint hr=0x%08lx", hr); return false; }
    bool ok = false, on = false;
    char32_t cp = 0;
    // Try the character the provider points at, then its neighbours: with characters outside the BMP
    // (𡗶 𠊛 …) many providers are one UTF-16 unit off on the right half of the glyph.
    IUIAutomationTextRange *best = nullptr, *noRect = nullptr;
    char32_t noRectCp = 0;
    static const int OFFS[3] = {0, -1, 1};
    for (int k = 0; k < 3 && !on; k++) {
        IUIAutomationTextRange *t = nullptr;
        if (FAILED(r->Clone(&t)) || !t) continue;
        if (OFFS[k]) {
            int m = 0;
            t->ExpandToEnclosingUnit(TextUnit_Character);
            t->Move(TextUnit_Character, OFFS[k], &m);
            if (m == 0) { t->Release(); continue; }
        }
        t->ExpandToEnclosingUnit(TextUnit_Character);
        std::u32string c32 = U32(wholeChar(t));
        char32_t c = 0;
        for (char32_t x : c32) if (hn::isHan(x)) { c = x; break; }   // a provider may hand back "X𡗶" or "𡗶\n"
        if (!c) { t->Release(); continue; }
        SAFEARRAY *sa = nullptr;
        bool hasRects = false;
        if (SUCCEEDED(t->GetBoundingRectangles(&sa)) && sa) {
            LONG lo = 0, hi = -1;
            SafeArrayGetLBound(sa, 1, &lo);
            SafeArrayGetUBound(sa, 1, &hi);
            hasRects = hi - lo + 1 >= 4;
            on = rectsContain(sa, pt, out.rc);       // frees sa
        }
        if (gVerbose) logf("try offset %d: U+%04X rects=%d on=%d", OFFS[k], (unsigned)c, hasRects, on);
        if (on) { best = t; cp = c; break; }
        if (!hasRects && k == 0) { noRect = t; noRectCp = c; continue; }
        t->Release();
    }
    if (!on && noRect) {
        // provider gives no geometry for this character at all: trust RangeFromPoint
        best = noRect;
        noRect = nullptr;
        cp = noRectCp;
        on = true;
        out.rc = {pt.x - 10, pt.y - 10, pt.x + 10, pt.y + 10};
        if (gVerbose) logf("accepted without rectangle");
    }
    if (noRect) noRect->Release();
    if (best) { r->Release(); r = best; }
    if (cp) {
        if (on) {
            std::u32string ctx(1, cp);
            size_t idx = 0;
            IUIAutomationTextRange *c = nullptr;
            if (SUCCEEDED(r->Clone(&c)) && c) {
                int m1 = 0, m2 = 0;
                c->MoveEndpointByUnit(TextPatternRangeEndpoint_Start, TextUnit_Character, -8, &m1);
                c->MoveEndpointByUnit(TextPatternRangeEndpoint_End, TextUnit_Character, 8, &m2);
                std::wstring cw = rangeText(c, 64);
                size_t expect = std::wstring::npos;
                IUIAutomationTextRange *p = nullptr;
                if (SUCCEEDED(c->Clone(&p)) && p) {
                    if (SUCCEEDED(p->MoveEndpointByRange(TextPatternRangeEndpoint_End, r, TextPatternRangeEndpoint_Start)))
                        expect = rangeText(p, 64).size();     // UTF-16 offset of the hovered char in cw
                    p->Release();
                }
                c->Release();
                // drop half pairs cut off at either end, then find the hovered char nearest to the expected offset
                size_t lo = 0, hi = cw.size();
                if (hi > lo && isLo(cw[lo])) lo++;
                if (hi > lo && isHi(cw[hi - 1])) hi--;
                std::u32string full;
                std::vector<size_t> at;          // UTF-16 offset of each code point
                for (size_t i = lo; i < hi; i++) {
                    at.push_back(i);
                    char32_t v = cw[i];
                    if (isHi(cw[i]) && i + 1 < hi && isLo(cw[i + 1])) { v = 0x10000 + ((v - 0xD800) << 10) + (cw[i + 1] - 0xDC00); i++; }
                    full.push_back(v);
                }
                size_t best = std::u32string::npos, bestD = SIZE_MAX;
                for (size_t k = 0; k < full.size(); k++) {
                    if (full[k] != cp) continue;
                    size_t d = expect == std::wstring::npos ? 0 : (at[k] > expect ? at[k] - expect : expect - at[k]);
                    if (d < bestD) { bestD = d; best = k; }
                }
                if (best != std::u32string::npos) { ctx = full; idx = best; }
            }
            out.ctx = ctx;
            out.idx = idx;
            ok = true;
        } else if (gVerbose) {
            logf("miss: cursor not on the character's rectangle");
        }
    }
    r->Release();
    return ok;
}

static bool fromName(IUIAutomationElement *el, POINT pt, Hit &out) {
    BSTR b = nullptr;
    if (FAILED(el->get_CurrentName(&b)) || !b) return false;
    std::u32string name = U32(std::wstring(b, SysStringLen(b)));
    SysFreeString(b);
    while (!name.empty() && (name.back() == U' ' || name.back() == U'\n')) name.pop_back();
    while (!name.empty() && name.front() == U' ') name.erase(name.begin());
    if (name.empty() || name.size() > 4) return false;
    for (char32_t c : name) if (!hn::isHan(c)) return false;
    RECT rc{};
    if (FAILED(el->get_CurrentBoundingRectangle(&rc))) return false;
    if (name.size() > 1 && gData.entriesFor(name).empty()) return false;   // only whole known words / single chars
    out.ctx = name;
    out.idx = 0;
    out.rc = rc;
    (void)pt;
    return true;
}

// Chromium/Electron apps such as Zalo: RangeFromPoint on the document lands on a whitespace node, but the
// text element under the cursor is right (its Name is the whole message). Walk that element's own range one
// character at a time and take the character whose box holds the cursor. Messages are short; capped anyway.
static bool fromChild(IUIAutomationTextPattern *tp, IUIAutomationElement *el, POINT pt, Hit &out) {
    IUIAutomationTextRange *r = nullptr;
    if (FAILED(tp->RangeFromChild(el, &r)) || !r) { if (gVerbose) logf("child: no range"); return false; }
    std::wstring all = rangeText(r, 1200);
    bool han = false;
    for (char32_t c : U32(all)) if (hn::isHan(c)) { han = true; break; }
    if (!han || all.size() > 1000) { r->Release(); if (gVerbose) logf("child: no Han (%u units)", (unsigned)all.size()); return false; }
    IUIAutomationTextRange *c = nullptr;
    if (FAILED(r->Clone(&c)) || !c) { r->Release(); return false; }
    c->MoveEndpointByRange(TextPatternRangeEndpoint_End, r, TextPatternRangeEndpoint_Start);   // empty, at start
    std::u32string full;              // code points of the element, in order
    size_t hit = std::u32string::npos;
    RECT hitRc{};
    bool anyRect = false;
    for (int guard = 0; guard < 1000; guard++) {
        int m = 0, cmp = 0;
        if (FAILED(r->CompareEndpoints(TextPatternRangeEndpoint_End, c, TextPatternRangeEndpoint_End, &cmp)) || cmp <= 0) break;
        c->MoveEndpointByUnit(TextPatternRangeEndpoint_End, TextUnit_Character, 1, &m);
        if (!m) break;
        std::wstring ch = rangeText(c, 4);
        if (ch.size() == 1 && isHi(ch[0])) { c->MoveEndpointByUnit(TextPatternRangeEndpoint_End, TextUnit_Character, 1, &m); ch = rangeText(c, 4); }
        std::u32string cp = U32(ch);
        if (!cp.empty()) {
            if (hit == std::u32string::npos) {
                SAFEARRAY *sa = nullptr;
                RECT rc{};
                if (SUCCEEDED(c->GetBoundingRectangles(&sa)) && sa) {
                    LONG lo = 0, hi = -1;
                    SafeArrayGetLBound(sa, 1, &lo);
                    SafeArrayGetUBound(sa, 1, &hi);
                    if (hi - lo + 1 >= 4) anyRect = true;
                    if (rectsContain(sa, pt, rc) && hn::isHan(cp[0])) { hit = full.size(); hitRc = rc; }
                }
            }
            full += cp;
        }
        c->MoveEndpointByRange(TextPatternRangeEndpoint_Start, c, TextPatternRangeEndpoint_End);   // next char
    }
    c->Release();
    r->Release();
    if (hit == std::u32string::npos && !anyRect) {
        // provider gives no character geometry: estimate on a single line of equal-width Han characters
        RECT er{};
        size_t n = full.size();
        if (n && SUCCEEDED(el->get_CurrentBoundingRectangle(&er)) && er.right > er.left) {
            double w = double(er.right - er.left) / n, h = er.bottom - er.top;
            if (h < 2.0 * w && pt.x >= er.left && pt.x < er.right) {
                size_t k = size_t((pt.x - er.left) / w);
                if (k < n && hn::isHan(full[k])) {
                    hit = k;
                    hitRc = {LONG(er.left + k * w), er.top, LONG(er.left + (k + 1) * w), er.bottom};
                    if (gVerbose) logf("child: estimated position");
                }
            }
        }
    }
    if (gVerbose) logf("child: %u chars, rects=%d, hit=%d", (unsigned)full.size(), anyRect, hit == std::u32string::npos ? -1 : (int)hit);
    if (hit == std::u32string::npos) return false;
    // keep a window of context around the hit, as fromTextPattern does
    size_t a = hit > 8 ? hit - 8 : 0, b = std::min(full.size(), hit + 9);
    out.ctx = full.substr(a, b - a);
    out.idx = hit - a;
    out.rc = hitRc;
    return true;
}

// Last resort for providers that expose a message only as a Text element's Name, with no usable text
// range at all (Zalo: RangeFromPoint gives spaces, RangeFromChild fails). Lay the Name out in the element's
// rectangle with typical advance widths (Hán/Nôm and full-width punctuation 1 em, Latin about half) and
// wrap it over as many lines as the rectangle's height suggests, then take the character under the cursor.
static double emWidth(char32_t c) {
    if (hn::isHan(c) || (c >= 0x2E80 && c <= 0x9FFF) || (c >= 0xF900 && c <= 0xFAFF) || (c >= 0xFF01 && c <= 0xFF60) ||
        (c >= 0x3000 && c <= 0x303F) || (c >= 0xAC00 && c <= 0xD7AF))
        return 1.0;
    if (c == U' ') return 0.27;
    if (c >= U'0' && c <= U'9') return 0.56;
    if (c >= U'A' && c <= U'Z') return 0.64;
    if (c == U'i' || c == U'l' || c == U'j' || c == U'.' || c == U',' || c == U':' || c == U';' || c == U'!' || c == U'|' || c == U'\'') return 0.26;
    if (c == U'm' || c == U'w' || c == U'M' || c == U'W') return 0.86;
    if (c < 0x2000) return 0.52;   // Latin incl. Vietnamese letters with diacritics, other narrow symbols
    return 0.8;
}

static bool fromEstimate(IUIAutomationElement *el, POINT pt, Hit &out) {
    CONTROLTYPEID ct = 0;
    if (FAILED(el->get_CurrentControlType(&ct)) || ct != UIA_TextControlTypeId) return false;
    BSTR b = nullptr;
    if (FAILED(el->get_CurrentName(&b)) || !b) return false;
    std::u32string s = U32(std::wstring(b, SysStringLen(b)));
    SysFreeString(b);
    if (s.empty() || s.size() > 600) return false;
    bool han = false;
    for (char32_t c : s) if (hn::isHan(c)) { han = true; break; }
    if (!han) return false;
    RECT r{};
    if (FAILED(el->get_CurrentBoundingRectangle(&r)) || r.right <= r.left || r.bottom <= r.top) return false;
    if (pt.x < r.left || pt.x >= r.right || pt.y < r.top || pt.y >= r.bottom) return false;
    double W = r.right - r.left, H = r.bottom - r.top, total = 0;
    for (char32_t c : s) total += emWidth(c);
    // One line: the text fills the box width, so em = W / total and the box is at most ~2 em tall (Zalo's box
    // includes generous line spacing). Learn that line height and font size from one-line messages and use them
    // for wrapped ones: lines = H / learned line height.
    static double gEm = 0, gLineH = 0;
    double em1 = W / total;
    int lines = 1;
    double em = em1;
    if (H <= em1 * 2.2 || (gLineH > 0 && H < gLineH * 1.5)) {
        lines = 1;
        if (total >= 3 && H <= em1 * 2.2) { gEm = em1; gLineH = H; }
    } else if (gLineH > 0) {
        lines = std::max(1, int(H / gLineH + 0.5));
        em = gEm;
    } else {
        for (int L = 2; L <= 40; L++) {                  // nothing learned yet: assume ~1.5 em lines
            double e = H / (L * 1.5);
            if (total * e <= L * W * 1.02) { lines = L; em = e; break; }
        }
    }
    if (em <= 0) return false;
    double lineH = H / lines;
    int row = int((pt.y - r.top) / lineH);
    double x = r.left;
    int curRow = 0;
    for (size_t i = 0; i < s.size(); i++) {
        double w = emWidth(s[i]) * em;
        if (x + w > r.right + 0.5 && x > r.left) { curRow++; x = r.left; }   // wrap
        if (curRow == row && pt.x >= x && pt.x < x + w) {
            if (!hn::isHan(s[i])) { if (gVerbose) logf("estimate: lands on non-Han U+%04X", (unsigned)s[i]); return false; }
            size_t a = i > 8 ? i - 8 : 0, e2 = std::min(s.size(), i + 9);
            out.ctx = s.substr(a, e2 - a);
            out.idx = i - a;
            LONG top = LONG(r.top + row * lineH);
            out.rc = {LONG(x), top, LONG(x + w), LONG(top + lineH)};
            if (gVerbose) logf("estimate: %u chars, %d line(s), em=%.1f px -> #%u", (unsigned)s.size(), lines, em, (unsigned)i);
            return true;
        }
        if (curRow > row) break;
        x += w;
    }
    if (gVerbose) logf("estimate: %d line(s), em=%.1f px, cursor past the text", lines, em);
    return false;
}

// Worker-thread cache: the last text container (document / edit box) and its screen rectangle. Hovering
// inside it again skips ElementFromPoint and the walk up the tree (many cross-process calls in browsers).
static IUIAutomationTextPattern *gCacheTp;
static RECT gCacheRc;
static HWND gCacheRoot;
static DWORD gCacheTime;
static IUIAutomationCacheRequest *gCacheReq;

static void dropCache() {
    if (gCacheTp) { gCacheTp->Release(); gCacheTp = nullptr; }
}

static bool textAvailable(IUIAutomationElement *e, bool cached) {
    if (!cached) return true;   // unknown: just ask for the pattern
    VARIANT v;
    VariantInit(&v);
    bool yes = SUCCEEDED(e->GetCachedPropertyValue(UIA_IsTextPatternAvailablePropertyId, &v)) && v.vt == VT_BOOL && v.boolVal;
    VariantClear(&v);
    return yes;
}

static bool hitTest(IUIAutomation *uia, IUIAutomationTreeWalker *walker, POINT pt, Hit &out) {
    HWND root = GetAncestor(WindowFromPoint(pt), GA_ROOT);
    if (gCacheTp && root == gCacheRoot && PtInRect(&gCacheRc, pt) && GetTickCount() - gCacheTime < 120000) {
        if (fromTextPattern(gCacheTp, pt, out)) { out.via = "cache"; gCacheTime = GetTickCount(); return true; }
    }
    IUIAutomationElement *el = nullptr;
    bool cached = gCacheReq != nullptr;
    HRESULT hr = cached ? uia->ElementFromPointBuildCache(pt, gCacheReq, &el) : uia->ElementFromPoint(pt, &el);
    if (FAILED(hr) || !el) { if (gVerbose) logf("miss: ElementFromPoint hr=0x%08lx", hr); return false; }
    std::string trail;
    bool got = false;
    IUIAutomationElement *cur = el;
    cur->AddRef();
    for (int lvl = 0; lvl < 40 && cur; lvl++) {
        CONTROLTYPEID ct = 0;
        if (cached) cur->get_CachedControlType(&ct); else cur->get_CurrentControlType(&ct);
        if (gVerbose) trail += std::to_string(ct - 50000) + " ";
        IUIAutomationTextPattern *tp = nullptr;
        if (textAvailable(cur, cached) &&
            SUCCEEDED(cur->GetCurrentPatternAs(UIA_TextPatternId, __uuidof(IUIAutomationTextPattern), (void **)&tp)) && tp) {
            if (gVerbose) trail += "[TextPattern] ";
            got = fromTextPattern(tp, pt, out);
            if (got) out.via = "TextPattern";
            if (!got && lvl > 0 && fromChild(tp, el, pt, out)) { got = true; out.via = "child"; }
            RECT rc{};
            if (SUCCEEDED(cur->get_CurrentBoundingRectangle(&rc)) && rc.right > rc.left) {
                dropCache();
                gCacheTp = tp;            // keep the reference
                tp = nullptr;
                gCacheRc = rc;
                gCacheRoot = root;
                gCacheTime = GetTickCount();
            }
            if (tp) tp->Release();
            break;   // the nearest text container decides; don't look further up
        }
        if (ct == UIA_WindowControlTypeId && lvl > 0) break;
        IUIAutomationElement *parent = nullptr;
        if (walker) {
            if (cached) walker->GetParentElementBuildCache(cur, gCacheReq, &parent);
            else walker->GetParentElement(cur, &parent);
        }
        cur->Release();
        cur = parent;
    }
    if (cur) cur->Release();
    if (!got && fromEstimate(el, pt, out)) { got = true; out.via = "estimate"; }
    if (!got && fromName(el, pt, out)) { got = true; out.via = "Name"; }
    if (!got && gVerbose) {
        BSTR nm = nullptr, cls = nullptr;
        el->get_CurrentName(&nm);
        el->get_CurrentClassName(&cls);
        logf("miss at %ld,%ld: types %s| class=\"%s\" name=\"%s\"", pt.x, pt.y, trail.c_str(),
             cls ? U8(std::wstring(cls, SysStringLen(cls))).c_str() : "",
             nm ? U8(std::wstring(nm, std::min<UINT>(40, SysStringLen(nm)))).c_str() : "");
        if (nm) SysFreeString(nm);
        if (cls) SysFreeString(cls);
    }
    el->Release();
    return got;
}

static DWORD WINAPI worker(void *) {
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    IUIAutomation *uia = nullptr;
    HRESULT hr = CoCreateInstance(CLSID_CUIAutomation, nullptr, CLSCTX_INPROC_SERVER, __uuidof(IUIAutomation), (void **)&uia);
    if (FAILED(hr) || !uia) { logf("UIA: CoCreateInstance failed 0x%08lx", hr); CoUninitialize(); return 1; }
    IUIAutomation2 *uia2 = nullptr;
    if (SUCCEEDED(uia->QueryInterface(__uuidof(IUIAutomation2), (void **)&uia2)) && uia2) {
        uia2->put_ConnectionTimeout(1500);
        uia2->put_TransactionTimeout(1500);
        uia2->Release();
    }
    IUIAutomationTreeWalker *walker = nullptr;
    HRESULT hw = uia->get_RawViewWalker(&walker);
    if (SUCCEEDED(uia->CreateCacheRequest(&gCacheReq)) && gCacheReq) {
        if (FAILED(gCacheReq->AddProperty(UIA_ControlTypePropertyId)) ||
            FAILED(gCacheReq->AddProperty(UIA_IsTextPatternAvailablePropertyId))) {
            gCacheReq->Release();
            gCacheReq = nullptr;
        }
    }
    logf("UIA ready%s", walker ? "" : " (no tree walker)");
    (void)hw;
    HANDLE evs[2] = {gQuitEvent, gReqEvent};
    for (;;) {
        DWORD w = WaitForMultipleObjects(2, evs, FALSE, INFINITE);
        if (w != WAIT_OBJECT_0 + 1) break;
        EnterCriticalSection(&gReqLock);
        POINT pt = gReqPt;
        unsigned seq = gReqSeq;
        bool pending = gReqPending;
        gReqPending = false;
        LeaveCriticalSection(&gReqLock);
        if (!pending) continue;
        Hit *h = new Hit;
        h->seq = seq;
        h->pt = pt;
        DWORD t0 = GetTickCount();
        if (gVerbose) logf("query %ld,%ld", pt.x, pt.y);
        bool ok = hitTest(uia, walker, pt, *h);
        DWORD dt = GetTickCount() - t0;
        if (gVerbose || dt > 400) logf("UIA %s %lu ms at %ld,%ld", ok ? h->via : "miss", dt, pt.x, pt.y);
        if (!ok) h->ctx.clear();
        PostMessageW(gMain, WM_HIT, 0, (LPARAM)h);       // misses too: they close a popup the cursor left
    }
    dropCache();
    if (gCacheReq) gCacheReq->Release();
    if (walker) walker->Release();
    uia->Release();
    CoUninitialize();
    return 0;
}

static void requestHit(POINT pt, unsigned seq) {
    EnterCriticalSection(&gReqLock);
    gReqPt = pt;
    gReqSeq = seq;
    gReqPending = true;
    LeaveCriticalSection(&gReqLock);
    SetEvent(gReqEvent);
}

// ------------------------------------------------------------------ popup rendering
static ID2D1Factory *gD2D;
static IDWriteFactory *gDW;
static IDWriteFontCollection *gNomColl;      // private collection with Nom Na Tong (null -> system)
static bool gNomInstalled;
static ID2D1HwndRenderTarget *gRT;
static IDWriteTextLayout *gLayout;
static UINT gPopupDpi = 96;

struct Palette { UINT32 bg, text, accent, muted, border; };
static Palette pal() {
    // Giấy dó (light) · Sơn thếp vàng (dark) — same names as the Weasel themes
    if (useLight()) return {0xF7EFDD, 0x2E2117, 0x9E2B1E, 0x7B6A55, 0xC9B48E};
    return {0x1F1513, 0xF3E7CD, 0xDDB04A, 0xA8957A, 0x6B4A2A};
}
static D2D1_COLOR_F col(UINT32 rgb) {
    return D2D1::ColorF(((rgb >> 16) & 255) / 255.f, ((rgb >> 8) & 255) / 255.f, (rgb & 255) / 255.f, 1.f);
}

enum Style { S_BIG, S_HEAD, S_KIND, S_LABEL, S_BODY, S_HAN, S_SMALL, S_GAP };
struct Run { UINT32 start, len; Style st; };
struct Content {
    std::wstring text;
    std::vector<Run> runs;
    void add(const std::wstring &s, Style st) {
        if (s.empty()) return;
        runs.push_back({(UINT32)text.size(), (UINT32)s.size(), st});
        text += s;
    }
    void add(const std::u32string &s, Style st) { add(W(s), st); }
    void nl(bool gap = false) {
        add(L"\n", S_BODY);
        if (gap) add(L"\n", S_GAP);
    }
};
static Content gContent;
static std::u32string gShownKey;       // what is on screen (char + word)
static RECT gShownRect;                // hovered char rect, px
static POINT gShownPt;

static std::u32string joinv(const std::vector<std::u32string> &v, const std::u32string &sep, size_t max) {
    std::u32string o;
    for (size_t i = 0; i < v.size() && i < max; i++) { if (i) o += sep; o += v[i]; }
    if (v.size() > max) o += U"…";
    return o;
}

static std::string sylAt(const std::string &key, size_t i) {
    size_t a = 0;
    for (size_t k = 0; k < i; k++) { a = key.find(' ', a); if (a == std::string::npos) return ""; a++; }
    size_t b = key.find(' ', a);
    return key.substr(a, b == std::string::npos ? std::string::npos : b - a);
}

static void buildContent(const Hit &h) {
    Content c;
    char32_t ch = h.ctx[h.idx];
    std::u32string chs(1, ch);
    const Entry *word = gData.wordAt(h.ctx, h.idx);
    std::string charKey;
    size_t wordStart = 0;
    if (word) {
        for (size_t s = (h.idx + 1 >= word->length() ? h.idx + 1 - word->length() : 0); s <= h.idx; s++)
            if (h.ctx.compare(s, word->length(), word->text) == 0) { wordStart = s; break; }
        charKey = sylAt(word->readingKey, h.idx - wordStart);
    }
    Card k = gData.card(chs, charKey);

    c.add(chs, S_BIG);
    if (!k.mainReading.empty()) { c.add(L"  ", S_HEAD); c.add(k.mainReading, S_HEAD); }
    c.nl();
    c.add(k.kind, S_KIND);
    c.nl(true);

    if (word) {
        Card wc = gData.card(word->text, word->readingKey);
        c.add(L"TRONG TỪ  ", S_LABEL);
        c.add(word->text, S_HAN);
        c.add(L"  " + W(wc.mainReading), S_HEAD);
        c.nl();
        if (!wc.wordGloss.empty()) { c.add(wc.wordGloss, S_BODY); c.nl(); }
        c.nl(true);
    }
    if (!k.hanViet.empty()) {
        c.add(L"ÂM HÁN VIỆT  ", S_LABEL);
        c.add(joinv(k.hanViet, U", ", 6), S_BODY);
        c.nl();
    }
    if (!k.otherReadings.empty()) {
        c.add(L"ÂM NÔM  ", S_LABEL);
        c.add(joinv(k.otherReadings, U", ", 10), S_BODY);
        c.nl();
    }
    if (!k.senses.empty()) {
        c.nl(true);
        c.add(L"NGHĨA HÁN VIỆT", S_LABEL);
        c.add(L"  (Thiều Chửu)", S_SMALL);
        c.nl();
        for (size_t i = 0; i < k.senses.size(); i++) {
            c.add(std::to_wstring(i + 1) + L". ", S_BODY);
            c.add(k.senses[i], S_BODY);
            c.nl();
        }
    }
    if (!k.nomGlosses.empty()) {
        c.nl(true);
        c.add(L"NGHĨA NÔM", S_LABEL);
        c.add(L"  (Wiktionary, tiếng Anh)", S_SMALL);
        c.nl();
        for (size_t i = 0; i < k.nomGlosses.size(); i++) {
            c.add(k.nomGlosses[i].first + U": ", S_HEAD);
            c.add(k.nomGlosses[i].second, S_BODY);
            c.nl();
        }
    }
    if (!k.unihan.empty()) {
        c.nl(true);
        c.add(L"UNIHAN  ", S_LABEL);
        c.add(k.unihan, S_BODY);
        c.nl();
    }
    if (k.senses.empty() && k.nomGlosses.empty() && k.unihan.empty() && !word) {
        c.add(L"Chưa có giải nghĩa cho chữ này.", S_SMALL);
        c.nl();
    }
    if (!k.compounds.empty()) {
        c.nl(true);
        c.add(L"TỪ GHÉP  ", S_LABEL);
        for (size_t i = 0; i < k.compounds.size(); i++) {
            if (i) c.add(L"   ", S_BODY);
            std::u32string wj;                                  // word joiners: never split a word or its reading
            for (char32_t x : k.compounds[i].first) { wj += x; wj += U'\u2060'; }
            c.add(wj, S_HAN);
            std::wstring r = W(k.compounds[i].second);
            for (auto &x : r) if (x == L' ') x = 0x00A0;          // keep each reading on one line
            c.add(L"\u00A0" + r, S_SMALL);
        }
        c.nl();
    }
    // trim trailing newlines
    while (!c.text.empty() && c.text.back() == L'\n') {
        c.text.pop_back();
        while (!c.runs.empty() && c.runs.back().start >= c.text.size()) c.runs.pop_back();
        if (!c.runs.empty() && c.runs.back().start + c.runs.back().len > c.text.size())
            c.runs.back().len = (UINT32)c.text.size() - c.runs.back().start;
    }
    gContent = std::move(c);
    gShownKey = chs + U"|" + (word ? word->text : U"");
}

static void initFonts() {
    std::wstring ttf = gExeDir + L"\\NomNaTong-Regular.ttf";
    IDWriteFactory3 *f3 = nullptr;
    if (GetFileAttributesW(ttf.c_str()) != INVALID_FILE_ATTRIBUTES &&
        SUCCEEDED(gDW->QueryInterface(__uuidof(IDWriteFactory3), (void **)&f3)) && f3) {
        IDWriteFontFaceReference *ref = nullptr;
        IDWriteFontSetBuilder *b = nullptr;
        IDWriteFontSet *set = nullptr;
        IDWriteFontCollection1 *coll = nullptr;
        if (SUCCEEDED(f3->CreateFontFaceReference(ttf.c_str(), nullptr, 0, DWRITE_FONT_SIMULATIONS_NONE, &ref)) &&
            SUCCEEDED(f3->CreateFontSetBuilder(&b)) && SUCCEEDED(b->AddFontFaceReference(ref)) &&
            SUCCEEDED(b->CreateFontSet(&set)) && SUCCEEDED(f3->CreateFontCollectionFromFontSet(set, &coll))) {
            gNomColl = coll;
            logf("font: private collection from %s", U8(ttf).c_str());
        }
        if (set) set->Release();
        if (b) b->Release();
        if (ref) ref->Release();
        f3->Release();
    }
    IDWriteFontCollection *sys = nullptr;
    if (SUCCEEDED(gDW->GetSystemFontCollection(&sys, FALSE)) && sys) {
        UINT32 i;
        BOOL exists = FALSE;
        sys->FindFamilyName(L"Nom Na Tong", &i, &exists);
        gNomInstalled = exists;
        sys->Release();
    }
    logf("font: Nom Na Tong installed=%d private=%d", gNomInstalled, gNomColl != nullptr);
}

static float styleSize(Style s) {
    switch (s) {
        case S_BIG: return 38.f;
        case S_HEAD: return 16.f;
        case S_HAN: return 19.f;
        case S_KIND: case S_SMALL: return 11.5f;
        case S_LABEL: return 11.f;
        case S_GAP: return 6.f;
        default: return 13.5f;
    }
}

static void releaseLayout() { if (gLayout) { gLayout->Release(); gLayout = nullptr; } }

static bool makeLayout(float maxW) {
    releaseLayout();
    if (!gRT) return false;
    IDWriteTextFormat *fmt = nullptr;
    if (FAILED(gDW->CreateTextFormat(L"Segoe UI", nullptr, DWRITE_FONT_WEIGHT_NORMAL, DWRITE_FONT_STYLE_NORMAL,
                                     DWRITE_FONT_STRETCH_NORMAL, 13.5f, L"vi-VN", &fmt)))
        return false;
    fmt->SetWordWrapping(DWRITE_WORD_WRAPPING_WRAP);
    HRESULT hr = gDW->CreateTextLayout(gContent.text.c_str(), (UINT32)gContent.text.size(), fmt, maxW, 4000.f, &gLayout);
    fmt->Release();
    if (FAILED(hr)) return false;
    Palette p = pal();
    ID2D1SolidColorBrush *bText = nullptr, *bAccent = nullptr, *bMuted = nullptr;
    gRT->CreateSolidColorBrush(col(p.text), &bText);
    gRT->CreateSolidColorBrush(col(p.accent), &bAccent);
    gRT->CreateSolidColorBrush(col(p.muted), &bMuted);
    for (const Run &r : gContent.runs) {
        DWRITE_TEXT_RANGE tr{r.start, r.len};
        gLayout->SetFontSize(styleSize(r.st), tr);
        if (r.st == S_HEAD || r.st == S_LABEL) gLayout->SetFontWeight(DWRITE_FONT_WEIGHT_SEMI_BOLD, tr);
        ID2D1SolidColorBrush *b = (r.st == S_LABEL || r.st == S_BIG) ? bAccent : (r.st == S_KIND || r.st == S_SMALL) ? bMuted : bText;
        gLayout->SetDrawingEffect(b, tr);
    }
    // Hán/Nôm runs -> Nom Na Tong (private collection if loaded, else installed font, else system fallback)
    if (gNomColl || gNomInstalled) {
        const std::wstring &t = gContent.text;
        for (size_t i = 0; i < t.size();) {
            size_t j = i;
            auto cpAt = [&](size_t k, size_t &len) -> char32_t {
                char32_t c = t[k];
                len = 1;
                if (c >= 0xD800 && c <= 0xDBFF && k + 1 < t.size()) { c = 0x10000 + ((c - 0xD800) << 10) + (t[k + 1] - 0xDC00); len = 2; }
                return c;
            };
            size_t len;
            while (j < t.size() && hn::isHan(cpAt(j, len))) j += len;
            if (j > i) {
                DWRITE_TEXT_RANGE tr{(UINT32)i, (UINT32)(j - i)};
                if (gNomColl) gLayout->SetFontCollection(gNomColl, tr);
                gLayout->SetFontFamilyName(L"Nom Na Tong", tr);
                i = j;
            } else {
                cpAt(i, len);
                i += len;
            }
        }
    }
    if (bText) bText->Release();
    if (bAccent) bAccent->Release();
    if (bMuted) bMuted->Release();
    return true;
}

static const float PAD = 14.f, MAXW = 380.f, MAXH = 520.f, BAR = 14.f;
static float gScroll = 0, gContentH = 0, gViewH = 0;   // DIP; gViewH = visible text height
static bool gDragging = false;
static float gDragY0 = 0, gDragScroll0 = 0;
static float gDemoScroll = 0;

static float maxScroll() { return std::max(0.f, gContentH - gViewH); }
static bool scrollable() { return maxScroll() > 0.5f; }
static void setScroll(float v) {
    v = std::max(0.f, std::min(maxScroll(), v));
    if (std::fabs(v - gScroll) > 0.01f) { gScroll = v; InvalidateRect(gPopup, nullptr, FALSE); }
}
// scrollbar thumb, DIP, in popup client coordinates
static D2D1_RECT_F thumbRect(D2D1_SIZE_F sz) {
    float trackTop = PAD * 0.6f, trackH = sz.height - PAD * 1.2f;
    float th = std::max(28.f, trackH * gViewH / gContentH);
    float y = trackTop + (trackH - th) * (maxScroll() > 0 ? gScroll / maxScroll() : 0);
    return D2D1::RectF(sz.width - 10.f, y, sz.width - 5.f, y + th);
}

static void ensureRT() {
    if (gRT) return;
    RECT rc;
    GetClientRect(gPopup, &rc);
    D2D1_RENDER_TARGET_PROPERTIES props = D2D1::RenderTargetProperties();
    props.dpiX = props.dpiY = (float)gPopupDpi;
    gD2D->CreateHwndRenderTarget(props, D2D1::HwndRenderTargetProperties(gPopup, D2D1::SizeU(rc.right, rc.bottom)), &gRT);
    if (gRT) gRT->SetTextAntialiasMode(D2D1_TEXT_ANTIALIAS_MODE_GRAYSCALE);
}

static void hidePopup() {
    if (IsWindowVisible(gPopup)) ShowWindow(gPopup, SW_HIDE);
    gShownKey.clear();
}

static void showPopup(const Hit &h) {
    buildContent(h);
    UINT dpi = dpiAt(h.pt);
    if (dpi != gPopupDpi || !gRT) {
        gPopupDpi = dpi;
        releaseLayout();
        if (gRT) { gRT->Release(); gRT = nullptr; }
    }
    ensureRT();
    if (!gRT || !makeLayout(MAXW)) return;
    DWRITE_TEXT_METRICS m{};
    gLayout->GetMetrics(&m);
    float s = dpi / 96.f;
    float wDip = std::max(220.f, std::min(MAXW, m.widthIncludingTrailingWhitespace)) + 2 * PAD;
    MONITORINFO mi{sizeof mi};
    GetMonitorInfoW(MonitorFromPoint(h.pt, MONITOR_DEFAULTTONEAREST), &mi);
    RECT wa = mi.rcWork;
    float limit = std::min(MAXH, (wa.bottom - wa.top) / s * 0.8f - 2 * PAD);
    gContentH = m.height;
    gViewH = std::min(m.height, limit);
    gScroll = 0;
    gDragging = false;
    if (gDemoScroll > 0) gScroll = std::min(maxScroll(), gDemoScroll);
    float hDip = gViewH + 2 * PAD;
    int w = (int)std::ceil(wDip * s), hgt = (int)std::ceil(hDip * s);
    int x = h.pt.x + (int)(12 * s);
    int y = h.rc.bottom + (int)(8 * s);
    if (x + w > wa.right) x = std::max((int)wa.left, (int)wa.right - w);
    if (y + hgt > wa.bottom) y = h.rc.top - hgt - (int)(8 * s);
    if (y < wa.top) y = wa.top;
    gShownRect = h.rc;
    gShownPt = h.pt;
    SetWindowPos(gPopup, HWND_TOPMOST, x, y, w, hgt, SWP_NOACTIVATE | SWP_SHOWWINDOW);
    gRT->Resize(D2D1::SizeU(w, hgt));
    InvalidateRect(gPopup, nullptr, FALSE);
}

static void paintPopup() {
    PAINTSTRUCT ps;
    BeginPaint(gPopup, &ps);
    ensureRT();
    if (gRT) {
        Palette p = pal();
        gRT->BeginDraw();
        gRT->Clear(col(p.bg));
        D2D1_SIZE_F sz = gRT->GetSize();
        ID2D1SolidColorBrush *br = nullptr;
        gRT->CreateSolidColorBrush(col(p.border), &br);
        if (br) {
            gRT->DrawRectangle(D2D1::RectF(0.5f, 0.5f, sz.width - 0.5f, sz.height - 0.5f), br, 1.f);
            br->SetColor(col(p.accent));
            gRT->FillRectangle(D2D1::RectF(0, 0, 3.f, sz.height), br);    // son-red / gold spine
            br->Release();
        }
        if (!gLayout && !gContent.text.empty()) makeLayout(MAXW);
        if (gLayout) {
            ID2D1SolidColorBrush *def = nullptr;
            gRT->CreateSolidColorBrush(col(p.text), &def);
            gRT->PushAxisAlignedClip(D2D1::RectF(3.f, 1.f, sz.width - 1.f, sz.height - 1.f), D2D1_ANTIALIAS_MODE_ALIASED);
            gRT->DrawTextLayout(D2D1::Point2F(PAD + 2, PAD - gScroll), gLayout, def, D2D1_DRAW_TEXT_OPTIONS_ENABLE_COLOR_FONT);
            gRT->PopAxisAlignedClip();
            if (def) def->Release();
        }
        if (scrollable()) {
            ID2D1SolidColorBrush *sb = nullptr;
            gRT->CreateSolidColorBrush(col(p.muted), &sb);
            if (sb) {
                // fade hints: content continues above / below
                ID2D1SolidColorBrush *bg = nullptr;
                gRT->CreateSolidColorBrush(col(p.bg), &bg);
                if (bg) {
                    for (int i = 0; i < 8; i++) {
                        bg->SetOpacity(0.9f - i * 0.11f);
                        if (gScroll > 0.5f) gRT->FillRectangle(D2D1::RectF(3.f, 1.f + i * 1.5f, sz.width - BAR, 2.5f + i * 1.5f), bg);
                        if (gScroll < maxScroll() - 0.5f)
                            gRT->FillRectangle(D2D1::RectF(3.f, sz.height - 2.5f - i * 1.5f, sz.width - BAR, sz.height - 1.f - i * 1.5f), bg);
                    }
                    bg->Release();
                }
                D2D1_RECT_F t = thumbRect(sz);
                sb->SetOpacity(0.18f);
                gRT->FillRoundedRectangle(D2D1::RoundedRect(D2D1::RectF(t.left, PAD * 0.6f, t.right, sz.height - PAD * 0.6f), 2.5f, 2.5f), sb);
                sb->SetOpacity(gDragging ? 0.95f : 0.7f);
                gRT->FillRoundedRectangle(D2D1::RoundedRect(t, 2.5f, 2.5f), sb);
                sb->Release();
            }
        }
        if (gRT->EndDraw() == (HRESULT)D2DERR_RECREATE_TARGET) {
            releaseLayout();
            gRT->Release();
            gRT = nullptr;
        }
    }
    EndPaint(gPopup, &ps);
}

static LRESULT CALLBACK popupProc(HWND h, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
        case WM_PAINT: paintPopup(); return 0;
        case WM_ERASEBKGND: return 1;
        case WM_MOUSEACTIVATE: return MA_NOACTIVATE;
        case WM_MOUSEWHEEL:
            if (scrollable()) setScroll(gScroll - GET_WHEEL_DELTA_WPARAM(wp) / 120.f * 54.f);
            return 0;
        case WM_LBUTTONDOWN: {
            if (!scrollable() || !gRT) return 0;
            float s = gPopupDpi / 96.f;
            float x = GET_X_LPARAM(lp) / s, y = GET_Y_LPARAM(lp) / s;
            D2D1_SIZE_F sz = gRT->GetSize();
            D2D1_RECT_F t = thumbRect(sz);
            if (x >= sz.width - BAR - 4) {                       // scrollbar area
                if (y >= t.top && y <= t.bottom) {
                    gDragging = true;
                    gDragY0 = y;
                    gDragScroll0 = gScroll;
                    SetCapture(h);
                    InvalidateRect(h, nullptr, FALSE);
                } else {
                    setScroll(gScroll + (y < t.top ? -gViewH * 0.9f : gViewH * 0.9f));   // page up / down
                }
            }
            return 0;
        }
        case WM_MOUSEMOVE:
            if (gDragging && gRT) {
                float s = gPopupDpi / 96.f;
                D2D1_SIZE_F sz = gRT->GetSize();
                D2D1_RECT_F t = thumbRect(sz);
                float trackH = sz.height - PAD * 1.2f, room = trackH - (t.bottom - t.top);
                if (room > 0) setScroll(gDragScroll0 + (GET_Y_LPARAM(lp) / s - gDragY0) * maxScroll() / room);
            }
            return 0;
        case WM_LBUTTONUP:
            if (gDragging) { gDragging = false; ReleaseCapture(); InvalidateRect(h, nullptr, FALSE); }
            return 0;
        case WM_CAPTURECHANGED: gDragging = false; return 0;
        case WM_RBUTTONUP: hidePopup(); return 0;
        case WM_NCHITTEST: return HTCLIENT;
    }
    return DefWindowProcW(h, msg, wp, lp);
}

// ------------------------------------------------------------------ tray
static void updateTray(DWORD action) {
    gNid.cbSize = sizeof gNid;
    gNid.hWnd = gMain;
    gNid.uID = 1;
    gNid.uFlags = NIF_ICON | NIF_TIP | NIF_MESSAGE;
    gNid.uCallbackMessage = WM_TRAY;
    gNid.hIcon = (HICON)LoadImageW(gInst, MAKEINTRESOURCEW(gSet.enabled ? IDI_APP : IDI_OFF), IMAGE_ICON,
                                   GetSystemMetrics(SM_CXSMICON), GetSystemMetrics(SM_CYSMICON), 0);
    std::wstring tip = std::wstring(APP) + (gSet.enabled ? L" — đang bật" : L" — đang tắt");
    if (gSet.enabled && gSet.ctrlOnly) tip += L" (giữ Ctrl)";
    if (!gDataReady) tip += L" — đang nạp từ điển…";
    wcsncpy(gNid.szTip, tip.c_str(), ARRAYSIZE(gNid.szTip) - 1);
    Shell_NotifyIconW(action, &gNid);
}

static void balloon(const wchar_t *title, const wchar_t *text) {
    NOTIFYICONDATAW n = gNid;
    n.uFlags = NIF_INFO;
    n.dwInfoFlags = NIIF_USER | NIIF_LARGE_ICON;
    n.hBalloonIcon = (HICON)LoadImageW(gInst, MAKEINTRESOURCEW(IDI_APP), IMAGE_ICON, 32, 32, 0);
    wcsncpy(n.szInfoTitle, title, ARRAYSIZE(n.szInfoTitle) - 1);
    wcsncpy(n.szInfo, text, ARRAYSIZE(n.szInfo) - 1);
    Shell_NotifyIconW(NIM_MODIFY, &n);
}

static void setEnabled(bool on, bool notify) {
    gSet.enabled = on;
    saveSettings();
    if (!on) hidePopup();
    updateTray(NIM_MODIFY);
    if (notify) balloon(APP, on ? L"Đã bật tra nghĩa. Giữ chuột yên trên chữ Hán/Nôm để xem."
                                : L"Đã tắt tra nghĩa. Nhấn Ctrl+Alt+N để bật lại.");
}

static void showMenu() {
    HMENU m = CreatePopupMenu(), theme = CreatePopupMenu(), delay = CreatePopupMenu();
    AppendMenuW(m, MF_STRING | (gSet.enabled ? MF_CHECKED : 0), ID_ENABLE, L"Bật tra nghĩa\tCtrl+Alt+N");
    AppendMenuW(m, MF_STRING | (gSet.ctrlOnly ? MF_CHECKED : 0), ID_CTRL, L"Chỉ tra khi giữ phím Ctrl");
    AppendMenuW(delay, MF_STRING | (gSet.delayMs == 150 ? MF_CHECKED : 0), ID_DELAY_INSTANT, L"Rất nhanh (0,15 giây)");
    AppendMenuW(delay, MF_STRING | (gSet.delayMs == 250 ? MF_CHECKED : 0), ID_DELAY_FAST, L"Nhanh (0,25 giây) — mặc định");
    AppendMenuW(delay, MF_STRING | (gSet.delayMs == 500 ? MF_CHECKED : 0), ID_DELAY_NORMAL, L"Vừa (0,5 giây)");
    AppendMenuW(delay, MF_STRING | (gSet.delayMs == 1000 ? MF_CHECKED : 0), ID_DELAY_SLOW, L"Chậm (1 giây)");
    AppendMenuW(m, MF_POPUP, (UINT_PTR)delay, L"Thời gian giữ chuột");
    AppendMenuW(theme, MF_STRING | (gSet.theme == 0 ? MF_CHECKED : 0), ID_THEME_SYS, L"Theo hệ thống");
    AppendMenuW(theme, MF_STRING | (gSet.theme == 1 ? MF_CHECKED : 0), ID_THEME_LIGHT, L"Sáng — Giấy dó");
    AppendMenuW(theme, MF_STRING | (gSet.theme == 2 ? MF_CHECKED : 0), ID_THEME_DARK, L"Tối — Sơn thếp vàng");
    AppendMenuW(m, MF_POPUP, (UINT_PTR)theme, L"Giao diện");
    AppendMenuW(m, MF_STRING | (startupEnabled() ? MF_CHECKED : 0), ID_STARTUP, L"Khởi động cùng Windows");
    AppendMenuW(m, MF_SEPARATOR, 0, nullptr);
    AppendMenuW(m, MF_STRING | (gVerbose ? MF_CHECKED : 0), ID_VERBOSE, L"Ghi nhật ký chi tiết (khi báo lỗi)");
    AppendMenuW(m, MF_STRING, ID_LOG, L"Mở nhật ký");
    AppendMenuW(m, MF_STRING, ID_ABOUT, L"Giới thiệu");
    AppendMenuW(m, MF_STRING, ID_EXIT, L"Thoát");
    POINT pt;
    GetCursorPos(&pt);
    SetForegroundWindow(gMain);
    TrackPopupMenu(m, TPM_RIGHTBUTTON | TPM_BOTTOMALIGN, pt.x, pt.y, 0, gMain, nullptr);
    PostMessageW(gMain, WM_NULL, 0, 0);
    DestroyMenu(m);
}

static void onCommand(int id) {
    switch (id) {
        case ID_ENABLE: setEnabled(!gSet.enabled, false); break;
        case ID_CTRL: gSet.ctrlOnly = !gSet.ctrlOnly; saveSettings(); updateTray(NIM_MODIFY); break;
        case ID_DELAY_INSTANT: gSet.delayMs = 150; saveSettings(); break;
        case ID_DELAY_FAST: gSet.delayMs = 250; saveSettings(); break;
        case ID_DELAY_NORMAL: gSet.delayMs = 500; saveSettings(); break;
        case ID_DELAY_SLOW: gSet.delayMs = 1000; saveSettings(); break;
        case ID_THEME_SYS: case ID_THEME_LIGHT: case ID_THEME_DARK:
            gSet.theme = id - ID_THEME_SYS; saveSettings(); hidePopup(); releaseLayout(); break;
        case ID_STARTUP: setStartup(!startupEnabled()); break;
        case ID_VERBOSE:
            gVerbose = !gVerbose;
            regSet(L"Verbose", gVerbose);
            logf("verbose %s", gVerbose ? "on" : "off");
            break;
        case ID_LOG: ShellExecuteW(nullptr, L"open", gLogPath.c_str(), nullptr, nullptr, SW_SHOWNORMAL); break;
        case ID_ABOUT:
            MessageBoxW(nullptr,
                        L"Tra Nghĩa Hán Nôm 1.4.4 — miễn phí, mã nguồn mở (GPL-3.0), thuộc dự án Bàn phím Hán Nôm.\n\n"
                        L"Rê chuột lên một chữ Hán hoặc chữ Nôm ở bất kỳ ứng dụng nào và giữ yên một thoáng (¼ giây): "
                        L"ô nổi sẽ hiện âm đọc, nghĩa và từ ghép.\n\n"
                        L"Phím tắt Ctrl+Alt+N: bật/tắt nhanh.\n\n"
                        L"Nguồn dữ liệu:\n"
                        L"• Hán Việt tự điển — Thiều Chửu (phạm vi công cộng)\n"
                        L"• Wiktionary tiếng Việt/Anh (CC BY-SA 4.0)\n"
                        L"• Unicode Unihan (giấy phép Unicode)\n"
                        L"• Thứ tự xếp hạng: tần suất wordfreq © Robyn Speer (CC BY-SA 4.0)\n"
                        L"• Phông Nom Na Tong — Hội Bảo tồn Di sản chữ Nôm (giấy phép MIT)\n\n"
                        L"© 2026 Bùi Trung Hiếu và Dự án Bàn phím Hán Nôm\n"
                        L"github.com/buitrunghieua2-coder/ban-phim-han-nom",
                        APP, MB_OK | MB_ICONINFORMATION);
            break;
        case ID_EXIT: DestroyWindow(gMain); break;
    }
}

// ------------------------------------------------------------------ hover tracking
static POINT gAnchor{-99999, -99999};
static DWORD gAnchorTime = 0;
static unsigned gSeq = 0, gAskedSeq = 0;
static bool gWasDown = false;

static bool overOwn(POINT pt) {
    HWND w = WindowFromPoint(pt);
    return w && (w == gPopup || GetAncestor(w, GA_ROOT) == gPopup);
}

static void poll() {
    POINT pt;
    if (!gDemoCtx.empty() || !GetCursorPos(&pt)) return;
    float s = dpiAt(pt) / 96.f;
    bool down = (GetAsyncKeyState(VK_LBUTTON) | GetAsyncKeyState(VK_RBUTTON) | GetAsyncKeyState(VK_MBUTTON)) & 0x8000;

    if (IsWindowVisible(gPopup)) {
        RECT pr;
        GetWindowRect(gPopup, &pr);
        bool inPopup = PtInRect(&pr, pt) || gDragging;
        RECT around = gShownRect, zone = gShownRect;
        InflateRect(&around, (int)(10 * s), (int)(10 * s));
        InflateRect(&zone, (int)(90 * s), (int)(60 * s));
        // Near the character the popup stays until the next lookup answers (new character -> replace,
        // nothing there -> close), so sliding along a line updates it without flicker or a full delay.
        if (!inPopup && (!PtInRect(&zone, pt) || (down && !gWasDown && !PtInRect(&around, pt)))) hidePopup();
    }
    gWasDown = down;
    static DWORD lastDbg = 0;
    if (gVerbose && GetTickCount() - lastDbg > 2000) {
        lastDbg = GetTickCount();
        logf("poll %ld,%ld enabled=%d ready=%d down=%d seq=%u asked=%u", pt.x, pt.y, gSet.enabled, (int)gDataReady, down, gSeq, gAskedSeq);
    }
    if (!gSet.enabled || !gDataReady || down) { gAnchorTime = GetTickCount(); gAnchor = pt; return; }

    int tol = (int)(4 * s);
    if (std::abs(pt.x - gAnchor.x) > tol || std::abs(pt.y - gAnchor.y) > tol) {
        gAnchor = pt;
        gAnchorTime = GetTickCount();
        gSeq++;
        return;
    }
    if (gAskedSeq == gSeq) return;
    DWORD need = IsWindowVisible(gPopup) ? 90 : (DWORD)gSet.delayMs;   // popup already open: follow quickly
    if (GetTickCount() - gAnchorTime < need) return;
    if (gSet.ctrlOnly && !(GetAsyncKeyState(VK_CONTROL) & 0x8000)) return;
    if (overOwn(pt)) return;
    if (IsWindowVisible(gPopup)) {
        RECT r = gShownRect;
        if (PtInRect(&r, pt)) { gAskedSeq = gSeq; return; }   // still on the same character
    }
    gAskedSeq = gSeq;
    requestHit(pt, gSeq);
}

static void onHit(Hit *h) {
    std::unique_ptr<Hit> keep(h);
    if (h->seq != gSeq || (!gSet.enabled && gDemoCtx.empty())) return;     // cursor moved on meanwhile
    if (h->ctx.empty() || h->idx >= h->ctx.size() || !hn::isHan(h->ctx[h->idx])) {
        if (IsWindowVisible(gPopup) && !gDragging) {
            POINT pt;
            GetCursorPos(&pt);
            RECT pr, around = gShownRect;
            GetWindowRect(gPopup, &pr);
            InflateRect(&around, 6, 6);
            if (!PtInRect(&pr, pt) && !PtInRect(&around, pt)) hidePopup();
        }
        return;
    }
    std::u32string key = h->ctx.substr(h->idx, 1) + U"|";
    const Entry *w = gData.wordAt(h->ctx, h->idx);
    key += w ? w->text : U"";
    logf("hit %s ctx=\"%s\" idx=%zu word=%s", h->via, hn::u8(h->ctx).c_str(), h->idx, w ? hn::u8(w->text).c_str() : "-");
    if (IsWindowVisible(gPopup) && key == gShownKey) return;
    showPopup(*h);
}

static DWORD WINAPI loader(void *) {
    DWORD t0 = GetTickCount();
    std::string dir = U8(gExeDir) + "\\data\\";
    bool a = gData.loadDict(dir + "dict.txt");
    bool b = gData.loadDefs(dir + "nghia.txt");
    logf("data: dict=%d (%zu) defs=%d (%zu) in %lu ms", a, gData.entryCount(), b, gData.defCount(), GetTickCount() - t0);
    PostMessageW(gMain, WM_LOADED, a && b, 0);
    return 0;
}

static LRESULT CALLBACK mainProc(HWND h, UINT msg, WPARAM wp, LPARAM lp) {
    if (msg == gTaskbarCreated && gTaskbarCreated) { updateTray(NIM_ADD); return 0; }
    switch (msg) {
        case WM_TIMER: if (wp == TIMER_POLL) poll(); return 0;
        case WM_HIT: onHit((Hit *)lp); return 0;
        case WM_LOADED:
            if (wp) {
                gDataReady = true;
                updateTray(NIM_MODIFY);
                if (!gDemoCtx.empty()) {
                    Hit *d = new Hit;
                    d->seq = gSeq;
                    d->pt = {60, 40};
                    d->rc = {50, 20, 70, 40};
                    d->ctx = U32(gDemoCtx);
                    d->idx = gDemoIdx;
                    d->via = "demo";
                    onHit(d);
                    return 0;
                }
                if (!regGet(L"Welcomed", 0)) {
                    regSet(L"Welcomed", 1);
                    balloon(APP, L"Đã sẵn sàng. Rê chuột lên chữ Hán/Nôm và giữ yên nửa giây để xem nghĩa. "
                                 L"Nhấp chuột phải vào biểu tượng này để cài đặt.");
                }
            } else {
                MessageBoxW(nullptr, L"Không đọc được dữ liệu từ điển (thư mục data cạnh tệp chạy). "
                                     L"Hãy giải nén lại toàn bộ gói rồi chạy lại.", APP, MB_OK | MB_ICONERROR);
            }
            return 0;
        case WM_HOTKEY: if (wp == HOTKEY_TOGGLE) setEnabled(!gSet.enabled, true); return 0;
        case WM_TRAY:
            switch (LOWORD(lp)) {
                case WM_RBUTTONUP: case WM_CONTEXTMENU: showMenu(); break;
                case WM_LBUTTONDBLCLK: setEnabled(!gSet.enabled, true); break;
                case WM_LBUTTONUP: showMenu(); break;
            }
            return 0;
        case WM_COMMAND: onCommand(LOWORD(wp)); return 0;
        case WM_SETTINGCHANGE: if (gSet.theme == 0) { hidePopup(); releaseLayout(); } return 0;
        case WM_DESTROY:
            KillTimer(h, TIMER_POLL);
            Shell_NotifyIconW(NIM_DELETE, &gNid);
            PostQuitMessage(0);
            return 0;
    }
    return DefWindowProcW(h, msg, wp, lp);
}

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE, PWSTR, int) {
    gInst = inst;
    HANDLE mutex = CreateMutexW(nullptr, TRUE, L"Local\\TraNghiaHanNom");
    if (GetLastError() == ERROR_ALREADY_EXISTS) {
        MessageBoxW(nullptr, L"Tra Nghĩa Hán Nôm đang chạy rồi — xem biểu tượng chữ 𡨸 ở khay hệ thống (góc phải thanh tác vụ).",
                    APP, MB_OK | MB_ICONINFORMATION);
        return 0;
    }
    wchar_t path[MAX_PATH];
    GetModuleFileNameW(nullptr, path, MAX_PATH);
    gExeDir = path;
    gExeDir = gExeDir.substr(0, gExeDir.find_last_of(L'\\'));
    wchar_t *la = nullptr;
    if (SUCCEEDED(SHGetKnownFolderPath(FOLDERID_LocalAppData, 0, nullptr, &la))) {
        std::wstring dir = std::wstring(la) + L"\\BanPhimHanNom";
        CreateDirectoryW(dir.c_str(), nullptr);
        gLogPath = dir + L"\\tra-nghia.log";
        CoTaskMemFree(la);
        WIN32_FILE_ATTRIBUTE_DATA fa;
        if (GetFileAttributesExW(gLogPath.c_str(), GetFileExInfoStandard, &fa) && fa.nFileSizeLow > 1000000) DeleteFileW(gLogPath.c_str());
    }
    OSVERSIONINFOW ov{sizeof ov};
    typedef LONG(WINAPI * RtlGetVersionFn)(OSVERSIONINFOW *);
    if (auto f = (RtlGetVersionFn)GetProcAddress(GetModuleHandleW(L"ntdll.dll"), "RtlGetVersion")) f(&ov);
    logf("=== start v1.4.4, Windows %lu.%lu build %lu, exe %s", ov.dwMajorVersion, ov.dwMinorVersion, ov.dwBuildNumber, U8(path).c_str());

    loadSettings();
    gVerbose = regGet(L"Verbose", 0) != 0;
    int argc = 0;
    if (wchar_t **argv = CommandLineToArgvW(GetCommandLineW(), &argc)) {
        for (int i = 1; i < argc; i++) {
            if (wcscmp(argv[i], L"--demo") == 0 && i + 2 < argc) { gDemoCtx = argv[i + 1]; gDemoIdx = _wtoi(argv[i + 2]); }
            if (wcscmp(argv[i], L"--dark") == 0) gSet.theme = 2;
            if (wcscmp(argv[i], L"--verbose") == 0) gVerbose = true;
            if (wcscmp(argv[i], L"--scroll") == 0 && i + 1 < argc) gDemoScroll = (float)_wtof(argv[i + 1]);
            if (wcscmp(argv[i], L"--light") == 0) gSet.theme = 1;
        }
        LocalFree(argv);
    }
    if (!gDemoCtx.empty()) { gSet.enabled = false; logf("demo ctx=\"%s\" idx=%d", U8(gDemoCtx).c_str(), gDemoIdx); }
    CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    D2D1CreateFactory(D2D1_FACTORY_TYPE_SINGLE_THREADED, &gD2D);
    DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED, __uuidof(IDWriteFactory), (IUnknown **)&gDW);
    if (!gD2D || !gDW) {
        MessageBoxW(nullptr, L"Máy không hỗ trợ Direct2D/DirectWrite.", APP, MB_OK | MB_ICONERROR);
        return 1;
    }
    initFonts();

    WNDCLASSEXW wc{sizeof wc};
    wc.lpfnWndProc = mainProc;
    wc.hInstance = inst;
    wc.lpszClassName = L"TraNghiaMain";
    wc.hIcon = LoadIconW(inst, MAKEINTRESOURCEW(IDI_APP));
    RegisterClassExW(&wc);
    WNDCLASSEXW pc{sizeof pc};
    pc.style = CS_DROPSHADOW;
    pc.lpfnWndProc = popupProc;
    pc.hInstance = inst;
    pc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    pc.lpszClassName = L"TraNghiaPopup";
    RegisterClassExW(&pc);

    gMain = CreateWindowExW(0, L"TraNghiaMain", APP, 0, 0, 0, 0, 0, nullptr, nullptr, inst, nullptr);
    gPopup = CreateWindowExW(WS_EX_TOPMOST | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE, L"TraNghiaPopup", L"", WS_POPUP,
                             0, 0, 300, 200, nullptr, nullptr, inst, nullptr);
    DWORD corner = 3;  // DWMWCP_ROUNDSMALL (Windows 11; ignored elsewhere)
    DwmSetWindowAttribute(gPopup, 33 /*DWMWA_WINDOW_CORNER_PREFERENCE*/, &corner, sizeof corner);

    gTaskbarCreated = RegisterWindowMessageW(L"TaskbarCreated");
    updateTray(NIM_ADD);
    if (!RegisterHotKey(gMain, HOTKEY_TOGGLE, MOD_CONTROL | MOD_ALT | MOD_NOREPEAT, 'N')) logf("hotkey Ctrl+Alt+N busy");

    InitializeCriticalSection(&gReqLock);
    gReqEvent = CreateEventW(nullptr, FALSE, FALSE, nullptr);
    gQuitEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    HANDLE th = CreateThread(nullptr, 0, worker, nullptr, 0, nullptr);
    CloseHandle(CreateThread(nullptr, 0, loader, nullptr, 0, nullptr));
    SetTimer(gMain, TIMER_POLL, 40, nullptr);

    MSG m;
    while (GetMessageW(&m, nullptr, 0, 0) > 0) {
        TranslateMessage(&m);
        DispatchMessageW(&m);
    }
    SetEvent(gQuitEvent);
    WaitForSingleObject(th, 2000);
    releaseLayout();
    if (gRT) gRT->Release();
    if (gNomColl) gNomColl->Release();
    gDW->Release();
    gD2D->Release();
    logf("exit");
    CloseHandle(mutex);
    return 0;
}

#include "utf8_safe.h"
#include <cassert>
#include <cstdint>
#include <iostream>
#include <string>
#include <vector>

static void check(const std::string & label,
                  const std::string & input,
                  const std::vector<uint16_t> & expected) {
    auto result = safe_unicode::utf8_to_utf16(input);
    if (result != expected) {
        std::cerr << label << ": result has " << result.size()
                  << " code units, expected " << expected.size() << '\n';
        std::abort();
    }
}

int main() {
    check("ascii", "ABC", {0x41, 0x42, 0x43});
    check("embedded nul", std::string("A\0B", 3), {0x41, 0, 0x42});
    check("accents", "Español: año", {
        0x45,0x73,0x70,0x61,0xF1,0x6F,0x6C,0x3A,0x20,0x61,0xF1,0x6F
    });
    check("Korean", "\xED\x95\x9C\xEA\xB5\xAD", {0xD55C,0xAD6D});
    check("surrogate pair", "\xF0\x9F\x98\x80", {0xD83D,0xDE00});
    check("invalid continuation", "\xE8\x9C\x20", {0xFFFD,0xFFFD,0x20});
    check("truncated Chinese token", "\xE8\x9C", {0xFFFD,0xFFFD});
    check("overlong", "\xC0\xAF", {0xFFFD,0xFFFD});
    check("surrogate scalar", "\xED\xA0\x80", {0xFFFD,0xFFFD,0xFFFD});
    check("above Unicode max", "\xF4\x90\x80\x80", {0xFFFD,0xFFFD,0xFFFD,0xFFFD});
    check("lone continuation", "\x80", {0xFFFD});
    check("empty", "", {});
    // Reproduce a repetitive token observed in the real device crash:
    std::string bad;
    for (int i=0; i<800; ++i) bad += "\xE8\x9C\x9C";
    bad += "\xE8\x9C"; // incomplete final character.
    auto out=safe_unicode::utf8_to_utf16(bad);
    assert(out.size()==802 && out[800]==0xFFFD && out[801]==0xFFFD);
    // Guard against unbounded responses from malformed inputs.
    assert(safe_unicode::utf8_to_utf16(std::string(30000, 'x')).size() == 8192);
    std::cout << "PASS: UTF-8 safety including actual crash pattern, 14 checks\n";
    return 0;
}

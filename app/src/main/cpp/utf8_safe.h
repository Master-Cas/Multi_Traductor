#pragma once

#include <algorithm>
#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

// Whisper normally provides UTF-8, but malformed or truncated model tokens
// must never reach JNI NewStringUTF (which requires valid *modified* UTF-8).
// Decode Unicode scalar values strictly and replace malformed input by U+FFFD.
// Returns UTF-16 code units suitable for JNIEnv::NewString.
namespace safe_unicode {

inline std::vector<uint16_t> utf8_to_utf16(std::string_view bytes,
                                           size_t max_input_bytes = 8192) {
    std::vector<uint16_t> out;
    const size_t limit = std::min(bytes.size(), max_input_bytes);
    out.reserve(limit);
    size_t i = 0;
    while (i < limit) {
        const auto b0 = static_cast<uint8_t>(bytes[i]);
        uint32_t cp = 0;
        size_t count = 1;
        uint32_t minimum = 0;
        if (b0 < 0x80) {
            cp = b0;
        } else if (b0 >= 0xC2 && b0 <= 0xDF) {
            cp = b0 & 0x1F;
            count = 2;
            minimum = 0x80;
        } else if (b0 >= 0xE0 && b0 <= 0xEF) {
            cp = b0 & 0x0F;
            count = 3;
            minimum = 0x800;
        } else if (b0 >= 0xF0 && b0 <= 0xF4) {
            cp = b0 & 0x07;
            count = 4;
            minimum = 0x10000;
        } else {
            out.push_back(0xFFFD);
            ++i;
            continue;
        }

        bool valid = i + count <= limit;
        if (valid) {
            for (size_t j = 1; j < count; ++j) {
                const auto bj = static_cast<uint8_t>(bytes[i + j]);
                if ((bj & 0xC0) != 0x80) {
                    valid = false;
                    break;
                }
                cp = (cp << 6) | (bj & 0x3F);
            }
        }
        if (!valid || cp < minimum || cp > 0x10FFFF ||
            (cp >= 0xD800 && cp <= 0xDFFF)) {
            out.push_back(0xFFFD);
            ++i;
            continue;
        }
        if (cp <= 0xFFFF) {
            out.push_back(static_cast<uint16_t>(cp));
        } else {
            cp -= 0x10000;
            out.push_back(static_cast<uint16_t>(0xD800 | (cp >> 10)));
            out.push_back(static_cast<uint16_t>(0xDC00 | (cp & 0x3FF)));
        }
        i += count;
    }
    return out;
}

} // namespace safe_unicode

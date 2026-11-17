#include "adts_parser.h"

#include <algorithm>
#include <array>
#include <sstream>

namespace aac {
namespace {

constexpr std::array<uint32_t, 13> kSampleRates = {
    96000, 88200, 64000, 48000, 44100, 32000, 24000,
    22050, 16000, 12000, 11025, 8000, 7350};

bool hasSync(const std::vector<uint8_t>& data, size_t offset) {
    return offset + 1 < data.size() && data[offset] == 0xff &&
           (data[offset + 1] & 0xf6u) == 0xf0u;
}

size_t skipId3v2(const std::vector<uint8_t>& data) {
    if (data.size() < 10 || data[0] != 'I' || data[1] != 'D' || data[2] != '3') {
        return 0;
    }
    const uint8_t flags = data[5];
    const std::array<uint8_t, 4> size = {data[6], data[7], data[8], data[9]};
    if (std::any_of(size.begin(), size.end(), [](uint8_t b) { return (b & 0x80u) != 0; })) {
        return 0;
    }
    const size_t tagSize = (static_cast<size_t>(size[0]) << 21) |
                           (static_cast<size_t>(size[1]) << 14) |
                           (static_cast<size_t>(size[2]) << 7) | size[3];
    return 10 + tagSize + ((flags & 0x10u) ? 10u : 0u);
}

void addIssue(ParseResult* result, size_t offset, const std::string& message, bool fatal) {
    result->issues.push_back({offset, message, fatal});
    if (fatal) {
        result->ok = false;
    }
}

}  // namespace

const char* profileName(uint8_t audioObjectType) {
    switch (audioObjectType) {
        case 1: return "AAC Main";
        case 2: return "AAC LC";
        case 3: return "AAC SSR";
        case 4: return "AAC LTP";
        default: return "reserved/unknown";
    }
}

ParseResult parseAdts(const std::vector<uint8_t>& data, bool recover) {
    ParseResult result;
    size_t offset = skipId3v2(data);
    if (offset > data.size()) {
        addIssue(&result, 0, "truncated ID3v2 tag", true);
        return result;
    }

    while (offset < data.size()) {
        if (data.size() - offset < 7) {
            addIssue(&result, offset, "truncated ADTS header", true);
            break;
        }
        if (!hasSync(data, offset)) {
            std::ostringstream message;
            message << "invalid syncword/layer at byte " << offset;
            if (!recover) {
                addIssue(&result, offset, message.str(), true);
                break;
            }
            addIssue(&result, offset, message.str() + "; scanning for next header", false);
            ++offset;
            while (offset < data.size() && !hasSync(data, offset)) {
                ++offset;
            }
            continue;
        }

        const uint8_t* h = data.data() + offset;
        const uint8_t samplingIndex = static_cast<uint8_t>((h[2] >> 2) & 0x0fu);
        const size_t frameLength = (static_cast<size_t>(h[3] & 0x03u) << 11) |
                                   (static_cast<size_t>(h[4]) << 3) |
                                   (static_cast<size_t>(h[5] & 0xe0u) >> 5);
        const bool crcPresent = (h[1] & 0x01u) == 0;
        const size_t headerLength = crcPresent ? 9 : 7;

        if (samplingIndex >= kSampleRates.size()) {
            addIssue(&result, offset, "reserved sampling_frequency_index", true);
            break;
        }
        if (frameLength < headerLength) {
            addIssue(&result, offset, "aac_frame_length is smaller than its header", true);
            break;
        }
        if (frameLength > data.size() - offset) {
            std::ostringstream message;
            message << "truncated frame: header declares " << frameLength
                    << " bytes, only " << (data.size() - offset) << " remain";
            addIssue(&result, offset, message.str(), true);
            break;
        }

        const uint8_t rawDataBlocks = static_cast<uint8_t>((h[6] & 0x03u) + 1);
        FrameInfo frame;
        frame.index = result.frames.size();
        frame.offset = offset;
        frame.frameLength = frameLength;
        frame.headerLength = headerLength;
        frame.payloadLength = frameLength - headerLength;
        frame.mpegVersion = static_cast<uint8_t>((h[1] >> 3) & 0x01u);
        frame.profile = static_cast<uint8_t>((h[2] >> 6) + 1);
        frame.samplingFrequencyIndex = samplingIndex;
        frame.sampleRate = kSampleRates[samplingIndex];
        frame.channelConfiguration = static_cast<uint8_t>(((h[2] & 0x01u) << 2) | (h[3] >> 6));
        frame.rawDataBlocks = rawDataBlocks;
        frame.crcPresent = crcPresent;
        frame.durationUs = 1000000.0 * 1024.0 * rawDataBlocks / frame.sampleRate;
        result.frames.push_back(frame);
        offset += frameLength;
    }
    return result;
}

}  // namespace aac

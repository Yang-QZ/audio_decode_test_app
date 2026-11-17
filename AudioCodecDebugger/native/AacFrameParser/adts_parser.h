#ifndef AUDIO_CODEC_DEBUGGER_ADTS_PARSER_H_
#define AUDIO_CODEC_DEBUGGER_ADTS_PARSER_H_

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace aac {

struct FrameInfo {
    size_t index = 0;
    size_t offset = 0;
    size_t frameLength = 0;
    size_t headerLength = 0;
    size_t payloadLength = 0;
    uint8_t mpegVersion = 0;  // 0=MPEG-4, 1=MPEG-2
    uint8_t profile = 0;      // Audio Object Type, ADTS profile field + 1
    uint8_t samplingFrequencyIndex = 0;
    uint32_t sampleRate = 0;
    uint8_t channelConfiguration = 0;
    uint8_t rawDataBlocks = 0;  // Number of AAC raw_data_blocks in this ADTS frame.
    bool crcPresent = false;
    double durationUs = 0.0;
};

struct ParseIssue {
    size_t offset = 0;
    std::string message;
    bool fatal = false;
};

struct ParseResult {
    std::vector<FrameInfo> frames;
    std::vector<ParseIssue> issues;
    bool ok = true;
};

ParseResult parseAdts(const std::vector<uint8_t>& data, bool recover = false);
const char* profileName(uint8_t audioObjectType);

}  // namespace aac

#endif  // AUDIO_CODEC_DEBUGGER_ADTS_PARSER_H_

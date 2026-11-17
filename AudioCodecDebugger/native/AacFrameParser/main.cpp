#include "adts_parser.h"

#include <fstream>
#include <iomanip>
#include <iostream>
#include <iterator>
#include <string>
#include <vector>

namespace {

void usage(const char* program) {
    std::cerr << "Usage: " << program << " [--csv|--json] [--recover] <input.aac>\n"
              << "Parses ADTS framing only; it does not decode AAC payload.\n";
}

std::string escapeJson(const std::string& value) {
    std::string out;
    for (char c : value) {
        if (c == '\\' || c == '"') out += '\\';
        if (c == '\n' || c == '\r') continue;
        out += c;
    }
    return out;
}

}  // namespace

int main(int argc, char** argv) {
    enum class Format { kText, kCsv, kJson } format = Format::kText;
    bool recover = false;
    std::string inputPath;
    for (int i = 1; i < argc; ++i) {
        const std::string arg = argv[i];
        if (arg == "--csv") format = Format::kCsv;
        else if (arg == "--json") format = Format::kJson;
        else if (arg == "--recover") recover = true;
        else if (arg == "--help" || arg == "-h") { usage(argv[0]); return 0; }
        else if (!arg.empty() && arg[0] == '-') { usage(argv[0]); return 2; }
        else if (inputPath.empty()) inputPath = arg;
        else { usage(argv[0]); return 2; }
    }
    if (inputPath.empty()) { usage(argv[0]); return 2; }

    std::ifstream input(inputPath, std::ios::binary);
    if (!input) { std::cerr << "Cannot open: " << inputPath << '\n'; return 2; }
    const std::vector<uint8_t> data((std::istreambuf_iterator<char>(input)),
                                    std::istreambuf_iterator<char>());
    const aac::ParseResult result = aac::parseAdts(data, recover);

    if (format == Format::kCsv) {
        std::cout << "index,offset,frame_length,header_length,payload_length,profile,sample_rate,"
                     "channel_configuration,raw_data_blocks,crc_present,duration_us\n";
        for (const auto& f : result.frames) {
            std::cout << f.index << ',' << f.offset << ',' << f.frameLength << ','
                      << f.headerLength << ',' << f.payloadLength << ','
                      << static_cast<unsigned>(f.profile) << ',' << f.sampleRate << ','
                      << static_cast<unsigned>(f.channelConfiguration) << ','
                      << static_cast<unsigned>(f.rawDataBlocks) << ',' << f.crcPresent << ','
                      << std::fixed << std::setprecision(3) << f.durationUs << '\n';
        }
        for (const auto& issue : result.issues) {
            std::cerr << (issue.fatal ? "ERROR" : "WARN") << " @" << issue.offset
                      << ": " << issue.message << '\n';
        }
    } else if (format == Format::kJson) {
        std::cout << "{\"ok\":" << (result.ok ? "true" : "false") << ",\"frames\":[";
        for (size_t i = 0; i < result.frames.size(); ++i) {
            const auto& f = result.frames[i];
            if (i) std::cout << ',';
            std::cout << "{\"index\":" << f.index << ",\"offset\":" << f.offset
                      << ",\"frameLength\":" << f.frameLength << ",\"profile\":" 
                      << static_cast<unsigned>(f.profile) << ",\"sampleRate\":" << f.sampleRate
                      << ",\"channelConfiguration\":" << static_cast<unsigned>(f.channelConfiguration)
                      << ",\"rawDataBlocks\":" << static_cast<unsigned>(f.rawDataBlocks)
                      << ",\"crcPresent\":" << (f.crcPresent ? "true" : "false")
                      << ",\"durationUs\":" << std::fixed << std::setprecision(3) << f.durationUs << '}';
        }
        std::cout << "],\"issues\":[";
        for (size_t i = 0; i < result.issues.size(); ++i) {
            if (i) std::cout << ',';
            std::cout << "{\"offset\":" << result.issues[i].offset << ",\"fatal\":"
                      << (result.issues[i].fatal ? "true" : "false") << ",\"message\":\""
                      << escapeJson(result.issues[i].message) << "\"}";
        }
        std::cout << "]}\n";
    } else {
        for (const auto& f : result.frames) {
            std::cout << "frame=" << f.index << " offset=" << f.offset
                      << " length=" << f.frameLength << " payload=" << f.payloadLength
                      << " profile=" << static_cast<unsigned>(f.profile)
                      << " (" << aac::profileName(f.profile) << ')'
                      << " sample_rate=" << f.sampleRate
                      << " channel_config=" << static_cast<unsigned>(f.channelConfiguration)
                      << " raw_data_blocks=" << static_cast<unsigned>(f.rawDataBlocks)
                      << " crc=" << (f.crcPresent ? "present" : "absent")
                      << std::fixed << std::setprecision(3) << " duration_us=" << f.durationUs << '\n';
        }
        for (const auto& issue : result.issues) {
            std::cerr << (issue.fatal ? "ERROR" : "WARN") << " @" << issue.offset
                      << ": " << issue.message << '\n';
        }
        double totalUs = 0.0;
        for (const auto& f : result.frames) totalUs += f.durationUs;
        std::cout << "frames=" << result.frames.size() << std::fixed << std::setprecision(3)
                  << " duration_us=" << totalUs << " status=" << (result.ok ? "ok" : "error") << '\n';
        if (!result.frames.empty() && result.frames[0].channelConfiguration == 0) {
            std::cout << "note: channel_configuration=0 requires PCE parsing; channel count is unknown\n";
        }
    }
    return result.ok ? 0 : 1;
}

#!/usr/bin/env python3
"""Dependency-free ADTS structural parser, matching the native C++ experiment."""
import argparse
import json
from pathlib import Path

RATES = (96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)


def parse(data: bytes):
    frames, issues = [], []
    offset = 0
    if data[:3] == b"ID3" and len(data) >= 10:
        if any(b & 0x80 for b in data[6:10]):
            raise ValueError("invalid ID3 synchsafe size")
        offset = 10 + (data[6] << 21) + (data[7] << 14) + (data[8] << 7) + data[9]
        offset += 10 if data[5] & 0x10 else 0
    while offset < len(data):
        if len(data) - offset < 7:
            issues.append({"offset": offset, "error": "truncated ADTS header"})
            break
        h = data[offset:offset + 9]
        if h[0] != 0xFF or h[1] & 0xF6 != 0xF0:
            issues.append({"offset": offset, "error": "invalid syncword/layer"})
            break
        index = (h[2] >> 2) & 0xF
        if index >= len(RATES):
            issues.append({"offset": offset, "error": "reserved sampling_frequency_index"})
            break
        length = ((h[3] & 3) << 11) | (h[4] << 3) | (h[5] >> 5)
        header_length = 7 if h[1] & 1 else 9
        if length < header_length or length > len(data) - offset:
            issues.append({"offset": offset, "error": "invalid or truncated frame length",
                           "declaredLength": length, "remaining": len(data) - offset})
            break
        blocks = (h[6] & 3) + 1
        frames.append({"index": len(frames), "offset": offset, "frameLength": length,
                       "payloadLength": length - header_length, "profile": (h[2] >> 6) + 1,
                       "sampleRate": RATES[index], "channelConfiguration": ((h[2] & 1) << 2) | (h[3] >> 6),
                       "rawDataBlocks": blocks, "crcPresent": header_length == 9,
                       "durationUs": 1_000_000 * 1024 * blocks / RATES[index]})
        offset += length
    return {"ok": not issues, "frames": frames, "issues": issues,
            "totalDurationUs": sum(f["durationUs"] for f in frames)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = parse(args.input.read_bytes())
    text = json.dumps(result, indent=2)
    if args.output:
        args.output.write_text(text + "\n", encoding="utf-8")
    print(text)
    raise SystemExit(0 if result["ok"] else 1)


if __name__ == "__main__":
    main()

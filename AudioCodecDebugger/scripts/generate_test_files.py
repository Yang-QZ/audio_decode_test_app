#!/usr/bin/env python3
"""Generate deterministic PCM/WAV and ADTS parser fixtures; optionally use FFmpeg."""
import argparse
import json
import math
import shutil
import struct
import subprocess
import wave
from pathlib import Path


def make_adts_frame(payload: bytes, object_type: int = 2, sample_index: int = 3,
                    channels: int = 2, crc: bool = False) -> bytes:
    if not 1 <= object_type <= 4 or not 0 <= sample_index <= 12 or not 0 <= channels <= 7:
        raise ValueError("invalid ADTS fixture parameter")
    header_length = 9 if crc else 7
    frame_length = header_length + len(payload)
    if frame_length >= 8192:
        raise ValueError("ADTS frame exceeds 13-bit length")
    header = bytearray(header_length)
    header[0] = 0xFF
    header[1] = 0xF0 | (0 if crc else 1)
    header[2] = ((object_type - 1) << 6) | (sample_index << 2) | ((channels >> 2) & 1)
    header[3] = ((channels & 3) << 6) | ((frame_length >> 11) & 3)
    header[4] = (frame_length >> 3) & 0xFF
    header[5] = ((frame_length & 7) << 5) | 0x1F
    header[6] = 0xFC  # fullness=0x7FF, number_of_raw_data_blocks_in_frame=0
    return bytes(header) + payload


def generate_pcm(path: Path, seconds: float, rate: int, channels: int) -> int:
    frames = round(seconds * rate)
    with path.open("wb") as out:
        for i in range(frames):
            value = round(12000 * math.sin(2 * math.pi * 997 * i / rate))
            for channel in range(channels):
                sample = value if channel == 0 else round(value * 0.5)
                out.write(struct.pack("<h", sample))
    return frames


def run(command: list[str]) -> None:
    print("+", " ".join(command))
    subprocess.run(command, check=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("test_data"))
    parser.add_argument("--seconds", type=float, default=5.0)
    parser.add_argument("--sample-rate", type=int, default=48000)
    parser.add_argument("--channels", type=int, default=2)
    parser.add_argument("--encode", action="store_true", help="also create decodable AAC/M4A with FFmpeg")
    args = parser.parse_args()
    if args.seconds <= 0 or args.channels not in (1, 2):
        parser.error("seconds must be positive and channels must be 1 or 2")
    args.output.mkdir(parents=True, exist_ok=True)

    pcm = args.output / "sine_48k_stereo_s16le.pcm"
    frames = generate_pcm(pcm, args.seconds, args.sample_rate, args.channels)
    wav_path = args.output / "sine.wav"
    with wave.open(str(wav_path), "wb") as wav:
        wav.setnchannels(args.channels)
        wav.setsampwidth(2)
        wav.setframerate(args.sample_rate)
        wav.writeframes(pcm.read_bytes())

    fixture = b"".join(make_adts_frame(bytes((i * 37 + j) & 0xFF for j in range(80 + i * 11)))
                       for i in range(5))
    fixture_path = args.output / "parser_only_5_frames.adts"
    fixture_path.write_bytes(fixture)
    (args.output / "truncated.adts").write_bytes(fixture[:-13])
    (args.output / "bad_sync.adts").write_bytes(b"NOT_ADTS" + fixture)

    manifest = {
        "pcm": {"path": pcm.name, "sampleRate": args.sample_rate, "channels": args.channels,
                "format": "s16le", "framesPerChannel": frames},
        "parserFixture": {"path": fixture_path.name, "frames": 5, "decodable": False,
                          "warning": "Synthetic payload is for ADTS header tests only, not AAC decoding."},
        "ffmpegGenerated": False,
    }
    if args.encode:
        ffmpeg = shutil.which("ffmpeg")
        if not ffmpeg:
            parser.error("--encode requires ffmpeg on PATH")
        common = [ffmpeg, "-hide_banner", "-loglevel", "warning", "-y",
                  "-f", "s16le", "-ar", str(args.sample_rate), "-ac", str(args.channels), "-i", str(pcm)]
        run(common + ["-c:a", "aac", "-b:a", "128k", "-f", "adts", str(args.output / "sine.aac")])
        run(common + ["-c:a", "aac", "-b:a", "128k", str(args.output / "sine.m4a")])
        run([ffmpeg, "-hide_banner", "-loglevel", "warning", "-y", "-i", str(args.output / "sine.m4a"),
             "-f", "s16le", "-acodec", "pcm_s16le", str(args.output / "decoded_ffmpeg_s16le.pcm")])
        manifest["ffmpegGenerated"] = True
    (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()

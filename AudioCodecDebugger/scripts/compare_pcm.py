#!/usr/bin/env python3
"""Compare interleaved signed 16-bit PCM/WAV, including offset and delay diagnostics."""
import argparse
import array
import json
import math
import sys
import wave
from pathlib import Path


def read_pcm(path: Path, wav_info=False):
    if path.suffix.lower() == ".wav":
        with wave.open(str(path), "rb") as wav:
            if wav.getsampwidth() != 2 or wav.getcomptype() != "NONE":
                raise ValueError("only uncompressed 16-bit WAV is supported")
            samples = array.array("h", wav.readframes(wav.getnframes()))
            rate, channels = wav.getframerate(), wav.getnchannels()
    else:
        samples = array.array("h", path.read_bytes())
        rate = channels = None
    if sys.byteorder != "little":
        samples.byteswap()
    return samples, rate, channels


def lag_metric(a, b, channels, lag, limit=100000):
    """Mean absolute difference of channel zero; lag>0 means b starts later."""
    start = max(0, lag) * channels
    other = max(0, -lag) * channels
    count = min(limit, (len(a) - start) // channels, (len(b) - other) // channels)
    if count <= 0:
        return float("inf")
    step = max(1, count // 20000)
    total = used = 0
    for i in range(0, count, step):
        total += abs(a[start + i * channels] - b[other + i * channels])
        used += 1
    return total / used


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("reference", type=Path)
    p.add_argument("candidate", type=Path)
    p.add_argument("--sample-rate", type=int, required=True)
    p.add_argument("--channels", type=int, required=True)
    p.add_argument("--offset-a", type=int, default=0, help="reference offset in samples per channel")
    p.add_argument("--offset-b", type=int, default=0, help="candidate offset in samples per channel")
    p.add_argument("--scan-delay", type=int, default=0, metavar="FRAMES",
                   help="search +/- this many samples per channel before comparison")
    p.add_argument("--json", action="store_true")
    args = p.parse_args()
    if args.channels <= 0 or args.sample_rate <= 0 or min(args.offset_a, args.offset_b) < 0:
        p.error("rate/channels must be positive and offsets non-negative")
    a, _, _ = read_pcm(args.reference)
    b, _, _ = read_pcm(args.candidate)
    if len(a) % args.channels or len(b) % args.channels:
        p.error("PCM byte stream is not aligned to complete interleaved frames")

    detected_lag = 0
    if args.scan_delay:
        candidates = range(-args.scan_delay, args.scan_delay + 1)
        detected_lag = min(candidates, key=lambda lag: lag_metric(a, b, args.channels, lag))
        args.offset_b = max(0, args.offset_b + detected_lag)
        args.offset_a = max(0, args.offset_a - detected_lag)
    ia, ib = args.offset_a * args.channels, args.offset_b * args.channels
    count = min((len(a) - ia) // args.channels, (len(b) - ib) // args.channels)
    if count <= 0:
        p.error("no comparable samples after applying offsets")
    n = count * args.channels
    squared_error = squared_reference = 0.0
    absolute_error = different = peak = 0
    for x, y in zip(a[ia:ia+n], b[ib:ib+n]):
        error = x - y
        different += error != 0
        absolute_error += abs(error)
        peak = max(peak, abs(error))
        squared_error += error * error
        squared_reference += x * x
    mse = squared_error / n
    result = {
        "sampleRate": args.sample_rate, "channels": args.channels,
        "referenceFramesPerChannel": len(a) // args.channels,
        "candidateFramesPerChannel": len(b) // args.channels,
        "comparedFramesPerChannel": count, "offsetA": args.offset_a, "offsetB": args.offset_b,
        "detectedLag": detected_lag, "differentSamples": different,
        "differentSampleRatio": different / n, "meanAbsoluteError": absolute_error / n,
        "rmsError": math.sqrt(mse), "peakAbsoluteError": peak,
        "snrDb": 10 * math.log10(squared_reference / squared_error) if squared_error and squared_reference else None,
        "bitExactComparedRegion": different == 0,
        "note": "Lossy AAC round trips are not expected to be bit-exact; verify priming/padding separately.",
    }
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()

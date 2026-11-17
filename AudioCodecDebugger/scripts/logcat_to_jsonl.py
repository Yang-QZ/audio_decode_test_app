#!/usr/bin/env python3
"""Convert AAC_TRACE logcat lines from the optional native patch to joined JSONL."""
import argparse
import json
import re
import sys
from pathlib import Path

TOKEN = re.compile(r"([A-Za-z][A-Za-z0-9]*)=(\"[^\"]*\"|\S+)")


def parse_line(line: str):
    marker = line.find("AAC_TRACE ")
    if marker < 0:
        return None
    parts = line[marker + len("AAC_TRACE "):].strip().split(None, 1)
    if not parts:
        return None
    result = {"event": parts[0]}
    thread = re.search(r"\b(\d+)\s+(\d+)\s+[VDIWEA]\s+[^:]*:\s*$", line[:marker])
    if thread:
        result["pid"], result["tid"] = int(thread.group(1)), int(thread.group(2))
    for key, value in TOKEN.findall(parts[1] if len(parts) > 1 else ""):
        if value.startswith('"'):
            result[key] = value[1:-1]
        else:
            try:
                result[key] = int(value, 0)
            except ValueError:
                result[key] = value
    result.setdefault("component", "c2.android.aac.decoder")
    return result


def convert(lines):
    active = {}
    for line in lines:
        event = parse_line(line)
        if not event:
            continue
        key = (event["component"], event.get("frameIndex", -1))
        new_session = key not in active
        repeated_decode = event["event"] == "decode_start" and key in active and "decodeStartNs" in active[key]
        if new_session or repeated_decode:
            if key in active:
                yield active.pop(key)
            active[key] = {"event": "codec_trace", "component": event["component"],
                           "frameIndex": event.get("frameIndex", -1)}
        record = active[key]
        for field, value in event.items():
            if field == "event":
                continue
            if field == "result" and event["event"] == "decode_frame":
                record["decodeResult"] = value
            elif field == "queueTimeNs" and event["event"] == "queue":
                record[field] = value  # Component queue is closer to decode than work_create.
            else:
                record[field] = value
        record["observedEvents"] = record.get("observedEvents", []) + [event["event"]]
    yield from active.values()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="threadtime logcat text")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    with args.input.open(encoding="utf-8", errors="replace") as stream:
        records = list(convert(stream))
    if not records:
        print("no AAC_TRACE records found", file=sys.stderr)
        raise SystemExit(1)
    text = "".join(json.dumps(record, separators=(",", ":")) + "\n" for record in records)
    if args.output:
        args.output.write_text(text, encoding="utf-8")
    else:
        print(text, end="")
    incomplete = sum("decodeStartNs" not in r or "decodeEndNs" not in r for r in records)
    if incomplete:
        print(f"warning: {incomplete} records lack decode start/end; check Flush/Stop/log loss", file=sys.stderr)


if __name__ == "__main__":
    main()

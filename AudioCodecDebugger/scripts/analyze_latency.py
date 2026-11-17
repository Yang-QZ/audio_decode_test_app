#!/usr/bin/env python3
"""Summarize monotonic timing fields from CodecTrace CSV or JSONL."""
import argparse
import csv
import json
import math
from pathlib import Path

FIELDS = ("queueDelayNs", "decodeCostNs", "callbackLagNs", "outputWaitNs")


def records(path: Path):
    if path.suffix.lower() == ".csv":
        with path.open(newline="", encoding="utf-8-sig") as stream:
            yield from csv.DictReader(stream)
    else:
        with path.open(encoding="utf-8") as stream:
            for line_number, line in enumerate(stream, 1):
                if line.strip():
                    try:
                        yield json.loads(line)
                    except json.JSONDecodeError as error:
                        raise ValueError(f"{path}:{line_number}: {error}") from error


def percentile(values, fraction):
    if not values:
        return None
    values = sorted(values)
    return values[max(0, math.ceil(fraction * len(values)) - 1)]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("input", type=Path)
    p.add_argument("--output", type=Path)
    args = p.parse_args()
    rows = list(records(args.input))
    result = {"recordCount": len(rows), "clock": "monotonic", "metrics": {}, "warnings": []}
    for field in FIELDS:
        values = []
        for row in rows:
            value = row.get(field)
            if value not in (None, ""):
                values.append(int(value))
        if values:
            result["metrics"][field] = {
                "count": len(values), "minMs": min(values) / 1e6,
                "meanMs": (sum(values) / len(values)) / 1e6,
                "p95Ms": percentile(values, .95) / 1e6, "p99Ms": percentile(values, .99) / 1e6,
                "maxMs": max(values) / 1e6,
            }
    derived = {"queueDelayNs": ("decodeStartNs", "queueTimeNs"),
               "decodeCostNs": ("decodeEndNs", "decodeStartNs"),
               "callbackLagNs": ("callbackTimeNs", "decodeEndNs")}
    for name, (end, start) in derived.items():
        if name in result["metrics"]:
            continue
        values = [int(row[end]) - int(row[start]) for row in rows
                  if row.get(end) not in (None, "") and row.get(start) not in (None, "")]
        if values:
            if min(values) < 0:
                result["warnings"].append(f"negative {name}: check clock domain and event ordering")
            result["metrics"][name] = {
                "count": len(values), "minMs": min(values) / 1e6,
                "meanMs": (sum(values) / len(values)) / 1e6,
                "p95Ms": percentile(values, .95) / 1e6, "p99Ms": percentile(values, .99) / 1e6,
                "maxMs": max(values) / 1e6,
            }
    if not result["metrics"]:
        result["warnings"].append("no supported timing fields found")
    text = json.dumps(result, indent=2)
    print(text)
    if args.output:
        args.output.write_text(text + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()

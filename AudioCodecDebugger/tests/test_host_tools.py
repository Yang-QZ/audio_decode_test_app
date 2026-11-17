import csv
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS))
import adts_inspect  # noqa: E402
import generate_test_files  # noqa: E402


class AdtsTests(unittest.TestCase):
    def test_five_frames(self):
        frame = generate_test_files.make_adts_frame(bytes(range(80)))
        result = adts_inspect.parse(frame * 5)
        self.assertTrue(result["ok"])
        self.assertEqual(len(result["frames"]), 5)
        self.assertEqual(result["frames"][0]["sampleRate"], 48000)
        self.assertEqual(result["frames"][0]["profile"], 2)
        self.assertEqual(result["frames"][0]["channelConfiguration"], 2)
        self.assertAlmostEqual(result["frames"][0]["durationUs"], 21333.333333, places=5)
        self.assertEqual([f["offset"] for f in result["frames"]], [i * 87 for i in range(5)])

    def test_crc_and_truncation(self):
        frame = generate_test_files.make_adts_frame(b"payload", crc=True)
        self.assertTrue(adts_inspect.parse(frame)["frames"][0]["crcPresent"])
        self.assertFalse(adts_inspect.parse(frame[:-1])["ok"])
        self.assertFalse(adts_inspect.parse(b"not an adts stream")["ok"])

    def test_native_if_provided(self):
        executable = os.environ.get("AAC_FRAME_PARSER")
        if not executable:
            self.skipTest("set AAC_FRAME_PARSER to test the compiled C++ executable")
        frame = generate_test_files.make_adts_frame(b"x" * 80)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "two.adts"
            path.write_bytes(frame * 2)
            output = subprocess.check_output([executable, "--json", str(path)], text=True)
        self.assertEqual(len(json.loads(output)["frames"]), 2)


class CommandTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def run_tool(self, name, *args):
        return subprocess.run([sys.executable, str(SCRIPTS / name), *map(str, args)],
                              text=True, capture_output=True, check=True)

    def test_generate_and_compare(self):
        self.run_tool("generate_test_files.py", "--output", self.root, "--seconds", "0.02")
        pcm = self.root / "sine_48k_stereo_s16le.pcm"
        result = json.loads(self.run_tool("compare_pcm.py", pcm, pcm, "--sample-rate", "48000",
                                          "--channels", "2", "--json").stdout)
        self.assertTrue(result["bitExactComparedRegion"])
        self.assertEqual(result["differentSamples"], 0)
        duration = adts_inspect.parse((self.root / "parser_only_5_frames.adts").read_bytes())["totalDurationUs"]
        self.assertAlmostEqual(duration, 106666.666666, places=4)

    def test_latency(self):
        trace = self.root / "trace.csv"
        with trace.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=["queueTimeNs", "decodeStartNs", "decodeEndNs", "callbackTimeNs"])
            writer.writeheader()
            for i in range(1, 101):
                writer.writerow({"queueTimeNs": 0, "decodeStartNs": i * 1000,
                                 "decodeEndNs": i * 2000, "callbackTimeNs": i * 3000})
        result = json.loads(self.run_tool("analyze_latency.py", trace).stdout)
        self.assertEqual(result["metrics"]["decodeCostNs"]["count"], 100)
        self.assertAlmostEqual(result["metrics"]["decodeCostNs"]["p95Ms"], 0.095)

    def test_native_log_conversion(self):
        import logcat_to_jsonl
        lines = [
            "11-04 10:00:00.000  100  101 I CCodecBufferChannel: AAC_TRACE work_create component=c2.android.aac.decoder frameIndex=7 ptsUs=21333 inputBytes=87 queueTimeNs=1000\n",
            "11-04 10:00:00.001  200  201 I SimpleC2Component: AAC_TRACE queue component=c2.android.aac.decoder frameIndex=7 ptsUs=21333 queueTimeNs=2000\n",
            "11-04 10:00:00.002  200  202 I C2SoftAacDec: AAC_TRACE decode_start frameIndex=7 ptsUs=21333 inputBytes=87 flags=0 decodeStartNs=3000 injectedDelayMs=0\n",
            "11-04 10:00:00.003  200  202 I C2SoftAacDec: AAC_TRACE decode_frame frameIndex=7 result=0x0 consumedBytes=87 sampleRate=48000 channels=2 frameSize=1024 outputDelay=1024\n",
            "11-04 10:00:00.004  200  202 I C2SoftAacDec: AAC_TRACE output frameIndex=7 ptsUs=21333 outputBytes=4096 sampleRate=48000 channels=2\n",
            "11-04 10:00:00.005  200  202 I C2SoftAacDec: AAC_TRACE decode_end frameIndex=7 decodeStartNs=3000 decodeEndNs=5000 result=0 workletsProcessed=1\n",
            "11-04 10:00:00.006  200  202 I SimpleC2Component: AAC_TRACE callback component=c2.android.aac.decoder frameIndex=7 callbackTimeNs=6000 result=0\n",
        ]
        records = list(logcat_to_jsonl.convert(lines))
        self.assertEqual(len(records), 1)
        record = records[0]
        self.assertEqual(record["queueTimeNs"], 2000)
        self.assertEqual(record["decodeStartNs"], 3000)
        self.assertEqual(record["decodeEndNs"], 5000)
        self.assertEqual(record["callbackTimeNs"], 6000)
        self.assertEqual(record["outputBytes"], 4096)
        self.assertEqual(record["decodeResult"], 0)
        self.assertEqual(record["pid"], 200)


if __name__ == "__main__":
    unittest.main()

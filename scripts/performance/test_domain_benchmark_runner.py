import copy
from contextlib import redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("runner", Path(__file__).with_name("run-domain-benchmark.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class CompletionTest(unittest.TestCase):
    def setUp(self):
        self.options = SimpleNamespace(profile="smoke", scenario="ledger", samples=1, warmup=0)
        self.report = {
            "schemaVersion": 1, "status": "PASSED", "profile": "smoke",
            "scenarioSelection": "ledger", "sampleCount": 1, "warmupCount": 0,
            "scenarios": [{"fixture": {"name": str(index), "family": "ledger"},
                           "status": "PASSED", "measurements": [{"phase": "reconcile",
                           "sampleIndex": 0, "status": "PASSED"}]} for index in range(3)],
        }

    def test_accepts_complete_report_and_rejects_partial_or_wrong_configuration(self):
        self.assertTrue(runner.completed_report(self.report, self.options))
        for key, value in (("status", "RUNNING"), ("schemaVersion", 2), ("profile", "large"),
                           ("scenarioSelection", "all"), ("sampleCount", 3), ("warmupCount", 1)):
            with self.subTest(key=key):
                changed = copy.deepcopy(self.report)
                changed[key] = value
                self.assertFalse(runner.completed_report(changed, self.options))
        self.report["scenarios"].pop()
        self.assertFalse(runner.completed_report(self.report, self.options))

    def test_rejects_missing_failed_duplicate_or_wrong_phase_samples(self):
        for change in ("missing", "failed", "duplicate", "wrong-phase", "wrong-index"):
            with self.subTest(change=change):
                changed = copy.deepcopy(self.report)
                row = changed["scenarios"][0]
                if change == "missing": row["measurements"] = []
                if change == "failed": row["measurements"][0]["status"] = "FAILED"
                if change == "duplicate": row["fixture"]["name"] = "1"
                if change == "wrong-phase": row["measurements"][0]["phase"] = "initialize"
                if change == "wrong-index": row["measurements"][0]["sampleIndex"] = 3
                self.assertFalse(runner.completed_report(changed, self.options))


    @unittest.skipIf(sys.platform == "win32", "POSIX fake Gradle executable")
    def test_preserves_failure_results_and_cannot_reuse_a_previous_successful_report(self):
        self.options.heap = "1g"
        for scenario in self.report["scenarios"]:
            scenario["measurements"][0].update({
                "elapsedNanos": 100, "runtime": {"sampledPeakHeapUsedBytes": 100},
                "transactions": {"maximumTransactionNanos": 90, "maximumObservedLockHoldNanos": 0},
            })
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            backend = root / "backend"
            backend.mkdir()
            wrapper = backend / "gradlew"
            wrapper.write_text(
                "#!/usr/bin/env python3\nimport json,sys\nfrom pathlib import Path\n"
                "out=Path(next(x for x in sys.argv if x.startswith('-PdomainBenchmark.outputDir=')).split('=',1)[1])\n"
                f"(out/'benchmark-report.json').write_text(json.dumps({self.report!r}))\n"
                "sys.exit(0)\n", encoding="utf-8")
            wrapper.chmod(0o755)
            with patch.object(runner, "PROJECT_ROOT", root), redirect_stdout(io.StringIO()):
                self.assertEqual(runner.run(self.options), 0)
                wrapper.write_text(wrapper.read_text().replace("sys.exit(0)", "sys.exit(1)"))
                self.assertEqual(runner.run(self.options), 1)
                wrapper.write_text("#!/usr/bin/env python3\n", encoding="utf-8")
                self.assertEqual(runner.run(self.options), 1)
            reports = [json.loads(p.read_text()) for p in (backend / "build/domain-benchmark").glob("*/result.json")]
            self.assertEqual(len(reports), 3)
            self.assertEqual(sorted(r["status"] for r in reports), ["FAILED", "FAILED", "PASSED"])
            self.assertEqual(sum(r.get("reportFailureType") == "FileNotFoundError" for r in reports), 1)


if __name__ == "__main__":
    unittest.main()

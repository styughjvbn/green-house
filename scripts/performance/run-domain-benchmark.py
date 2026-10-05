#!/usr/bin/env python3
"""Run isolated PostgreSQL measurements and produce one shareable result.json."""

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import platform
import re
import signal
import statistics
import subprocess
import sys
import time
import uuid


PROJECT_ROOT = Path(__file__).resolve().parents[2]


def bounded_integer(minimum, maximum):
    def parse(value):
        number = int(value)
        if not minimum <= number <= maximum:
            raise argparse.ArgumentTypeError(f"must be between {minimum} and {maximum}")
        return number
    return parse


def heap_size(value):
    if not re.fullmatch(r"[1-9][0-9]{0,3}[mg]", value):
        raise argparse.ArgumentTypeError("use a positive JVM heap size such as 1g or 2048m")
    return value


def git_value(*arguments):
    try:
        result = subprocess.run(
            ["git", "-C", str(PROJECT_ROOT), *arguments],
            capture_output=True, text=True, check=False,
        )
        return result.stdout.strip() if result.returncode == 0 else None
    except OSError:
        return None


def completed_report(report, options):
    """Do not turn a partial, stale, or wrong-configuration report into a success."""
    expected = {
        "smoke": {"settlement": 2, "ledger": 3},
        "standard": {"settlement": 5, "ledger": 5},
        "large": {"settlement": 2, "ledger": 4},
    }[options.profile]
    families = list(expected) if options.scenario == "all" else [options.scenario]
    if (report.get("schemaVersion") != 1 or report.get("status") != "PASSED"
            or report.get("profile") != options.profile
            or report.get("scenarioSelection") != options.scenario
            or report.get("sampleCount") != options.samples
            or report.get("warmupCount") != options.warmup):
        return False
    scenarios = report.get("scenarios", [])
    if not isinstance(scenarios, list) or len(scenarios) != sum(expected[f] for f in families):
        return False
    names = set()
    for family in families:
        selected = [s for s in scenarios if s.get("fixture", {}).get("family") == family]
        if len(selected) != expected[family]:
            return False
        phases = {"initialize", "replay"} if family == "settlement" else {"reconcile"}
        for scenario in selected:
            name = scenario.get("fixture", {}).get("name")
            if not name or name in names or scenario.get("status") != "PASSED":
                return False
            names.add(name)
            rows = scenario.get("measurements", [])
            if len(rows) != len(phases) * options.samples:
                return False
            for phase in phases:
                samples = [row for row in rows if row.get("phase") == phase]
                if (len(samples) != options.samples
                        or {row.get("sampleIndex") for row in samples} != set(range(options.samples))
                        or any(row.get("status") != "PASSED" for row in samples)):
                    return False
    return True


def summaries(report):
    results = []
    for scenario in report.get("scenarios", []):
        rows = scenario.get("measurements", [])
        for phase in sorted({row.get("phase") for row in rows if row.get("status") == "PASSED"}):
            samples = [row for row in rows if row.get("phase") == phase and row.get("status") == "PASSED"]
            elapsed = [row["elapsedNanos"] / 1_000_000 for row in samples]
            results.append({
                "scenario": scenario["fixture"]["name"], "phase": phase,
                "samples": len(samples), "medianMs": statistics.median(elapsed),
                "minMs": min(elapsed), "maxMs": max(elapsed),
                "maximumSampledHeapBytes": max(row["runtime"]["sampledPeakHeapUsedBytes"] for row in samples),
                "maximumObservedJdbcTransactionMs": max(row["transactions"]["maximumTransactionNanos"] for row in samples) / 1_000_000,
                "maximumObservedRowLockHoldMs": max(row["transactions"]["maximumObservedLockHoldNanos"] for row in samples) / 1_000_000,
            })
    return results


def stop_process(process):
    if sys.platform == "win32":
        process.send_signal(signal.CTRL_BREAK_EVENT)
    else:
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            return
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        if sys.platform == "win32":
            subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
        else:
            os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=5)


def run(options):
    backend = PROJECT_ROOT / "backend"
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
    directory = backend / "build/domain-benchmark" / run_id
    directory.mkdir(parents=True)
    wrapper = backend / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    command = [str(wrapper), "domainBenchmark", "--console=plain", "--no-parallel", "--no-daemon", "--stacktrace",
               f"-PdomainBenchmark.profile={options.profile}",
               f"-PdomainBenchmark.scenario={options.scenario}",
               f"-PdomainBenchmark.samples={options.samples}",
               f"-PdomainBenchmark.warmup={options.warmup}",
               f"-PdomainBenchmarkHeap={options.heap}",
               f"-PdomainBenchmark.outputDir={directory}"]
    dirty = git_value("status", "--porcelain")
    result = {"schemaVersion": 1, "status": "RUNNING", "runId": run_id,
              "revision": git_value("rev-parse", "HEAD"),
              "workingTreeDirty": None if dirty is None else bool(dirty),
              "pythonVersion": platform.python_version(),
              "configuration": vars(options)}
    result_path = directory / "result.json"
    result_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Results: {directory}", flush=True)
    print("Progress is written to console.log. Ctrl+C stops the run and keeps partial results.", flush=True)
    started = time.monotonic()
    exit_code = 1
    process = None
    try:
        with (directory / "console.log").open("w", encoding="utf-8") as log:
            process_options = ({"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP}
                               if sys.platform == "win32" else {"start_new_session": True})
            process = subprocess.Popen(command, cwd=backend, stdout=log, stderr=subprocess.STDOUT,
                                       **process_options)
            exit_code = process.wait()
    except KeyboardInterrupt:
        result["interrupted"] = True
        exit_code = 130
        if process is not None:
            stop_process(process)
    except OSError as failure:
        result["runnerFailureType"] = type(failure).__name__
    finally:
        result["runElapsedSeconds"] = round(time.monotonic() - started, 3)
        result["gradleExitCode"] = exit_code
        path = directory / "benchmark-report.json"
        try:
            report = json.loads(path.read_text(encoding="utf-8"))
            result["benchmark"] = report
            result["summary"] = summaries(report)
            valid = completed_report(report, options)
        except (OSError, ValueError, KeyError, TypeError, AttributeError) as failure:
            valid = False
            result["reportFailureType"] = type(failure).__name__
        result["status"] = "PASSED" if exit_code == 0 and valid else "FAILED"
        result_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{result['status']}: send {result_path}", flush=True)
        if result["status"] != "PASSED":
            print(f"Diagnostic log: {directory / 'console.log'}", flush=True)
    return 0 if result["status"] == "PASSED" else (exit_code or 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", choices=["smoke", "standard", "large"], default="standard")
    parser.add_argument("--scenario", choices=["all", "settlement", "ledger"], default="all")
    parser.add_argument("--samples", type=bounded_integer(1, 30), default=3)
    parser.add_argument("--warmup", type=bounded_integer(0, 20), default=1)
    parser.add_argument("--heap", type=heap_size, default="2g")
    return run(parser.parse_args())


if __name__ == "__main__":
    sys.exit(main())

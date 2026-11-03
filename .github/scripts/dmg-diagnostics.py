#!/usr/bin/env python3
"""Temporary main-based copy evidence collection; never release wiring."""

import collections
import datetime
import json
import os
from pathlib import Path
import resource
import signal
import subprocess
import sys
import tempfile
import threading
import time


ROOT = Path(os.environ["DMG_DIAGNOSTICS_DIR"])
ROOT.mkdir(parents=True, exist_ok=True)


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def output(command):
    try:
        completed = subprocess.run(command, capture_output=True, text=True, timeout=10)
        return {"command": command, "exit": completed.returncode, "output": completed.stdout + completed.stderr}
    except Exception as error:
        return {"command": command, "error": repr(error)}


def sample(target="."):
    return {
        "time": now(),
        "pid": os.getpid(),
        "file_limit": resource.getrlimit(resource.RLIMIT_NOFILE),
        "process_limit": resource.getrlimit(resource.RLIMIT_NPROC),
        "disk": output(["/bin/df", "-k", target, "/private/var/folders"]),
        "memory": output(["/usr/bin/vm_stat"]),
        "swap_and_files": output(["/usr/sbin/sysctl", "vm.swapusage", "kern.num_files", "kern.maxfiles"]),
    }


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n")


def snapshot():
    write_json(ROOT / ("environment-" + str(time.time_ns()) + ".json"), {
        "revision": output(["git", "rev-parse", "HEAD"]),
        "os": output(["/usr/bin/sw_vers"]),
        "architecture": output(["/usr/bin/uname", "-m"]),
        "rsync": output(["/usr/bin/rsync", "--version"]),
        "image": {key: os.environ.get(key) for key in ("ImageOS", "ImageVersion", "RUNNER_OS", "RUNNER_ARCH")},
        "launch_limits": output(["/bin/launchctl", "limit", "maxfiles"]),
        "mounted_images": output(["/usr/bin/hdiutil", "info", "-plist"]),
        "resources": sample(),
    })


def copy():
    directory = Path(tempfile.mkdtemp(prefix=str(time.time_ns()) + "-", dir=ROOT))
    original = sys.argv[2:]
    arguments = [argument for argument in original if argument not in ("--quiet", "-q")]
    command = ["/usr/bin/rsync", *arguments]
    write_json(directory / "before.json", {"original": original, "command": command, "resources": sample(arguments[-1])})
    started = time.monotonic()
    with (directory / "copy.log").open("w") as log:
        completed = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
    status = completed.returncode
    write_json(directory / "after.json", {
        "exit": status,
        "elapsed_seconds": time.monotonic() - started,
        "resources": sample(arguments[-1]),
        "child_usage": list(resource.getrusage(resource.RUSAGE_CHILDREN)),
    })
    print(f"[DMG-COPY] exit={status} evidence={directory}", file=sys.stderr, flush=True)
    if status:
        with (directory / "copy.log").open(errors="replace") as log:
            print("".join(collections.deque(log, maxlen=100)), file=sys.stderr, flush=True)
        with (directory / "storage.log").open("w") as log:
            try:
                subprocess.run([
                    "/usr/bin/log", "show", "--last", "3m", "--style", "compact",
                    "--predicate", 'process == "rsync" OR subsystem == "com.apple.apfs"',
                ], stdout=log, stderr=subprocess.STDOUT, timeout=30)
            except subprocess.TimeoutExpired:
                log.write("\nStorage log collection timed out.\n")
        reports = Path.home() / "Library/Logs/DiagnosticReports"
        if reports.is_dir():
            for report in reports.iterdir():
                if report.is_file() and report.name.startswith("rsync"):
                    try:
                        (directory / report.name).write_bytes(report.read_bytes())
                    except OSError as error:
                        print(f"Cannot preserve {report}: {error}", file=sys.stderr)
    return status if status >= 0 else 128 - status


def run():
    snapshot()
    write_json(ROOT / "test-command.json", {"command": sys.argv[2:], "started": now()})
    stopped = threading.Event()

    def monitor():
        with (ROOT / "resources.jsonl").open("a", buffering=1) as log:
            while not stopped.is_set():
                log.write(json.dumps(sample()) + "\n")
                stopped.wait(15)

    thread = threading.Thread(target=monitor, daemon=True)
    thread.start()
    process = subprocess.Popen(sys.argv[2:])

    def forward(signum, frame):
        write_json(ROOT / "interruption.json", {"signal": signum, "time": now()})
        process.send_signal(signum)

    signal.signal(signal.SIGTERM, forward)
    signal.signal(signal.SIGINT, forward)
    try:
        status = process.wait()
        write_json(ROOT / "test-result.json", {"exit": status, "finished": now()})
        return status if status >= 0 else 128 - status
    finally:
        stopped.set()
        thread.join(timeout=35)
        snapshot()


if __name__ == "__main__":
    if sys.argv[1] == "copy":
        sys.exit(copy())
    elif sys.argv[1] == "run":
        sys.exit(run())
    elif sys.argv[1] == "snapshot":
        snapshot()
    else:
        raise SystemExit("Expected copy, run, or snapshot")
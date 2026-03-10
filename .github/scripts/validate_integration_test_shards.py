#!/usr/bin/env python3

from __future__ import annotations

from collections import Counter
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_PATH = ROOT / ".github/workflows/reusable-integrationTests.yml"
INTEGRATION_TESTS_PATH = ROOT / "src/integrationTest/kotlin/org/jetbrains/intellij/platform/gradle"


def collect_integration_test_classes() -> set[str]:
    classes: set[str] = set()
    for file in sorted(INTEGRATION_TESTS_PATH.glob("*.kt")):
        content = file.read_text(encoding="utf-8")
        if re.search(r"^\s*@Test\b", content, re.MULTILINE):
            classes.add(file.stem)
    return classes


def collect_shard_patterns() -> list[str]:
    content = WORKFLOW_PATH.read_text(encoding="utf-8")
    return re.findall(r"--tests '\*([^']+)'", content)


def main() -> int:
    integration_tests = collect_integration_test_classes()
    shard_patterns = collect_shard_patterns()

    pattern_counts = Counter(shard_patterns)
    duplicates = sorted(name for name, count in pattern_counts.items() if count > 1)

    shard_set = set(shard_patterns)
    missing = sorted(integration_tests - shard_set)
    extra = sorted(shard_set - integration_tests)

    if missing or extra or duplicates:
        print("Integration test shard validation failed.")
        if missing:
            print(f"Missing shard entries ({len(missing)}):")
            for name in missing:
                print(f"  - {name}")
        if extra:
            print(f"Stale shard entries ({len(extra)}):")
            for name in extra:
                print(f"  - {name}")
        if duplicates:
            print(f"Duplicated shard entries ({len(duplicates)}):")
            for name in duplicates:
                print(f"  - {name} (count={pattern_counts[name]})")
        return 1

    print(
        "Integration test shard validation passed: "
        f"{len(integration_tests)} classes mapped to {len(shard_patterns)} patterns."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

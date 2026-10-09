"""Prints per-class test counts from build/test-results/test/*.xml.

Run from the project root after `gw test`: python3 -I tools/test_summary.py
Exits non-zero if any test failed or errored, or if no results exist.
"""
import glob
import re
import sys

total = 0
bad = 0
files = sorted(glob.glob("build/test-results/test/TEST-*.xml"))
if not files:
    print("no test results found")
    sys.exit(1)
for path in files:
    with open(path, encoding="utf-8") as handle:
        match = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', handle.read())
    tests, skipped, failures, errors = (int(x) for x in match.groups())
    name = path.rsplit("TEST-", 1)[1].removesuffix(".xml").rsplit(".", 1)[1]
    print(f"{name}: {tests} tests, {failures + errors} failed, {skipped} skipped")
    total += tests
    bad += failures + errors
print(f"total: {total} tests, {bad} failed")
sys.exit(1 if bad else 0)

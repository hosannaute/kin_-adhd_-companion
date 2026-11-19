"""Expose JUnit totals and failures as check annotations without changing test outcomes."""
import sys
from pathlib import Path
from xml.etree import ElementTree


def escape(value):
    return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


reports = tests = failed = skipped = 0
for root in sys.argv[1:]:
    for report in Path(root).rglob("TEST-*.xml"):
        reports += 1
        for case in ElementTree.parse(report).getroot().iter("testcase"):
            tests += 1
            failures = list(case.findall("failure")) + list(case.findall("error"))
            failed += bool(failures)
            skipped += case.find("skipped") is not None
            for failure in failures:
                title = escape(f"{case.get('classname', '')}.{case.get('name', '')}").replace(",", "%2C").replace(":", "%3A")
                message = escape((failure.get("message", "") + "\n" + (failure.text or ""))[:4000])
                print(f"::error title={title}::{message}")
if reports:
    print(f"::notice title=Test results::{tests} tests: {tests - failed - skipped} passed, "
          f"{failed} failed, {skipped} skipped ({reports} JUnit reports).")
else:
    print("::warning title=Test results::No JUnit reports found; no passing result is inferred.")

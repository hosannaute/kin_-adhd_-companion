"""Expose JUnit failures as PR check annotations without changing test outcomes."""
import sys
from pathlib import Path
from xml.etree import ElementTree


def escape(value):
    return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


for root in sys.argv[1:]:
    for report in Path(root).rglob("TEST-*.xml"):
        for case in ElementTree.parse(report).getroot().iter("testcase"):
            for failure in list(case.findall("failure")) + list(case.findall("error")):
                title = escape(f"{case.get('classname', '')}.{case.get('name', '')}").replace(",", "%2C").replace(":", "%3A")
                message = escape((failure.get("message", "") + "\n" + (failure.text or ""))[:4000])
                print(f"::error title={title}::{message}")

#!/usr/bin/env python3
"""Expose Android build diagnostics via Check annotations without external log hosts."""
import glob
import os
import re
import xml.etree.ElementTree as ET


def escape(text):
    return str(text).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def annotate(level, title, message):
    print(f"::{level} title={escape(title)}::{escape(message)}")


def main():
    for path in glob.glob("/tmp/android-*.log"):
        seen = set()
        with open(path) as log:
            for line in log:
                if re.search(r"^e: |error:|^> .*failed|Execution failed for task|What went wrong:", line):
                    level = "error"
                elif line.startswith("w: "):
                    level = "warning"
                else:
                    continue
                text = line.strip()
                if text not in seen:
                    annotate(level, "Android build", text[:2000])
                    seen.add(text)

    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    suites = []
    for path in glob.glob("android/app/build/test-results/testDebugUnitTest/*.xml"):
        suite = ET.parse(path).getroot()
        for name in counts:
            counts[name] += int(suite.get(name, 0))
        suites.append(f"{suite.get('name')}: {suite.get('tests')} tests, {suite.get('failures')} failures")
        for case in suite.findall("testcase"):
            for failure in list(case.findall("failure")) + list(case.findall("error")):
                annotate("error", case.get("name", "Unit test"), (failure.text or "")[:2000])
    if suites:
        summary = ", ".join(f"{value} {name}" for name, value in counts.items())
        annotate("notice", "Android unit tests", summary)
        summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary_path:
            with open(summary_path, "a") as output:
                output.write("\n### Unit tests\n" + summary + "\n\n" + "\n".join(f"- {s}" for s in suites) + "\n")

    for path in glob.glob("android/app/build/reports/lint-results-debug.xml"):
        root = ET.parse(path).getroot()
        issues = root.findall("issue")
        # Actions only retains ten warnings per step. Group each severity so
        # every issue remains visible through the Checks API.
        groups = {"error": [], "warning": []}
        for issue in issues:
            location = issue.find("location")
            where = "" if location is None else f"{location.get('file')}:{location.get('line', '1')}: "
            level = "error" if issue.get("severity") in ("Error", "Fatal") else "warning"
            groups[level].append(f"{issue.get('id')}: {where}{issue.get('message', '')}")
        for level, messages in groups.items():
            if messages:
                annotate(level, "Android lint diagnostics", "\n".join(messages))
        annotate("notice", "Android lint", f"{len(issues)} issue(s) in lint-results-debug.xml")


if __name__ == "__main__":
    main()

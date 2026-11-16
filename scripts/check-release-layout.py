#!/usr/bin/env python3
"""Exercise the release workflow's actual shell steps against both source layouts."""

import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest


WORKFLOW = Path(__file__).resolve().parents[1] / ".github/workflows/release.yml"
VERSION = "1.2.3"
JAR = f"github-analyzer-{VERSION}.jar"


def workflow_command(step_name):
    """Read one literal run block; fail if the named workflow step changes shape."""
    lines = WORKFLOW.read_text(encoding="utf-8").splitlines()
    marker = f"      - name: {step_name}"
    start = lines.index(marker) + 1
    while start < len(lines) and not lines[start].startswith("      - name:"):
        if lines[start] == "        run: |":
            end = start + 1
            while end < len(lines) and (not lines[end].strip() or lines[end].startswith("          ")):
                end += 1
            return textwrap.dedent("\n".join(lines[start + 1:end]))
        start += 1
    raise AssertionError(f"Missing literal run block for {step_name}")


def write_script(path, body):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("#!/bin/sh\nset -eu\n" + body + "\n", encoding="utf-8")
    path.chmod(0o755)


class ReleaseLayoutTest(unittest.TestCase):
    def fixture(self, root, modern, fail_tests=False, produce_jar=True):
        scripts = root / "scripts" if modern else root
        output = f"dist/{JAR}" if modern else JAR
        write_script(scripts / "run-tests.sh", "exit 7" if fail_tests else "printf 'tests\\n' >> calls")
        build = "printf 'build\\n' >> calls\n"
        if produce_jar:
            build += f"mkdir -p dist\nprintf 'current jar' > '{output}'"
        write_script(scripts / "build-jar.sh", build)

        # An old artifact in the other location must never be selected.
        other = root / JAR if modern else root / "dist" / JAR
        other.parent.mkdir(parents=True, exist_ok=True)
        other.write_bytes(b"stale jar")
        if modern:
            # Prefer scripts/ even if legacy entry points happen to remain.
            write_script(root / "run-tests.sh", "exit 9")
            write_script(root / "build-jar.sh", "exit 9")

    def run_step(self, root, name):
        return subprocess.run(
            ["bash", "-c", workflow_command(name)], cwd=root,
            env={**os.environ, "VERSION": VERSION}, capture_output=True, text=True,
        )

    def check_layout(self, modern):
        with tempfile.TemporaryDirectory(prefix="release-layout-") as directory:
            root = Path(directory)
            self.fixture(root, modern)
            for name in ("Run automated tests", "Build the runnable JAR", "Generate checksum"):
                result = self.run_step(root, name)
                self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(["tests", "build"], (root / "calls").read_text().splitlines())
            artifact = root / "dist" / JAR
            self.assertEqual(b"current jar", artifact.read_bytes())
            checksum = (root / "dist" / (JAR + ".sha256")).read_text().split()
            self.assertEqual([hashlib.sha256(artifact.read_bytes()).hexdigest(), JAR], checksum)

    def test_legacy_root_layout(self):
        self.check_layout(modern=False)

    def test_scripts_and_dist_layout(self):
        self.check_layout(modern=True)

    def test_test_failure_is_propagated(self):
        for modern in (False, True):
            with self.subTest(modern=modern), tempfile.TemporaryDirectory(prefix="release-failure-") as directory:
                root = Path(directory)
                self.fixture(root, modern, fail_tests=True)
                self.assertEqual(7, self.run_step(root, "Run automated tests").returncode)
                self.assertFalse((root / "calls").exists())

    def test_missing_expected_jar_rejects_stale_artifact(self):
        for modern in (False, True):
            with self.subTest(modern=modern), tempfile.TemporaryDirectory(prefix="release-missing-") as directory:
                root = Path(directory)
                self.fixture(root, modern, produce_jar=False)
                self.assertEqual(0, self.run_step(root, "Build the runnable JAR").returncode)
                result = self.run_step(root, "Generate checksum")
                self.assertNotEqual(0, result.returncode)
                self.assertFalse((root / "dist" / (JAR + ".sha256")).exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)

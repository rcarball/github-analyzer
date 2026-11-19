#!/usr/bin/env python3
"""Offline regression tests for the actual dependency audit commands."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError, URLError
import zipfile


SCRIPT = Path(__file__).with_name("dependencies.py")
SPEC = importlib.util.spec_from_file_location("dependency_audit", SCRIPT)
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


class InventoryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="dependency-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        shutil.copyfile(audit.ROOT / "CITATION.cff", self.root / "CITATION.cff")
        shutil.copytree(audit.ROOT / "lib", self.root / "lib")
        (self.root / "scripts").mkdir()
        shutil.copyfile(SCRIPT, self.root / "scripts/dependencies.py")
        self.lock = json.loads((self.root / "lib/dependencies.lock.json").read_text())
        (self.root / ".classpath").write_text('<classpath>' + ''.join(
            f'<classpathentry kind="lib" path="{a["file"]}"/>' for a in self.lock["artifacts"]) + '</classpath>')

    def save(self):
        (self.root / "lib/dependencies.lock.json").write_text(audit.json_text(self.lock))

    def test_real_inventory_includes_nested_libraries_and_test_scope(self):
        lock = audit.inventory(self.root)
        bom = audit.sbom(lock, self.root)
        self.assertEqual((self.root / "lib/sbom.cdx.json").read_text(), audit.json_text(bom))
        by_name = {c["name"]: c for c in bom["components"]}
        self.assertEqual(by_name["fastdoubleparser"]["scope"], "required")
        for name in ("junit", "hamcrest-core", "picocli", "fastcsv", "open-test-reporting-events"):
            self.assertEqual(by_name[name]["scope"], "excluded")
            self.assertNotIn("hashes", by_name[name], "Relocated bytes must not claim an upstream JAR hash")
        self.assertEqual(len(lock["artifacts"]), 7)
        self.assertEqual(len(bom["components"]), 27)
        refs = {"github-analyzer", *(c["bom-ref"] for c in bom["components"])}
        self.assertEqual(len(refs), len(bom["components"]) + 1)
        for dependency in bom["dependencies"]:
            self.assertIn(dependency["ref"], refs)
            self.assertTrue(set(dependency.get("dependsOn", [])) <= refs)

    def test_modified_jar_is_rejected(self):
        path = self.root / self.lock["artifacts"][0]["file"]
        path.write_bytes(path.read_bytes() + b"tampered")
        with self.assertRaisesRegex(audit.AuditError, "modified"):
            audit.inventory(self.root)

    def test_missing_jar_is_rejected(self):
        (self.root / self.lock["artifacts"][0]["file"]).unlink()
        with self.assertRaisesRegex(audit.AuditError, "Missing"):
            audit.inventory(self.root)

    def test_extra_jar_is_not_silently_omitted_from_scan(self):
        (self.root / "lib/unreviewed.jar").write_bytes(b"extra")
        with self.assertRaisesRegex(audit.AuditError, "Uninventoried"):
            audit.inventory(self.root)

    def test_duplicate_component_is_rejected(self):
        original = self.lock["artifacts"][0]
        original["embedded"].append({key: original[key] for key in ("group", "name", "version", "license")})
        self.save()
        with self.assertRaisesRegex(audit.AuditError, "Duplicate component"):
            audit.inventory(self.root)

    def test_changed_maven_identity_is_rejected_even_with_updated_checksum(self):
        artifact = self.lock["artifacts"][0]
        jar_path = self.root / artifact["file"]
        with zipfile.ZipFile(jar_path) as jar:
            entries = {n: jar.read(n) for n in jar.namelist()}
        name = f"META-INF/maven/{artifact['group']}/{artifact['name']}/pom.properties"
        entries[name] = entries[name].replace(artifact["version"].encode(), b"0.0.0")
        with zipfile.ZipFile(jar_path, "w") as jar:
            for name, content in entries.items():
                jar.writestr(name, content)
        artifact["sha256"] = audit.sha256(jar_path)
        self.save()
        with self.assertRaisesRegex(audit.AuditError, "metadata mismatch"):
            audit.inventory(self.root)

    def test_forged_group_cannot_fall_back_to_manifest(self):
        self.lock["artifacts"][0]["group"] = "wrong.group"
        self.save()
        with self.assertRaisesRegex(audit.AuditError, "Missing Maven identity"):
            audit.inventory(self.root)

    def test_removed_embedded_inventory_entry_is_rejected(self):
        standalone = self.lock["artifacts"][-1]
        standalone["embedded"] = standalone["embedded"][1:]
        self.save()
        with self.assertRaisesRegex(audit.AuditError, "Embedded inventory mismatch"):
            audit.inventory(self.root)

    def test_required_pom_dependency_cannot_be_dropped(self):
        self.lock["artifacts"][2]["requires"] = []
        self.save()
        with self.assertRaisesRegex(audit.AuditError, "Missing non-optional POM"):
            audit.inventory(self.root)

    def test_embedded_parser_version_must_match_its_pom(self):
        self.lock["artifacts"][4]["embedded"][0]["version"] = "999.0"
        self.save()
        with self.assertRaisesRegex(audit.AuditError, "Embedded POM version mismatch"):
            audit.inventory(self.root)

    def test_dangling_dependency_is_rejected(self):
        self.lock["artifacts"][0]["requires"] = ["pkg:maven/missing/library@1"]
        self.save()
        with self.assertRaisesRegex(audit.AuditError, "Unresolved"):
            audit.inventory(self.root)

    def test_eclipse_cannot_reference_a_different_set(self):
        file = self.root / ".classpath"
        file.write_text(file.read_text().replace('kind="lib"', 'kind="ignored"', 1))
        with self.assertRaisesRegex(audit.AuditError, "Eclipse"):
            audit.inventory(self.root)

    def test_clean_checkout_does_not_require_local_eclipse_files(self):
        (self.root / ".classpath").unlink()
        audit.inventory(self.root)

    def test_cli_detects_stale_sbom_and_regenerates_reproducibly(self):
        script = str(self.root / "scripts/dependencies.py")
        def run(*args):
            return subprocess.run([sys.executable, script, *args], capture_output=True, text=True)
        original = (self.root / "lib/sbom.cdx.json").read_text(encoding="utf-8").encode("utf-8")
        self.assertEqual(run("generate", "--check").returncode, 0)
        (self.root / "lib/sbom.cdx.json").write_text("{}\n")
        self.assertEqual(run("verify").returncode, 2)
        self.assertEqual(run("generate").returncode, 0)
        self.assertEqual((self.root / "lib/sbom.cdx.json").read_bytes(), original)
        citation = self.root / "CITATION.cff"
        version = json.loads(original)["metadata"]["component"]["version"]
        series, patch = version.rsplit(".", 1)
        next_version = f"{series}.{int(patch) + 1}"
        citation.write_text(citation.read_text().replace(f'version: "{version}"', f'version: "{next_version}"'))
        self.assertEqual(run("verify").returncode, 2)

    def test_upstream_mismatch_fails_without_modifying_local_artifacts(self):
        with patch.object(audit, "urlopen", return_value=io.BytesIO(b"different upstream bytes")):
            with self.assertRaisesRegex(audit.AuditError, "Central checksum mismatch"):
                audit.verify_upstream(self.lock)
        audit.inventory(self.root)


class VulnerabilityTest(unittest.TestCase):
    def setUp(self):
        self.bom = audit.sbom(audit.inventory())
        self.component = self.bom["components"][0]

    def test_clean_scan_checks_every_runtime_test_and_embedded_component(self):
        seen = []
        def request(url, payload):
            self.assertEqual(url, audit.OSV)
            seen.append(payload["package"]["purl"])
            self.assertNotIn("version", payload, "Do not send two versions to OSV")
            return {}
        report = audit.scan(self.bom, request)
        self.assertEqual(set(seen), {c["purl"] for c in self.bom["components"]})
        self.assertEqual(audit.scan_exit_code(report), 0)

    def test_live_finding_fails_and_withdrawn_record_does_not(self):
        report = audit.scan({"components": [self.component]}, lambda *_: {"vulns": [
            {"id": "GHSA-example", "summary": "Untriaged finding"},
            {"id": "WITHDRAWN", "withdrawn": "2026-01-01T00:00:00Z"}]})
        self.assertEqual(audit.scan_exit_code(report), 1)
        self.assertEqual([f["vulnerability"]["id"] for f in report["findings"]], ["GHSA-example"])

    def test_pagination_collects_and_deduplicates_all_findings(self):
        calls = []
        def request(url, payload):
            calls.append(payload)
            return {"vulns": [{"id": "SECOND"}, {"id": "FIRST"}]} if "page_token" in payload else {
                "vulns": [{"id": "FIRST"}], "next_page_token": "page-2"}
        vulns = audit.query_component(self.component, request)
        self.assertEqual({v["id"] for v in vulns}, {"FIRST", "SECOND"})
        self.assertEqual(calls[1]["page_token"], "page-2")

    def test_repeated_pagination_token_is_an_error(self):
        with self.assertRaisesRegex(audit.AuditError, "repeated"):
            audit.query_component(self.component, lambda *_: {"next_page_token": "same"})

    def test_invalid_results_are_never_clean(self):
        for response in ([], {"error": "service failure"}, {"vulns": None}, {"vulns": [{}]}, {"vulns": ["bad"]}):
            with self.subTest(response=response):
                report = audit.scan(self.bom, lambda *_: response)
                self.assertEqual(audit.scan_exit_code(report), 2)
                self.assertEqual(report["status"], "incomplete")

    def test_outage_stops_scan_and_report_preserves_partial_findings(self):
        calls = []
        def request(*_):
            calls.append(1)
            if len(calls) == 1:
                return {"vulns": [{"id": "EXISTING"}]}
            raise audit.AuditError("Network unavailable")
        report = audit.scan(self.bom, request)
        self.assertEqual(len(calls), 2)
        self.assertEqual(audit.scan_exit_code(report), 2)
        self.assertEqual(report["findings"][0]["vulnerability"]["id"], "EXISTING")

    def test_http_retry_is_bounded_and_eventual_success_is_read(self):
        unavailable = HTTPError(audit.OSV, 503, "Unavailable", None, None)
        with patch.object(audit, "urlopen", side_effect=[unavailable, io.BytesIO(b"{}")]) as network, patch.object(audit.time, "sleep"):
            self.assertEqual(audit.request_json(audit.OSV, {}), {})
            self.assertEqual(network.call_count, 2)
        with patch.object(audit, "urlopen", side_effect=URLError("offline")) as network, patch.object(audit.time, "sleep"):
            with self.assertRaisesRegex(audit.AuditError, "unavailable"):
                audit.request_json(audit.OSV, {})
            self.assertEqual(network.call_count, 3)

    def test_bad_request_and_malformed_json_are_not_retried(self):
        with patch.object(audit, "urlopen", side_effect=HTTPError(audit.OSV, 400, "Bad request", None, None)) as network:
            with self.assertRaisesRegex(audit.AuditError, "400"):
                audit.request_json(audit.OSV, {})
            self.assertEqual(network.call_count, 1)
        with patch.object(audit, "urlopen", return_value=io.BytesIO(b"not json")):
            with self.assertRaisesRegex(audit.AuditError, "Malformed"):
                audit.request_json(audit.OSV, {})

    def test_cli_writes_findings_and_incomplete_reports_with_failure_status(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "report.json"
            for report, expected in (({"status": "complete", "checked": [], "findings": [
                    {"purl": self.component["purl"], "vulnerability": {"id": "FINDING"}}], "errors": []}, 1),
                    ({"status": "incomplete", "checked": [], "findings": [], "errors": [
                        {"purl": self.component["purl"], "error": "offline"}]}, 2)):
                with patch.object(audit, "scan", return_value=report), contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(audit.main(["scan", "--output", str(output)]), expected)
                self.assertEqual(json.loads(output.read_text()), report)


if __name__ == "__main__":
    unittest.main(verbosity=2)

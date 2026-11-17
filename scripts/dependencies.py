#!/usr/bin/env python3
"""Verify vendored JARs, generate CycloneDX, and query OSV. Python 3.9+, stdlib only."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import time
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[1]
CENTRAL = "https://repo.maven.apache.org/maven2"
OSV = "https://api.osv.dev/v1/query"


class AuditError(Exception):
    """An incomplete or invalid audit must never be reported as clean."""


def purl(component):
    return "pkg:maven/{}/{}@{}".format(*(quote(component[key], safe=".") for key in ("group", "name", "version")))


def artifact_url(component):
    group, name, version = (component[key] for key in ("group", "name", "version"))
    return f"{CENTRAL}/{group.replace('.', '/')}/{name}/{version}/{name}-{version}.jar"


def json_text(value):
    return json.dumps(value, indent=2, ensure_ascii=False) + "\n"


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inventory(root=ROOT):
    """Verify the exact files, metadata, embedded inventory and Eclipse classpath."""
    lock = json.loads((root / "lib/dependencies.lock.json").read_text(encoding="utf-8"))
    if lock.get("formatVersion") != 1 or not lock.get("artifacts"):
        raise AuditError("Unsupported or empty dependency inventory")
    files, refs = set(), set()
    for artifact in lock["artifacts"]:
        if artifact.get("scope") not in ("runtime", "test"):
            raise AuditError("Every artifact must have runtime or test scope")
        expected = f"lib/{artifact['name']}-{artifact['version']}.jar"
        if artifact["file"] != expected or not re.fullmatch(r"lib/[A-Za-z0-9_.-]+\.jar", expected):
            raise AuditError("Invalid JAR path in inventory")
        if expected in files:
            raise AuditError(f"Duplicate artifact: {expected}")
        files.add(expected)
        for component in [artifact, *artifact["embedded"]]:
            for field in ("group", "name", "version"):
                if not re.fullmatch(r"[A-Za-z0-9_.-]+", component[field]):
                    raise AuditError(f"Invalid Maven coordinate: {component[field]}")
            if not component.get("license"):
                raise AuditError("Every component must declare a reviewed license")
            ref = purl(component)
            if ref in refs:
                raise AuditError(f"Duplicate component: {ref}")
            refs.add(ref)
        path = root / expected
        if not path.is_file() or sha256(path) != artifact["sha256"]:
            raise AuditError(f"Missing or modified artifact: {expected}")
        with zipfile.ZipFile(path) as jar:
            properties = f"META-INF/maven/{artifact['group']}/{artifact['name']}/pom.properties"
            if properties in jar.namelist():
                values = dict(line.split("=", 1) for line in jar.read(properties).decode().splitlines()
                              if "=" in line and not line.startswith("#"))
                if any(values.get(key) != artifact[field] for key, field in
                       (("groupId", "group"), ("artifactId", "name"), ("version", "version"))):
                    raise AuditError(f"Maven metadata mismatch: {expected}")
            elif artifact["group"] == "org.junit.platform" and artifact["name"] == "junit-platform-console-standalone":
                manifest = jar.read("META-INF/MANIFEST.MF").decode().replace("\r\n ", "")
                if (f"Implementation-Version: {artifact['version']}\r\n" not in manifest
                        or f"Implementation-Title: {artifact['name']}\r\n" not in manifest):
                    raise AuditError(f"Manifest version mismatch: {expected}")
            else:
                raise AuditError(f"Missing Maven identity metadata: {expected}")
            declared = {"{}:{}:{}".format(c["group"], c["name"], c["version"])
                        for c in artifact["embedded"] if c["evidence"] == "META-INF/shadowed-artifacts"}
            if "META-INF/shadowed-artifacts" in jar.namelist():
                if set(jar.read("META-INF/shadowed-artifacts").decode().splitlines()) != declared:
                    raise AuditError(f"Embedded inventory mismatch: {expected}")
            elif declared:
                raise AuditError(f"Missing embedded inventory: {expected}")
            for component in artifact["embedded"]:
                if "marker" in component and component["marker"] not in jar.namelist():
                    raise AuditError(f"Missing embedded component: {purl(component)}")
                if not component.get("source") or not component.get("evidence"):
                    raise AuditError("Embedded components need provenance evidence")
                if component["evidence"].startswith("META-INF/maven/"):
                    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
                    pom = ET.fromstring(jar.read(component["evidence"]))
                    matches = [dep for dep in pom.findall("m:dependencies/m:dependency", ns)
                               if dep.findtext("m:groupId", namespaces=ns) == component["group"]
                               and dep.findtext("m:artifactId", namespaces=ns) == component["name"]]
                    if len(matches) != 1 or matches[0].findtext("m:version", namespaces=ns) != component["version"]:
                        raise AuditError(f"Embedded POM version mismatch: {purl(component)}")
    actual = {path.relative_to(root).as_posix() for path in (root / "lib").glob("*.jar")}
    if files != actual:
        raise AuditError(f"Uninventoried or missing JARs: {sorted(files ^ actual)}")
    # Eclipse metadata is local and ignored by Git. A clean checkout need not
    # have it; if present, detect stale local library references as well.
    if (root / ".classpath").is_file():
        classpath = {entry.attrib["path"] for entry in ET.parse(root / ".classpath").getroot()
                     if entry.get("kind") == "lib"}
        if classpath != files:
            raise AuditError("Eclipse libraries differ from the dependency inventory")
    for artifact in lock["artifacts"]:
        if not set(artifact["requires"]) <= refs:
            raise AuditError(f"Unresolved runtime dependency: {artifact['name']}")
        # Check non-optional runtime requirements, but allow the reviewed newer
        # versions selected by this application instead of upstream defaults.
        with zipfile.ZipFile(root / artifact["file"]) as jar:
            pom = f"META-INF/maven/{artifact['group']}/{artifact['name']}/pom.xml"
            if pom not in jar.namelist():
                continue
            ns = {"m": "http://maven.apache.org/POM/4.0.0"}
            required = set()
            for dep in ET.fromstring(jar.read(pom)).findall("m:dependencies/m:dependency", ns):
                if (dep.findtext("m:scope", "compile", ns) in ("compile", "runtime")
                        and dep.findtext("m:optional", "false", ns) != "true"):
                    required.add((dep.findtext("m:groupId", namespaces=ns), dep.findtext("m:artifactId", namespaces=ns)))
            available = {(c["group"], c["name"]) for c in artifact["embedded"]}
            available.update((c["group"], c["name"]) for c in lock["artifacts"] if purl(c) in artifact["requires"])
            if not required <= available:
                raise AuditError(f"Missing non-optional POM dependencies for {artifact['name']}: {sorted(required - available)}")
    return lock


def sbom(lock, root=ROOT):
    """Deterministic source-distribution BOM; test components have excluded scope.

    Embedded components carry their upstream identity but not a misleading hash of
    an unmodified upstream JAR. The container's verified hash covers their bytes.
    """
    match = re.search(r'^version: "([0-9.]+)"$', (root / "CITATION.cff").read_text(), re.M)
    if not match:
        raise AuditError("Missing release version in CITATION.cff")
    app_ref = "github-analyzer"
    components, dependencies = [], []
    for artifact in lock["artifacts"]:
        for item in [artifact, *artifact["embedded"]]:
            ref = purl(item)
            component = {
                "type": "library", "bom-ref": ref, "group": item["group"], "name": item["name"],
                "version": item["version"], "purl": ref,
                "scope": "required" if artifact["scope"] == "runtime" else "excluded",
                "licenses": [{"license": {"id": item["license"]}}],
                "externalReferences": [{"type": "distribution", "url": artifact_url(item)}],
                "properties": [{"name": "github-analyzer:usage", "value": artifact["scope"]}],
            }
            if item is artifact:
                component["hashes"] = [{"alg": "SHA-256", "content": artifact["sha256"]}]
                component["properties"].append({"name": "github-analyzer:file", "value": artifact["file"]})
                dependencies.append({"ref": ref, "dependsOn": sorted([
                    *artifact["requires"], *(purl(c) for c in artifact["embedded"])])})
            else:
                component["properties"] += [
                    {"name": "github-analyzer:embedded-in", "value": purl(artifact)},
                    {"name": "github-analyzer:evidence", "value": item["evidence"]},
                ]
                component["externalReferences"].append({"type": "evidence", "url": item["source"]})
                # No empty dependsOn: the complete upstream dependency graph of
                # relocated libraries is not claimed by this physical inventory.
            components.append(component)
    dependencies.append({"ref": app_ref, "dependsOn": sorted(purl(c) for c in lock["artifacts"])})
    return {
        "$schema": "http://cyclonedx.org/schema/bom-1.6.schema.json", "bomFormat": "CycloneDX",
        "specVersion": "1.6", "version": 1,
        "metadata": {"component": {"type": "application", "bom-ref": app_ref,
                                    "name": "GitHub Analyzer", "version": match[1],
                                    "licenses": [{"license": {"id": "Apache-2.0"}}]}},
        "components": sorted(components, key=lambda c: c["bom-ref"]),
        "dependencies": sorted(dependencies, key=lambda d: d["ref"]),
    }


def request_json(url, payload):
    """Bounded retries for transient errors; never convert an outage to no findings."""
    request = Request(url, data=json.dumps(payload).encode(), headers={
        "Content-Type": "application/json", "User-Agent": "github-analyzer-dependency-audit/1"})
    for attempt in range(3):
        try:
            with urlopen(request, timeout=30) as response:
                data = response.read(10 * 1024 * 1024 + 1)
            if len(data) > 10 * 1024 * 1024:
                raise AuditError("OSV response too large")
            value = json.loads(data)
            if not isinstance(value, dict):
                raise AuditError("OSV response must be an object")
            return value
        except HTTPError as ex:
            if ex.code not in (429, 500, 502, 503, 504) or attempt == 2:
                raise AuditError(f"OSV HTTP error: {ex.code}") from ex
        except (URLError, TimeoutError, OSError) as ex:
            if attempt == 2:
                raise AuditError(f"OSV unavailable: {ex}") from ex
        except (ValueError, UnicodeError) as ex:
            raise AuditError("Malformed OSV JSON") from ex
        time.sleep(attempt + 1)
    raise AuditError("OSV request did not complete")


def query_component(component, request=request_json):
    findings, tokens, token = {}, set(), None
    for _ in range(100):
        payload = {"package": {"purl": component["purl"]}}
        if token:
            payload["page_token"] = token
        page = request(OSV, payload)
        if not isinstance(page, dict) or set(page) - {"vulns", "next_page_token"}:
            raise AuditError("OSV returned an invalid result")
        vulns = page.get("vulns", [])
        if not isinstance(vulns, list):
            raise AuditError("Invalid OSV vulnerability list")
        for vuln in vulns:
            if not isinstance(vuln, dict) or not isinstance(vuln.get("id"), str) or not vuln["id"]:
                raise AuditError("Invalid OSV vulnerability record")
            if not vuln.get("withdrawn"):
                findings[vuln["id"]] = vuln
        token = page.get("next_page_token")
        if not token:
            return list(findings.values())
        if not isinstance(token, str) or token in tokens:
            raise AuditError("Invalid or repeated OSV pagination token")
        tokens.add(token)
    raise AuditError("OSV pagination limit exceeded")


def scan(bom, request=request_json):
    report = {"provider": "OSV", "checkedAt": datetime.now(timezone.utc).isoformat(),
              "status": "complete", "checked": [], "findings": [], "errors": []}
    for component in bom["components"]:
        try:
            vulns = query_component(component, request)
            report["checked"].append(component["purl"])
            for vuln in vulns:
                report["findings"].append({"purl": component["purl"], "scope": component["scope"],
                                            "vulnerability": vuln})
        except AuditError as ex:
            report["errors"].append({"purl": component["purl"], "error": str(ex)})
            # Fail closed without retrying a service outage for every component.
            break
    if report["errors"]:
        report["status"] = "incomplete"
    return report


def scan_exit_code(report):
    return 2 if report["status"] != "complete" else 1 if report["findings"] else 0


def verify_upstream(lock):
    for artifact in lock["artifacts"]:
        digest = hashlib.sha256()
        with urlopen(artifact_url(artifact), timeout=30) as response:
            for chunk in iter(lambda: response.read(65536), b""):
                digest.update(chunk)
        if digest.hexdigest() != artifact["sha256"]:
            raise AuditError(f"Maven Central checksum mismatch: {artifact['file']}")
        print(f"Maven Central matches: {artifact['file']}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("verify", "generate", "scan", "verify-upstream"))
    parser.add_argument("--check", action="store_true", help="Fail if committed SBOM is stale")
    parser.add_argument("--output", type=Path, help="SBOM or vulnerability report destination")
    args = parser.parse_args(argv)
    try:
        lock = inventory()
        bom = sbom(lock)
        if args.command == "verify-upstream":
            verify_upstream(lock)
        elif args.command in ("generate", "verify"):
            output = args.output or ROOT / "lib/sbom.cdx.json"
            expected = json_text(bom)
            if args.command == "verify" or args.check:
                if not output.is_file() or output.read_text(encoding="utf-8") != expected:
                    raise AuditError("SBOM is missing or stale; run python3 scripts/dependencies.py generate")
                print(f"Verified {len(lock['artifacts'])} JARs and {len(bom['components'])} SBOM components")
            else:
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_bytes(expected.encode("utf-8"))
                print(f"Generated {output}")
        else:
            report = scan(bom)
            output = args.output or ROOT / "build/security/osv-report.json"
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(json_text(report).encode("utf-8"))
            print(f"OSV: {report['status']}; {len(report['checked'])} components checked; "
                  f"{len(report['findings'])} findings; {len(report['errors'])} errors")
            for finding in report["findings"]:
                print(f"{finding['purl']}: {finding['vulnerability']['id']}")
            for error in report["errors"]:
                print(f"{error['purl']}: {error['error']}", file=sys.stderr)
            return scan_exit_code(report)
        return 0
    except (AuditError, OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile, ET.ParseError) as ex:
        print(f"Dependency audit failed: {ex}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())

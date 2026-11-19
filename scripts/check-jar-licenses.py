#!/usr/bin/env python3
"""Verify legal texts and runtime contents in the actual distributable JAR."""

import json
from pathlib import Path
import re
import struct
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    version = re.search(r'^version: "([0-9.]+)"$', (ROOT / "CITATION.cff").read_text(), re.M)[1]
    artifact = ROOT / "dist" / f"github-analyzer-{version}.jar"
    inventory = json.loads((ROOT / "lib/dependencies.lock.json").read_text())
    legal_count = 0
    with ZipFile(artifact) as output:
        names = set(output.namelist())
        manifest = output.read("META-INF/MANIFEST.MF").decode()
        require("Main-Class: es.deusto.prog3.githubanalyzer.Main" in manifest, "Incorrect main class")
        application = [n for n in names if n.startswith("es/deusto/prog3/githubanalyzer/") and n.endswith(".class")]
        require(bool(application), "Application classes missing")
        require(all(struct.unpack(">H", output.read(n)[6:8])[0] == 61 for n in application), "Expected Java 17 classes")
        for dependency in inventory["artifacts"]:
            if dependency["scope"] != "runtime":
                continue
            jar = ROOT / dependency["file"]
            prefix = f"META-INF/licenses/{jar.stem}/"
            has_license = False
            with ZipFile(jar) as original:
                for entry in original.infolist():
                    if entry.is_dir():
                        continue
                    filename = entry.filename.rsplit("/", 1)[-1].lower()
                    if filename.endswith(".class"):
                        if filename != "module-info.class":
                            require(output.read(entry.filename) == original.read(entry),
                                    f"Missing or modified runtime class: {jar.name}/{entry.filename}")
                        continue
                    if any(word in filename for word in ("license", "notice", "copying", "copyright")):
                        require(output.read(prefix + entry.filename) == original.read(entry),
                                f"Missing or modified legal text: {jar.name}/{entry.filename}")
                        has_license |= "license" in filename or filename.startswith("copying")
                        legal_count += 1
            extra = ROOT / "LICENSES/runtime" / jar.stem
            for file in extra.rglob("*"):
                if file.is_file():
                    target = prefix + file.relative_to(extra).as_posix()
                    require(output.read(target) == file.read_bytes(), f"Modified supplemental text: {target}")
                    has_license |= "license" in file.name.lower() or file.name.lower().startswith("copying")
                    legal_count += 1
            require(has_license, f"No license text for {jar.name}")
        for file in (ROOT / "resources/images").glob("*.png"):
            require(output.read("images/" + file.name) == file.read_bytes(), f"Missing icon: {file.name}")
        for name in ("LICENSE", "NOTICE", "docs/THIRD_PARTY_NOTICES.md"):
            require(output.read(name) == (ROOT / name).read_bytes(), f"Missing project notice: {name}")
        forbidden = [n for n in names if n.startswith(("org/junit/", "junit/", "org/opentest4j/", "org/apiguardian/", "resources/"))
                     or "benchmark" in n.lower() or n.rsplit("/", 1)[-1] in ("config.properties", "repositories.txt")]
        require(not forbidden, f"Unexpected test or local data entries: {forbidden}")
    print(f"OK: {artifact.name}; {legal_count} original legal texts; Java 17 classes, runtime libraries and icons verified")


if __name__ == "__main__":
    main()

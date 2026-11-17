# Third-party notices

GitHub Analyzer distributes the following libraries. Their respective license
terms apply to each component; this list does not replace the license texts or
notices shipped by their authors.

## Runtime libraries included in the runnable JAR

| Component | Version | License | Project |
| --- | --- | --- | --- |
| Apache Commons IO | 2.22.0 | Apache-2.0 | https://commons.apache.org/proper/commons-io/ |
| Apache Commons Lang | 3.21.0 | Apache-2.0 | https://commons.apache.org/proper/commons-lang/ |
| GitHub API for Java | 1.330 | MIT | https://github.com/hub4j/github-api |
| Jackson Annotations | 2.21 | Apache-2.0 | https://github.com/FasterXML/jackson-annotations |
| Jackson Core | 2.21.7 | Apache-2.0 | https://github.com/FasterXML/jackson-core |
| Jackson Databind | 2.21.7 | Apache-2.0 | https://github.com/FasterXML/jackson-databind |

Jackson Core also embeds relocated classes from **FastDoubleParser 2.0.1** (MIT).
Its embedded POM identifies the upstream dependency; its JAR retains
`META-INF/FastDoubleParser-LICENSE`, `FastDoubleParser-ThirdParty-LICENSE` and
`Schubfach-LICENSE`. These copied-code notices remain applicable alongside
Jackson's main Apache-2.0 license.

## Test-only library included in the source distribution

| Component | Version | License | Project |
| --- | --- | --- | --- |
| JUnit Platform Console Standalone | 6.1.3 | EPL-2.0 | https://junit.org/ |

The standalone runner bundles additional libraries, including relocated copies.
They are part of the source-distribution SBOM, not the runnable application JAR:

| Embedded component | Version | License |
| --- | --- | --- |
| JUnit Jupiter, Platform and Vintage modules (11 modules) | 6.1.3 | EPL-2.0 |
| JUnit 4 | 4.13.2 | EPL-1.0 |
| API Guardian | 1.1.2 | Apache-2.0 |
| Hamcrest Core | 1.3 | BSD-3-Clause |
| OpenTest4J | 1.3.0 | Apache-2.0 |
| Open Test Reporting tooling SPI and relocated events | 0.2.5 | Apache-2.0 |
| Picocli (relocated) | 4.7.7 | Apache-2.0 |
| FastCSV (relocated) | 4.2.0 | MIT |

The runner retains its third-party license texts in `META-INF/LICENSE-*`.

## Dependency review and updates

The review on **2026-11-17** compared all seven vendored JARs byte for byte with
their canonical Maven Central artifacts. All matched. Their SHA-256 hashes,
selected versions, usage scopes, required runtime links and embedded-component
evidence are recorded in `lib/dependencies.lock.json`. The inventory covers **27
Maven components: seven JARs and twenty embedded components**.

- The six ordinary JARs provide Maven coordinates in `META-INF/maven/`.
- JUnit identifies its version in the manifest and sixteen embedded components in
  `META-INF/shadowed-artifacts`. Three further libraries are relocated inside its
  modules. Their versions were checked against the runner's exact build revision
  [`f59f60d2cebdf2224235d81f781b1f310cbc8138`](https://github.com/junit-team/junit-framework/tree/f59f60d2cebdf2224235d81f781b1f310cbc8138),
  including its version catalog and shading declarations. Class markers in the
  vendored JAR are also checked locally.
- Jackson's embedded POM and relocated FastDoubleParser classes identify the
  twentieth embedded component. Embedded components deliberately have no hash
  claiming that relocated bytes equal the original upstream JAR; their container
  JAR's pinned hash covers those bytes.
- The required runtime dependencies declared by the embedded POMs are covered by
  the shipped JARs or their embedded components. The application's newer selected
  versions override the older defaults in GitHub API's POM. Optional transports,
  optional JWT support, provided annotations and upstream test dependencies that
  are not shipped are not added to the inventory.

The generated `lib/sbom.cdx.json` uses the
[CycloneDX 1.6 format](https://cyclonedx.org/docs/1.6/json/). It describes physical
Maven library contents in the source distribution and selected runtime dependency
links, not a complete upstream dependency graph or a file-level inventory of the
JDK, Python, operating system, icons or individual copied algorithm snippets.
Test libraries use `excluded` scope so consumers do not mistake them for runtime
dependencies. The scanner explicitly includes them nevertheless.

The initial [OSV](https://google.github.io/osv.dev/post-v1-query/) check completed
for all 27 components with **zero active findings**. This is a dated database
result, not a guarantee that dependencies are vulnerability-free. Daily scans
check for subsequently published advisories without requiring a code change.
Only public Maven package identities and versions are sent to OSV; application
configuration, repository lists, credentials and student data are never read by
the audit scripts. Results are written to `build/security/osv-report.json`.

Exit statuses are `0` for a complete clean scan, `1` for active findings of any
severity, and `2` for invalid inventory or an incomplete scan. Pagination is
followed, withdrawn records are ignored, and transient network errors have bounded
retries. There are no advisory suppressions or automatic version changes.

To update a dependency:

1. Download the official artifact, review its license, runtime requirements and
   any shaded components, then replace the JAR and its Eclipse classpath entry.
2. Update `lib/dependencies.lock.json` with the reviewed coordinates, SHA-256, scope,
   dependency links and embedded evidence. The tool never blesses changed bytes
   by silently updating this lock file.
3. Run `python3 scripts/dependencies.py verify-upstream` to compare the selected
   bytes with Maven Central, and update these notices where needed.
4. Run `python3 scripts/dependencies.py generate` and commit the generated SBOM
   with the inventory and JAR update. Its contents are deterministic; changes to
   the application version in `CITATION.cff` also require regeneration.
5. Run the offline audit tests, inventory verification, online vulnerability scan,
   application tests and packaging before opening the update PR. The JSON report
   is an ignored build artifact, not a permanent claim of vulnerability status.

## Icons

The PNG icons under `resources/images` are by
[Pixel perfect](https://www.flaticon.com/authors/pixel-perfect) on Flaticon.
They are distributed under the applicable Flaticon terms and are excluded from
the Apache-2.0 license granted for this project.

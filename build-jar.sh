#!/usr/bin/env sh
# Build a SELF-CONTAINED runnable JAR (macOS / Linux).
#
# Produces ./github-analyzer.jar with the application classes, all GUI icons AND
# all third-party libraries bundled inside — no lib/ folder needed at runtime.
#
# Run it (from any directory) with:
#     java -jar github-analyzer.jar
# Keep a `resources/` folder (with your config.properties and repositories.txt)
# next to the jar — those files stay EXTERNAL and editable (never bundled, since
# they hold your token and student data).
set -e
cd "$(dirname "$0")"

OUT=build/jar-classes
STAGE=build/fat
JAR=github-analyzer.jar
MAIN=es.deusto.prog3.githubanalyzer.Main
LIB="$(pwd)/lib"

rm -rf "$OUT" "$STAGE"
mkdir -p "$OUT" "$STAGE"

# 1) Compile application sources (tests excluded).
javac --release 17 -cp "lib/*" -d "$OUT" $(find src -name '*.java')

# 2) Unpack the runtime dependencies into the staging dir (the JUnit runner is
#    a test-only tool and is intentionally excluded).
( cd "$STAGE" && for j in "$LIB"/commons-io-*.jar "$LIB"/commons-lang3-*.jar \
                          "$LIB"/github-api-*.jar "$LIB"/jackson-*.jar; do
      jar xf "$j"
  done )

# 3) Drop artifacts that don't belong in a merged, non-modular classpath jar.
rm -f "$STAGE/META-INF/MANIFEST.MF" "$STAGE/module-info.class"
rm -f "$STAGE/META-INF/"*.SF "$STAGE/META-INF/"*.DSA "$STAGE/META-INF/"*.RSA "$STAGE/META-INF/"*.EC 2>/dev/null || true

# 4) Add our compiled classes and all GUI icons (loaded as /images/<name>).
cp -R "$OUT/." "$STAGE/"
mkdir -p "$STAGE/images"
cp resources/images/*.png "$STAGE/images/"

# 5) Package a single self-contained runnable jar (Main-Class via -e).
jar cfe "$JAR" "$MAIN" -C "$STAGE" .
echo "Built self-contained $JAR ($(du -h "$JAR" | cut -f1))"
echo "Run with: java -jar $JAR   (only a resources/ folder needs to sit next to it)"

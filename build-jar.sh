#!/usr/bin/env sh
# Build a runnable JAR (macOS / Linux).
#
# Produces ./github-analyzer.jar containing the application classes and the tree
# icons (bundled at /images/). The third-party libraries are NOT copied in: the
# manifest's Class-Path points to the lib/ folder, so keep lib/ next to the jar.
#
# Run it with:   java -jar github-analyzer.jar
# from a folder that also contains lib/ and a resources/ folder with your
# config.properties and repositories.txt (these stay external and editable).
set -e
cd "$(dirname "$0")"

OUT=build/jar-classes
JAR=github-analyzer.jar
MAIN=es.deusto.prog3.githubanalyzer.Main

# Runtime dependencies (relative to the jar location, via the Class-Path header).
LIBS="lib/commons-io-2.20.0.jar lib/commons-lang3-3.19.0.jar lib/github-api-1.330.jar lib/jackson-annotations-2.20.jar lib/jackson-core-2.20.1.jar lib/jackson-databind-2.20.1.jar"

rm -rf "$OUT"
mkdir -p "$OUT/images"

# Compile application sources (tests excluded) against the libraries.
javac -cp "lib/*" -d "$OUT" $(find src -name '*.java')

# Bundle the icons on the classpath (loaded as /images/<name> at runtime).
cp resources/images/*.png "$OUT/images/"

# Write the manifest.
MF=build/MANIFEST.MF
mkdir -p build
{
  echo "Main-Class: $MAIN"
  echo "Class-Path: $LIBS"
} > "$MF"

jar cfm "$JAR" "$MF" -C "$OUT" .
echo "Built $JAR"
echo "Run with: java -jar $JAR   (keep lib/ and resources/ alongside it)"

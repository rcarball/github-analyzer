#!/usr/bin/env sh
# Compile and run the unit tests from the command line (macOS / Linux).
# Eclipse users can just use "Run as > JUnit Test"; this is the CLI equivalent.
set -e
cd "$(dirname "$0")"

OUT=build/test-classes
JUNIT=lib/junit-platform-console-standalone-1.11.4.jar

rm -rf "$OUT"
mkdir -p "$OUT"

# Compile application + test sources against the bundled libraries.
javac --release 17 -cp "lib/*" -d "$OUT" $(find src test -name '*.java')

# Run every test on the classpath (headless: no GUI window pops up).
java -Djava.awt.headless=true -jar "$JUNIT" execute \
     --class-path "$OUT" --scan-classpath --details=tree

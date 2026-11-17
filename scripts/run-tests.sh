#!/usr/bin/env sh
# Compile and run the unit tests from the command line (macOS / Linux).
# Eclipse users can just use "Run as > JUnit Test"; this is the CLI equivalent.
set -e
if [ -z "${JAVA_HOME:-}" ] && [ "$(uname -s)" = Darwin ] && [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null) || true
    export JAVA_HOME
fi
if [ -n "${JAVA_HOME:-}" ]; then
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH
fi
cd "$(dirname "$0")/.."

OUT=build/test-classes
JUNIT=lib/junit-platform-console-standalone-6.1.3.jar

rm -rf "$OUT"
mkdir -p "$OUT"

# Compile application + test sources against the bundled libraries.
javac --release 17 -cp "lib/*" -d "$OUT" $(find src test -name '*.java')

# Run every test on the classpath (headless: no GUI window pops up).
java -Djava.awt.headless=true -cp "$JUNIT:$OUT:lib/*" org.junit.platform.console.ConsoleLauncher execute \
     --scan-classpath="$OUT" --details=tree

#!/usr/bin/env sh
# Compare the current source with a chosen Git revision, offline and outside CI.
set -eu
if [ -z "${JAVA_HOME:-}" ] && [ "$(uname -s)" = Darwin ] && [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null) || true
    export JAVA_HOME
fi
if [ -n "${JAVA_HOME:-}" ]; then PATH="$JAVA_HOME/bin:$PATH"; export PATH; fi
cd "$(dirname "$0")/.."
BASELINE=${1:-4ded5fd}
BENCH_DIR=$(mktemp -d "${TMPDIR:-/tmp}/java-source-bench.XXXXXX")
trap 'rm -rf "$BENCH_DIR"' EXIT HUP INT TERM
mkdir -p "$BENCH_DIR/baseline" "$BENCH_DIR/current"
BENCHMARK_SOURCE=test/es/deusto/prog3/githubanalyzer/JavaSourceBenchmark.java
git archive "$BASELINE" src | tar -x -C "$BENCH_DIR/baseline"
for variant in baseline current; do
    if [ "$variant" = baseline ]; then SOURCE="$BENCH_DIR/baseline/src"; else SOURCE=src; fi
    javac --release 17 -cp 'lib/*' -d "$BENCH_DIR/$variant/classes" $(find "$SOURCE" -name '*.java') "$BENCHMARK_SOURCE"
    echo "$variant (baseline revision: $BASELINE)"
    java -Xbatch -Xms512m -Xmx512m -Djava.awt.headless=true -cp "$BENCH_DIR/$variant/classes:lib/*" es.deusto.prog3.githubanalyzer.JavaSourceBenchmark
done

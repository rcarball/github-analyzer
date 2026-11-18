#!/usr/bin/env sh
# Optional real-window regression check; JDK 17+, no Python/PowerShell dependency.
set -eu
cd "$(dirname "$0")/.."
DIALOG_PROJECT=$(pwd -P)
fail() {
    printf '%s\n' "$1" >&2
    exit 1
}
# Select both tools from the same installation without altering PATH or JAVA_HOME.
DIALOG_JDK=${JAVA_HOME:-}
if [ -z "$DIALOG_JDK" ] && [ "$(uname -s)" = Darwin ] && [ -x /usr/libexec/java_home ]; then
    DIALOG_JDK=$(/usr/libexec/java_home 2>/dev/null) || DIALOG_JDK=
fi
if [ -n "$DIALOG_JDK" ]; then
    [ -x "$DIALOG_JDK/bin/javac" ] && [ -x "$DIALOG_JDK/bin/java" ] ||
        fail 'JAVA_HOME or macOS JDK discovery must identify a complete JDK containing bin/java and bin/javac.'
    DIALOG_JDK=$(CDPATH= cd "$DIALOG_JDK" && pwd -P) || fail 'Could not open the selected JDK folder.'
    DIALOG_JAVA="$DIALOG_JDK/bin/java"
    DIALOG_JAVAC="$DIALOG_JDK/bin/javac"
else
    DIALOG_JAVAC=$(command -v javac) || fail 'A JDK 17 or newer is required. Set JAVA_HOME or add its bin folder to PATH.'
    case "$DIALOG_JAVAC" in
        /*) ;;
        *) DIALOG_JAVAC="$DIALOG_PROJECT/$DIALOG_JAVAC" ;;
    esac
    # Resolve Linux alternatives and other compiler links before choosing java.
    DIALOG_LINK_COUNT=0
    while [ -L "$DIALOG_JAVAC" ]; do
        DIALOG_LINK_COUNT=$((DIALOG_LINK_COUNT + 1))
        [ "$DIALOG_LINK_COUNT" -le 40 ] || fail 'The compiler symlink chain is invalid.'
        DIALOG_LINK=$(readlink "$DIALOG_JAVAC") || fail 'Could not resolve the compiler symlink.'
        case "$DIALOG_LINK" in
            /*) DIALOG_JAVAC=$DIALOG_LINK ;;
            *) DIALOG_JAVAC="$(dirname "$DIALOG_JAVAC")/$DIALOG_LINK" ;;
        esac
    done
    DIALOG_BIN=$(CDPATH= cd "$(dirname "$DIALOG_JAVAC")" && pwd -P) || fail 'Could not open the compiler folder.'
    DIALOG_JAVAC="$DIALOG_BIN/$(basename "$DIALOG_JAVAC")"
    DIALOG_JAVA="$DIALOG_BIN/java"
    [ -x "$DIALOG_JAVAC" ] && [ -x "$DIALOG_JAVA" ] || fail 'The selected compiler has no matching executable java in its bin folder.'
fi

# The shared Java controller owns child processes, exit codes and all temporary files.
unset GITHUB_TOKEN
exec "$DIALOG_JAVA" --source 17 "$DIALOG_PROJECT/scripts/DownloadDialogLauncher.java" "$DIALOG_PROJECT"

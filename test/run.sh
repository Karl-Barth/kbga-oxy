#!/usr/bin/env bash
# Compiles the plugin and runs the offline sanity checks
# (JSON + register table + Text-mode attribute rewriting).
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"

OXYGEN_DIR="${OXYGEN_DIR:-/Applications/Oxygen XML Editor}"
CP="$(find "$OXYGEN_DIR/lib" -name '*.jar' | tr '\n' ':')build/classes"

./build.sh >/dev/null
mkdir -p build/test
javac --release 8 -encoding UTF-8 -cp "$CP" -d build/test test/ManualTest.java
java -cp "$CP:build/test" ch.kr.kbga.oxy.ManualTest

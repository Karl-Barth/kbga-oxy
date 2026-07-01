#!/usr/bin/env bash
#
# Builds the kbga-oxy plugin without any network access.
# Compiles to Java 8 bytecode against the installed oXygen jars, so the result
# runs on oXygen 22 (its bundled Java 8) and on any newer oXygen version too.
#
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

OXYGEN_DIR="${OXYGEN_DIR:-/Applications/Oxygen XML Editor}"
LIB_DIR="$OXYGEN_DIR/lib"
VERSION="1.1.0"
JAR_NAME="kbga-oxy-${VERSION}.jar"

if [ ! -f "$LIB_DIR/oxygen.jar" ]; then
  echo "ERROR: oxygen.jar not found under '$LIB_DIR'." >&2
  echo "Set OXYGEN_DIR to your oXygen installation, e.g.:" >&2
  echo "  OXYGEN_DIR='/Applications/Oxygen XML Editor' ./build.sh" >&2
  exit 1
fi

# Classpath = every jar in the oXygen lib dir.
CP="$(find "$LIB_DIR" -name '*.jar' | tr '\n' ':')"

rm -rf build "lib/$JAR_NAME"
mkdir -p build/classes lib

echo "Compiling (Java 8 target) ..."
find src/main/java -name '*.java' > build/sources.txt
javac --release 8 -encoding UTF-8 -classpath "$CP" -d build/classes @build/sources.txt

echo "Packaging $JAR_NAME ..."
jar --create --file "lib/$JAR_NAME" -C build/classes .

echo
echo "Built: lib/$JAR_NAME"
echo "Install with:  ./install.sh        (copies the plugin into oXygen)"
echo "Or package an add-on with:  ./make-addon.sh"

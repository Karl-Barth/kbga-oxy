#!/usr/bin/env bash
#
# Installs the built plugin directly into the oXygen "plugins" directory.
# (Alternative: use ./make-addon.sh + oXygen "Help > Install new add-ons…",
#  which survives oXygen updates.)
#
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

OXYGEN_DIR="${OXYGEN_DIR:-/Applications/Oxygen XML Editor}"
VERSION="1.1.2"
JAR="lib/kbga-oxy-${VERSION}.jar"
TARGET="$OXYGEN_DIR/plugins/kbga-oxy"

if [ ! -f "$JAR" ]; then
  echo "Plugin jar missing — run ./build.sh first." >&2
  exit 1
fi
if [ ! -d "$OXYGEN_DIR/plugins" ]; then
  echo "ERROR: '$OXYGEN_DIR/plugins' not found. Set OXYGEN_DIR." >&2
  exit 1
fi

echo "Installing into: $TARGET"
rm -rf "$TARGET"
mkdir -p "$TARGET/lib"
cp plugin.xml "$TARGET/plugin.xml"
cp "$JAR" "$TARGET/lib/"

echo "Done. Restart oXygen, then enable the toolbar via"
echo "  Window > Show Toolbar, or use the 'KBGA' menu (Ctrl+Shift+K)."

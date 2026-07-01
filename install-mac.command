#!/bin/bash
# kbga-oxy — macOS one-click installer.
# Double-click in Finder. Downloads the latest release from GitHub and installs it
# into your local oXygen (no build, no Java needed). Re-run any time to update.

set -e
REPO="Karl-Barth/kbga-oxy"

echo "kbga-oxy installer"
echo

# Locate oXygen (a folder under /Applications that contains a "plugins" directory).
OXY="${OXYGEN_DIR:-}"
if [ -z "$OXY" ]; then
  for d in "/Applications/Oxygen XML Editor" "/Applications/Oxygen XML Author" \
           /Applications/Oxygen*; do
    if [ -d "$d/plugins" ]; then OXY="$d"; break; fi
  done
fi
if [ -z "$OXY" ] || [ ! -d "$OXY/plugins" ]; then
  echo "Could not find oXygen under /Applications."
  echo "Set it and re-run, e.g.:  OXYGEN_DIR='/Applications/Oxygen XML Editor' \"$0\""
  read -n1 -r -p "Press any key to close..."; exit 1
fi
echo "oXygen:   $OXY"

# Find the latest release zip asset.
URL="$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" \
        | grep -oE 'https://[^"]*/kbga-oxy-[^"/]*\.zip' | head -1)"
if [ -z "$URL" ]; then
  echo "Could not determine the latest release download URL."
  read -n1 -r -p "Press any key to close..."; exit 1
fi
echo "Download: $URL"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
curl -fsSL "$URL" -o "$TMP/kbga-oxy.zip"
/usr/bin/unzip -oq "$TMP/kbga-oxy.zip" -d "$TMP"
SRC="$(ls -d "$TMP"/kbga-oxy-* 2>/dev/null | head -1)"
if [ -z "$SRC" ]; then
  echo "Unexpected archive layout."; read -n1 -r -p "Press any key to close..."; exit 1
fi

TARGET="$OXY/plugins/kbga-oxy"
echo "Install:  $TARGET"
if ! rm -rf "$TARGET" 2>/dev/null; then
  echo "No write permission for $OXY/plugins."
  echo "Re-run from Terminal with sudo, or use the add-on URL instead (see README)."
  read -n1 -r -p "Press any key to close..."; exit 1
fi
mkdir -p "$TARGET"
cp -R "$SRC"/. "$TARGET"/

echo
echo "Installed. Restart oXygen, then use the 'KBGA' menu (Ctrl+Shift+K) or the"
echo "'KBGA @ref' toolbar button. Set your KBGA URL under KBGA > KBGA-Einstellungen."
read -n1 -r -p "Press any key to close..."

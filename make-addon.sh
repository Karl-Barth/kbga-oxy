#!/usr/bin/env bash
#
# Packages BOTH KBGA oXygen add-ons into one update site:
#   dist/kbga-oxy-<v>.zip            – the plugin archive
#   dist/kbga-reading-view-<fv>.zip  – the TEI reading-view framework (opt-in Author CSS)
#   addon/updateSite.xml             – ONE descriptor advertising both (oXygen filters by oxy_version)
#
# Install in oXygen via:  Help > Install new add-ons…  →  point to addon/updateSite.xml
# (or host the files on a web server / GitHub release and use that URL for auto-updates).
#
# When ADDON_BASE_URL is set (e.g. a GitHub release download URL) the descriptor points at
# the hosted zips; otherwise it uses relative paths for local installs.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

# --- plugin ---------------------------------------------------------------
VERSION="1.1.5"
OXY_MIN="22.0+"
ID="ch.kr.kbga.oxy"
NAME="kbga-oxy"
AUTHOR="K-R / KBGA"
JAR="lib/kbga-oxy-${VERSION}.jar"
PKG_DIR="kbga-oxy-${VERSION}"
ZIP="${PKG_DIR}.zip"

# --- reading-view framework (independently versioned) ---------------------
FW_VERSION="1.0.0"
FW_OXY_MIN="23.0+"
FW_ID="ch.kr.kbga.reading-view"
FW_NAME="KBGA Leseansicht (TEI-Author-CSS)"
FW_PKG="kbga-reading-view"
FW_ZIP="${FW_PKG}-${FW_VERSION}.zip"
CSS="author-css/kbga-author.css"
FW_SCRIPT="framework/kbga-reading-view.xml"

if [ ! -f "$JAR" ]; then
  echo "Plugin jar missing — run ./build.sh first." >&2
  exit 1
fi
[ -f "$CSS" ]       || { echo "Fehlt: $CSS"       >&2; exit 1; }
[ -f "$FW_SCRIPT" ] || { echo "Fehlt: $FW_SCRIPT" >&2; exit 1; }

rm -rf dist
mkdir -p "dist/$PKG_DIR/lib" "dist/$FW_PKG/css" addon

# Plugin folder + zip.
cp plugin.xml "dist/$PKG_DIR/plugin.xml"
cp "$JAR" "dist/$PKG_DIR/lib/"
( cd dist && zip -r -q "$ZIP" "$PKG_DIR" )
echo "Created dist/$ZIP"

# Framework folder (extension script + CSS) + zip.
cp "$FW_SCRIPT" "dist/$FW_PKG/kbga-reading-view.xml"
cp "$CSS"       "dist/$FW_PKG/css/kbga-author.css"
( cd dist && zip -r -q "$FW_ZIP" "$FW_PKG" )
echo "Created dist/$FW_ZIP"

if [ -n "${ADDON_BASE_URL:-}" ]; then
  HREF="${ADDON_BASE_URL%/}/${ZIP}"
  FW_HREF="${ADDON_BASE_URL%/}/${FW_ZIP}"
else
  HREF="../dist/${ZIP}"
  FW_HREF="../dist/${FW_ZIP}"
fi

cat > addon/updateSite.xml <<XML
<?xml version="1.0" encoding="UTF-8"?>
<xt:extensions xmlns:xt="http://www.oxygenxml.com/ns/extension">
    <xt:extension id="${ID}">
        <xt:location href="${HREF}"/>
        <xt:version>${VERSION}</xt:version>
        <xt:oxy_version>${OXY_MIN}</xt:oxy_version>
        <xt:type>plugin</xt:type>
        <xt:author>${AUTHOR}</xt:author>
        <xt:name>${NAME}</xt:name>
        <xt:description>
            <html xmlns="http://www.w3.org/1999/xhtml">
                <body>
                    <p>Sucht Akteure, Orte, Literatur und Lieder live in der KBGA-Metadatenbank und schreibt die id (z. B. kbga-actors-123) in das passende Attribut des Elements unter dem Cursor: @ref für Akteure/Orte, @corresp für Literatur/Lieder (Lieder zusätzlich mit type="song"). Basis-URL, Trefferzahl und Element→Register-Mapping sind konfigurierbar.</p>
                </body>
            </html>
        </xt:description>
    </xt:extension>
    <xt:extension id="${FW_ID}">
        <xt:location href="${FW_HREF}"/>
        <xt:version>${FW_VERSION}</xt:version>
        <xt:oxy_version>${FW_OXY_MIN}</xt:oxy_version>
        <xt:type>framework</xt:type>
        <xt:author>${AUTHOR}</xt:author>
        <xt:name>${FW_NAME}</xt:name>
        <xt:description>
            <html xmlns="http://www.w3.org/1999/xhtml">
                <body>
                    <p>Opt-in Lese-CSS für den TEI-Author-Modus (Korrekturlesen); erweitert das eingebaute TEI-P5-Framework nicht-brechend um eine „KBGA Lesefont“-Alternativ-CSS. Benötigt oXygen 23.0+.</p>
                </body>
            </html>
        </xt:description>
    </xt:extension>
</xt:extensions>
XML

echo "Created addon/updateSite.xml (Plugin + Leseansicht-Framework)"
echo
echo "Install: oXygen → Help → Install new add-ons… → enter the path to addon/updateSite.xml"
echo "When hosting on the web, place updateSite.xml and the .zip files together (or set ADDON_BASE_URL)."

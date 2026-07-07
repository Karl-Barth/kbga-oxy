#!/usr/bin/env bash
#
# Packages the plugin as an oXygen add-on:
#   dist/kbga-oxy-<v>.zip   – the plugin archive
#   addon/updateSite.xml    – the add-on descriptor / update site
#
# Install in oXygen via:  Help > Install new add-ons…  →  point to addon/updateSite.xml
# (or host both files on a web server and use that URL for auto-updates).
#
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

VERSION="1.1.4"
OXY_MIN="22.0+"
ID="ch.kr.kbga.oxy"
NAME="kbga-oxy"
AUTHOR="K-R / KBGA"
JAR="lib/kbga-oxy-${VERSION}.jar"
PKG_DIR="kbga-oxy-${VERSION}"
ZIP="${PKG_DIR}.zip"

if [ ! -f "$JAR" ]; then
  echo "Plugin jar missing — run ./build.sh first." >&2
  exit 1
fi

rm -rf dist
mkdir -p "dist/$PKG_DIR/lib" addon
cp plugin.xml "dist/$PKG_DIR/plugin.xml"
cp "$JAR" "dist/$PKG_DIR/lib/"

( cd dist && zip -r -q "$ZIP" "$PKG_DIR" )
echo "Created dist/$ZIP"

# When ADDON_BASE_URL is set (e.g. a GitHub release download URL), the descriptor
# points at the hosted zip; otherwise it uses a relative path for local installs.
if [ -n "${ADDON_BASE_URL:-}" ]; then
  HREF="${ADDON_BASE_URL%/}/${ZIP}"
else
  HREF="../dist/${ZIP}"
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
</xt:extensions>
XML

echo "Created addon/updateSite.xml"
echo
echo "Install: oXygen → Help → Install new add-ons… → enter the path to addon/updateSite.xml"
echo "When hosting on the web, place updateSite.xml and the .zip together and adjust the href."

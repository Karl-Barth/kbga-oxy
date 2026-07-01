# kbga-oxy

oXygen-XML-Editor-Plugin für die **Karl-Barth-Gesamtausgabe (KBGA)**. Es durchsucht die
KBGA-Metadatenbank (<https://meta.karl-barth.ch>) live und schreibt die passende Referenz-ID
in das TEI-Element unter dem Cursor — in **Text**- *und* **Author**-Modus.

Vorbild und Architektur: [anton-oxy](https://github.com/kraenzle-ritter/anton-oxy).

## Was es macht

Cursor in ein Element setzen, Aktion auslösen (**Ctrl+Shift+K**, Toolbar-Button **„KBGA @ref“**
oder Menü **KBGA → KBGA-Referenz einfügen**), Suchbegriff eingeben, Treffer wählen — das Plugin
setzt das richtige Attribut mit der ID aus der Datenbank (`full-id`, z. B. `kbga-actors-123`).

Pro Suche geht **eine** HTTP-Anfrage an `…/api/{register}?search=…` — es wird nie ein ganzes
Register heruntergeladen. Die Such-Endpunkte sind öffentlich, es wird kein Login gesendet.

## Register, Elemente und Attribute

| Entity | Register | Vorbelegtes Element | Attribut | Beispielwert |
|---|---|---|---|---|
| Akteure (Person) | `actors` | `persName` | `@ref` | `kbga-actors-123` |
| Akteure (Organisation) | `actors` | `orgName` | `@ref` | `kbga-actors-10784` |
| Orte | `places` | `placeName` | `@ref` | `kbga-places-5` |
| Literatur | `bibls` | `bibl` | `@corresp` | `kbga-bibls-1` |
| Lieder | `songs` | `bibl` + `@type="song"` | `@corresp` | `kbga-songs-247` |

Das Register wird aus dem Element unter dem Cursor **vorbelegt**, lässt sich im Suchdialog aber
jederzeit umschalten. Da Literatur und Lieder beide das Element `<bibl>` nutzen, ist bei `<bibl>`
zunächst **Literatur** aktiv; für ein Lied im Dropdown auf **Lieder** wechseln — dann setzt das
Plugin zusätzlich `type="song"`. Das Zielattribut (`ref` bzw. `corresp`) ergibt sich automatisch
aus dem gewählten Register.

## Installation (empfohlen: als Add-on)

In oXygen: **Hilfe → Neue Add-ons installieren…**, diese URL eintragen, Assistent durchlaufen,
oXygen neu starten:

```
https://github.com/Karl-Barth/kbga-oxy/releases/latest/download/updateSite.xml
```

So installiert übersteht das Plugin oXygen-Updates. macOS-Alternative: `install-mac.command`
doppelklicken (lädt das letzte Release und installiert es ohne Build).

## Selbst bauen

Benötigt eine lokale oXygen-Installation (für `oxygen.jar`) und ein JDK.

```bash
OXYGEN_DIR="/Applications/Oxygen XML Editor" ./build.sh   # kompiliert lib/kbga-oxy-1.0.0.jar
./test/run.sh                                             # Offline-Sanity-Checks
./install.sh                                              # direkt in oXygen/plugins/ kopieren
./make-addon.sh                                           # dist/*.zip + addon/updateSite.xml
```

`OXYGEN_DIR` zeigt auf das oXygen-Verzeichnis (Standard: `/Applications/Oxygen XML Editor`).
Kompiliert wird auf **Java-8-Bytecode**, läuft daher auf oXygen 22 und neuer.

## Einstellungen

**KBGA → KBGA-Einstellungen…** (oder im Suchdialog **Einstellungen…**):

- **KBGA base URL** (Standard: `https://meta.karl-barth.ch`) — z. B. auf eine Staging-/DDEV-Instanz umstellen
- **Treffer pro Suche** (Standard: `30`)
- **ID-Template** (Standard: `{fullId}`) — Platzhalter `{fullId}` `{slug}` `{register}` `{id}`
- **Element → Register** — Zuordnung fürs Vorbelegen des Registers (eine `element=register`-Zeile je Element; Register: `actors`, `places`, `bibls`, `songs`)
- **Selbstsignierte / DDEV-Zertifikate akzeptieren** — nur für lokale Entwicklung

## Voraussetzungen

oXygen XML Editor/Author 22 oder neuer (Java 8 Bytecode).

## Lizenz

MIT (siehe `LICENSE`).

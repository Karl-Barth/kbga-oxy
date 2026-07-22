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

## Auszeichnen und referenzieren in einem Schritt

Steht der Cursor **nicht** in einem konfigurierten Element, aber es ist **Text markiert**, dann
zeichnet das Plugin die Auswahl aus *und* referenziert sie in einem Rutsch: Register im Dialog
wählen, Treffer bestätigen — das passende TEI-Element wird um die Auswahl gelegt und das Attribut
gesetzt. Akteure werden als `persName`, Organisationen (erkannt am Typ) als `orgName`, Orte als
`placeName`, Literatur/Lieder als `bibl` (bei Liedern zusätzlich `type="song"`) ausgezeichnet.
Funktioniert im **Text**- und **Author**-Modus.

## Weitere Funktionen

- **Literatur + Lieder gemeinsam:** Bei `<bibl>` durchsucht das Plugin **beide** Register
  gleichzeitig und markiert Lieder mit `[Lied]`. Wählt man ein Lied, werden `@corresp` und
  `type="song"` automatisch gesetzt — kein manuelles Umschalten mehr nötig.
- **Zuletzt verwendet:** Bei leerem Suchfeld zeigt der Dialog die letzten Treffer je Register —
  ein Klick genügt für wiederkehrende Personen/Orte.
- **Im Browser öffnen:** Button **„Im Browser…“** öffnet den gewählten Eintrag im KBGA-Portal;
  ohne Treffer die Portalsuche zum Prüfen oder Anlegen eines fehlenden Eintrags.
- **Weitere Vorkommen mitauszeichnen:** Nach dem Referenzieren eines Akteurs/Orts sucht das
  Plugin (im Text-Modus) das restliche Dokument nach weiteren Vorkommen ab — inklusive
  **Genitiv-Endung** (`Barths`, `Marx'`; die Endung bleibt außerhalb des Elements) und der
  Namensvarianten aus der Datenbank (`name`, `alternative_names`, `abbreviations`). In einer
  Checkliste wählt man die passenden Stellen, bereits ausgezeichnete werden übersprungen.
  Abschaltbar in den Einstellungen.
- **Referenzen prüfen:** Menü **KBGA → KBGA-Referenzen prüfen…** löst alle `kbga-…`-IDs des
  Dokuments live gegen die Datenbank auf und meldet fehlende (404) oder nicht prüfbare Referenzen
  (im Text-Modus ausführen).

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
OXYGEN_DIR="/Applications/Oxygen XML Editor" ./build.sh   # kompiliert lib/kbga-oxy-1.1.5.jar
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
- **Vorschau-Kontext (Zeichen/Seite)** (Standard: `60`) — wie viel Text bei „Weitere Vorkommen mitauszeichnen" links und rechts der Fundstelle angezeigt wird; Wörter am Rand werden nicht abgeschnitten
- **Selbstsignierte / DDEV-Zertifikate akzeptieren** — nur für lokale Entwicklung

## Voraussetzungen

oXygen XML Editor/Author 22 oder neuer (Java 8 Bytecode).

## Lizenz

MIT (siehe `LICENSE`).

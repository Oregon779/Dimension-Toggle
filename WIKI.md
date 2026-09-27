# DimensionToggle – Wiki

Mit **DimensionToggle** steuerst du auf deinem Paper-Server, wer wann in den
**Nether** und ins **End** darf: an/aus schalten, Wartung mit Countdown,
tägliche Öffnungszeiten, Notfall-Lockdown, Spielerlimits und vieles mehr –
per Command oder komplett per Klick im In-Game-Editor.

> Dieses Wiki beschreibt den aktuellen Stand des Plugins (Version 3.1.x).
> Den Versionsverlauf findest du in der [README](README.md).

## Inhalt

1. [Überblick](#1-überblick)
2. [Voraussetzungen und Installation](#2-voraussetzungen-und-installation)
3. [Schnellstart](#3-schnellstart)
4. [Commands](#4-commands)
5. [Permissions](#5-permissions)
6. [Deaktivieren, Soft-Lock, Lockdown und Co. im Vergleich](#6-deaktivieren-soft-lock-lockdown-und-co-im-vergleich)
7. [Features im Detail](#7-features-im-detail)
8. [Der In-Game-Editor](#8-der-in-game-editor)
9. [Konfiguration](#9-konfiguration)
10. [FAQ und Fehlerbehebung](#10-faq-und-fehlerbehebung)
11. [Bekannte Einschränkungen](#11-bekannte-einschränkungen)
12. [Für Entwickler](#12-für-entwickler)

---

## 1. Überblick

| Feature | Kurz erklärt |
|---|---|
| **Dimension an/aus** | Nether und End einzeln sperren oder öffnen – mit Title/Sound für alle Spieler. |
| **Soft-Lock** | Niemand Neues kommt rein, wer drin ist, darf bleiben. |
| **Lockdown** | Notfall-Knopf: beide Dimensionen sofort zu, alle Spieler raus. |
| **Spielerlimit** | Maximal X Spieler gleichzeitig pro Dimension. |
| **Wartung** | Countdown (z.B. 10 Minuten) mit Warnungen, danach werden alle rausgebracht und die Dimension geschlossen. |
| **Zeitplan** | Tägliche automatische Öffnungs- und Schließzeiten. |
| **End-Gateways** | Gateway-Teleports im End separat sperren. |
| **Dimension-Schalter** | Keep Inventory, Mob-Spawning, PvP, Elytra-Flug (End), Spawner (Nether). |
| **Mob-Management** | Pro Mob-Typ das Spawnen verbieten und automatische Aufräum-Intervalle setzen. |
| **World Border** | Größe und Mittelpunkt der Weltgrenze per Klick ändern. |
| **Dashboard** | Spieler-Rekord, geladene Chunks, häufigste Entities, TPS. |
| **In-Game-Editor** | Alles oben per Klick unter `/dt editor`. |
| **Logging** | Jede Aktion mit Zeit und Verursacher in einer Log-Datei. |
| **Update-Checker** | Meldet neue Versionen auf Modrinth. |
| **Zweisprachig** | Deutsch und Englisch mitgeliefert, eigene Sprachen möglich. |

Alle Texte – Chat, Titles, Bossbars, GUI – sind in den Config-Dateien
anpassbar, mit klassischen `&`-Farbcodes, Hex-Farben und MiniMessage
(Farbverläufe usw.).

---

## 2. Voraussetzungen und Installation

**Voraussetzungen**

- **Paper** 1.21.x (entwickelt und getestet gegen 1.21.1). Spigot/CraftBukkit wird nicht unterstützt, das Plugin nutzt die Paper-API.
- **Java 21**
- Kein **Folia** (nicht unterstützt)
- Optional: **PvPManager** – nur nötig, damit der PvP-Schalter im Editor freigeschaltet ist

**Installation**

1. `DimensionToggle-<version>.jar` in den Ordner `plugins/` legen.
2. Server starten. Das Plugin legt `plugins/DimensionToggle/` mit allen Dateien an.
3. Optional: in `config.yml` die Sprache setzen (`language: "de"`) und `/dt reload` ausführen.

**Update auf eine neue Version**

1. Alte Jar durch die neue ersetzen, Server neu starten.
2. Neue *Hauptabschnitte* der Config werden automatisch ans Ende deiner
   `config.yml` (und der GUI-Configs) angehängt. Deine eigenen Werte werden
   nie überschrieben.
3. Neue Optionen *innerhalb* eines Abschnitts, den du schon hast, werden
   nicht automatisch ergänzt – vergleiche bei einem Update kurz mit der
   Standard-Config der neuen Version (siehe [Config-Updates](#config-updates)).

---

## 3. Schnellstart

1. `/dt editor` öffnen (als OP oder mit `dimensiontoggle.admin`).
2. Auf den **Nether**- oder **End**-Kopf klicken → Control Panel der Dimension.
3. Oben in der Mitte den großen Schalter klicken → Dimension an/aus.
4. Alles andere (Limit, Wartung, Zeitplan, Mobs, World Border …) findest du
   als Buttons im selben Panel.

**Wichtig beim Testen:** OPs haben standardmäßig `dimensiontoggle.bypass` und
werden deshalb **nie** blockiert. Teste Sperren mit einem Nicht-OP-Account
oder entziehe dir die Permission (siehe [FAQ](#10-faq-und-fehlerbehebung)).

Per Command geht es genauso:

```
/dt nether off              Nether schließen
/dt maintenance end 10m     End in 10 Minuten für Wartung schließen
/dt limit nether 20         Maximal 20 Spieler im Nether
/dt status                  Aktuellen Zustand anzeigen
```

---

## 4. Commands

Hauptcommand: `/dimensiontoggle` – Aliase: **`/dt`**, `/dimtoggle`.
`/dt` ohne Argument zeigt die Hilfe.

| Command | Was er macht | Permission |
|---|---|---|
| `/dt help` | Zeigt alle Commands, die **du** benutzen darfst. | keine |
| `/dt status` | Zustand von Nether und End (aktiv / deaktiviert / Soft-Lock / Wartung läuft) plus Spielerzahl. | `dimensiontoggle.status` |
| `/dt editor` | Öffnet den Klick-Editor. Nur für Spieler, nicht in der Konsole. | `dimensiontoggle.admin` |
| `/dt nether <on\|off>` | Nether aktivieren / deaktivieren. Alle Online-Spieler bekommen die konfigurierte Benachrichtigung + Sound. | `dimensiontoggle.admin` |
| `/dt end <on\|off>` | Dasselbe fürs End. | `dimensiontoggle.admin` |
| `/dt softlock <nether\|end>` | Soft-Lock ein/aus (umschalten). | `dimensiontoggle.admin` |
| `/dt lockdown` | Lockdown aktivieren – nochmal ausführen hebt ihn auf. | `dimensiontoggle.admin` |
| `/dt limit <nether\|end> <zahl>` | Setzt das Spielerlimit (0 oder mehr) und **aktiviert** es. Wird sofort in die `config.yml` geschrieben, kein Reload nötig. | `dimensiontoggle.admin` |
| `/dt maintenance <nether\|end> <zeit>` | Startet einen Wartungs-Countdown. | `dimensiontoggle.admin` |
| `/dt maintenance <nether\|end> cancel` | Bricht einen laufenden Countdown ab – oder öffnet eine Dimension wieder, die gerade in Wartung ist. | `dimensiontoggle.admin` |
| `/dt reload` | Lädt `config.yml`, die Sprachdatei und die GUI-Configs neu und übernimmt die Update-Checker-Einstellungen. | `dimensiontoggle.admin` |
| `/dt checkupdate` | Prüft sofort auf Modrinth nach einer neuen Version (Ergebnis in der Konsole). | `dimensiontoggle.admin` |

**Zeitangaben** (Wartung): `30s`, `5m`, `1h`, `1h30m`, `2h15m30s`.
Eine reine Zahl bedeutet Minuten (`10` = 10 Minuten). Die Zeit muss größer als 0 sein.

Tab-Completion und `/dt help` zeigen nur Commands an, für die der Spieler die
Permission hat.

---

## 5. Permissions

| Permission | Standard | Bedeutung |
|---|---|---|
| `dimensiontoggle.admin` | OP | Alle Verwaltungs-Commands und der Editor. Bekommt außerdem Update-Hinweise beim Join. |
| `dimensiontoggle.status` | alle | `/dt status` benutzen. |
| `dimensiontoggle.bypass` | OP | Darf in deaktivierte, soft-gelockte und volle Dimensionen, darf gesperrte End-Gateways benutzen und im End mit Elytra fliegen, auch wenn Elytra-Flug aus ist. Enthält beide Limit-Bypässe. |
| `dimensiontoggle.bypass.limit.nether` | OP | Darf in den Nether, auch wenn das Spielerlimit erreicht ist (Sperre und Soft-Lock gelten aber weiter). |
| `dimensiontoggle.bypass.limit.end` | OP | Dasselbe fürs End. |

**Beispiele mit LuckPerms**

```
# VIPs dürfen in den vollen Nether, aber nicht in einen gesperrten
/lp group vip permission set dimensiontoggle.bypass.limit.nether true

# Moderator soll alles verwalten, aber selbst gesperrt werden können
/lp group mod permission set dimensiontoggle.admin true
/lp group mod permission set dimensiontoggle.bypass false

# /dt status für normale Spieler ausblenden
/lp group default permission set dimensiontoggle.status false
```

---

## 6. Deaktivieren, Soft-Lock, Lockdown und Co. im Vergleich

| Modus | Neue Spieler per Portal rein? | Was passiert mit Spielern, die schon drin sind? | Auslöser |
|---|---|---|---|
| **Deaktiviert** | Nein | Bleiben drin, kommen nach dem Verlassen aber nicht wieder rein. | `/dt nether off`, Editor |
| **Soft-Lock** | Nein | Bleiben drin. Eigene Meldung „gesperrt, wer drin ist darf bleiben“; unabhängig vom An/Aus-Zustand. | `/dt softlock`, Editor |
| **Spielerlimit** | Nur bis das Limit erreicht ist | Bleiben drin. | `/dt limit`, Editor, Config |
| **Wartung** | Nach Ablauf des Countdowns nein | Werden nach Ablauf rausgebracht, dann wird die Dimension deaktiviert. | `/dt maintenance`, Editor |
| **Zeitplan** | Außerhalb der Öffnungszeit nein | Werden beim automatischen Schließen rausgebracht. | `config.yml`, Editor |
| **Lockdown** | Nein, beide Dimensionen | Werden aus beiden Dimensionen rausgebracht (abschaltbar). | `/dt lockdown`, Editor |

**„Rausbringen“** heißt: Ist ein `action-command` eingestellt, wird dieser
Command für jeden betroffenen Spieler ausgeführt (z.B. `spawn`), sonst
werden die Spieler zum Spawn der Hauptwelt teleportiert. Bei vielen Spielern
passiert das verteilt über mehrere Ticks (20 Spieler pro Tick), damit der
Server nicht laggt.

**Reihenfolge der Prüfung beim Portal-Betreten**

1. Spieler hat `dimensiontoggle.bypass` → darf immer rein.
2. Dimension deaktiviert → blockiert („Zugang deaktiviert“).
3. Soft-Lock aktiv → blockiert („gesperrt“).
4. Limit aktiv und erreicht → blockiert („voll“), außer mit `dimensiontoggle.bypass.limit.<dimension>`.

**Verlassen** einer Dimension (zurück in die Oberwelt) ist immer erlaubt.
Wer in einem gesperrten Portal stehen bleibt, bekommt die Meldung höchstens
alle 2 Sekunden.

Geprüft werden **Netherportale, Endportale und End-Gateways** – andere
Teleports (siehe [Bekannte Einschränkungen](#11-bekannte-einschränkungen))
nicht.

---

## 7. Features im Detail

### Dimension aktivieren und deaktivieren

- Per `/dt nether|end on|off` oder den großen Schalter im Control Panel.
- Alle Online-Spieler bekommen die Benachrichtigung aus `config.yml` →
  `nether.activate` / `nether.deactivate` (bzw. `end.…`) plus Sound.
- Spieler, die gerade drin sind, werden **nicht** rausgeworfen.
- Der Zustand wird in `data.yml` gespeichert und übersteht Neustarts.
- Gilt für **alle** Welten dieses Typs (z.B. auch zusätzliche Nether-Welten
  von Multiverse).

### Soft-Lock

- Neue Spieler kommen nicht rein, wer drin ist, spielt normal weiter.
- Umschalten per `/dt softlock <nether|end>` oder Editor.
- Wer benachrichtigt wird, steuert `softlock.broadcast-scope`:
  `"server"` = alle Online-Spieler, `"dimension"` = nur die Spieler in der Dimension.
  Der Auslöser bekommt immer eine Bestätigung.

### Lockdown

- `/dt lockdown` (oder der Lockdown-Button im Hauptmenü) schaltet **beide**
  Dimensionen sofort ab und bringt alle Spieler raus
  (`lockdown.kick-players: true` oder gesetzter `lockdown.action-command`).
- Alle Online-Spieler sehen die Lockdown-Meldung (`lockdown.messages.activated`).
- Der Lockdown bleibt auch nach einem Neustart aktiv, bis er aufgehoben wird.
- Während eines Lockdowns öffnet der **Zeitplan** keine Dimension (wird im Log
  als `SCHEDULE OPEN SKIPPED` vermerkt). Manuelles `/dt nether on` geht weiterhin.
- Nochmal `/dt lockdown` hebt ihn auf: **beide** Dimensionen werden wieder
  aktiviert (auch eine, die schon vor dem Lockdown aus war).

### Spielerlimit

- `limits.<dimension>.enabled` + `max-players` in der `config.yml`.
- Gezählt werden **alle** Spieler in der Dimension (auch solche mit Bypass).
- Limit `0` = niemand kommt rein (außer mit Bypass).
- `/dt limit` und die Klicks im Editor aktivieren das Limit automatisch und
  schreiben den Wert in die `config.yml` – deine Kommentare bleiben erhalten.
- **Abschalten:** in der `config.yml` `limits.<dimension>.enabled: false`
  setzen und `/dt reload` ausführen.

### Wartung

Ablauf:

1. Start per `/dt maintenance <dimension> <zeit>` oder im Editor
   (Vorgaben 5m, 10m, 30m, 1h, 2h, 6h oder eigene Zeit über den Amboss).
2. Ankündigung („schließt in …“) an die Zielgruppe aus `maintenance.broadcast-scope`
   (`"server"` = alle, `"dimension"` = nur Spieler in der Dimension).
3. Warnungen zu den Sekunden aus `maintenance.countdown.warnings`
   (Standard: 10 Min, 5 Min, 2 Min, 1 Min, 30s, 10s, 5–1s) mit Warn-Sound.
   Mit `notification: "BossBar"` läuft zusätzlich eine Bossbar live herunter.
4. Bei Ablauf: Spieler in der Dimension bekommen eine Nachricht, werden
   rausgebracht (wenn `kick-to-spawn: true` oder ein `action-command` gesetzt
   ist) und die Dimension wird **deaktiviert**.

Weitere Punkte:

- Pro Dimension kann nur ein Countdown gleichzeitig laufen.
- `/dt maintenance <dimension> cancel` bricht einen laufenden Countdown ab.
  Ist die Wartung schon durch (Dimension zu), öffnet derselbe Command die
  Dimension wieder und meldet „Wartung beendet“.
- Ein laufender Countdown wird **nicht** über einen Neustart gerettet – nach
  dem Neustart ist die Dimension weiter offen und der Countdown weg.
- `kick-to-spawn: false` **und** leerer `action-command` = Spieler bleiben
  nach Ablauf drin, kommen nach dem Verlassen aber nicht wieder rein.

### Zeitplan

Öffnet und schließt Dimensionen automatisch jeden Tag zur selben Uhrzeit.

```yaml
schedule:
  nether:
    enabled: true
    open-time: "08:00"
    close-time: "22:00"
    countdown-enabled: true
    countdown-warnings: [600, 300, 60, 10]   # Sekunden vorher
```

- Die Uhrzeit ist die **Systemzeit des Servers** (Zeitzone des Server-Rechners).
- Zur `close-time`: Spieler werden rausgebracht (`schedule.action-command`
  oder Teleport zum Spawn), die Dimension wird deaktiviert, alle bekommen
  die „geschlossen“-Meldung.
- Zur `open-time`: Dimension wird aktiviert und alle werden informiert –
  außer ein Lockdown ist aktiv.
- Vorwarnungen gehen an alle Online-Spieler (mit Sound). Mit
  `notification: "BossBar"` läuft ab der größten Warnzeit eine Countdown-Bossbar.
- Der Zeitplan reagiert nur **im Moment** des Erreichens der Uhrzeit. Startet
  der Server z.B. um 23:00 bei `close-time: "22:00"`, wird nicht nachträglich
  geschlossen. Ist die Dimension zur Uhrzeit schon im Zielzustand, passiert nichts.
- **Uhrzeit-Format:** am besten `"HH:mm"` in Anführungszeichen. `8:00`,
  `22:00` ohne Anführungszeichen und `"24:00"` (= Mitternacht) werden ebenfalls
  verstanden. Ungültige Werte werden einmalig in der Konsole gemeldet und ignoriert.

### End-Gateways

- `block-end-gateways: true` sperrt Teleports durch End-Gateways –
  **unabhängig** davon, ob das End selbst offen ist (z.B. um die äußeren Inseln
  zu sperren, während die Hauptinsel offen ist).
- Spieler mit `dimensiontoggle.bypass` sind ausgenommen.
- Umschaltbar im End-Control-Panel (schreibt direkt in die `config.yml`).

### Weitere Schalter im Control Panel

| Schalter | Dimension | Wirkung | Wo gespeichert |
|---|---|---|---|
| **Keep Inventory** | beide | Gamerule `keepInventory` der Welt(en) dieser Dimension. | In der Welt (Minecraft) |
| **Mob Spawning** | beide | Gamerule `doMobSpawning` – natürliches Spawnen an/aus. Spawner sind davon nicht betroffen. | In der Welt (Minecraft) |
| **PvP** | beide | PvP in der Dimension an/aus. Nur aktiv, wenn das Plugin **PvPManager** installiert ist, sonst ist der Button grau. | `data.yml` (wird beim Start wieder gesetzt) |
| **Elytra-Flug** | nur End | Verhindert das Starten eines Elytra-Gleitflugs im End. Bypass ausgenommen. | `data.yml` |
| **Spawner** | nur Nether | Blockiert alle Spawns aus Monster-Spawnern im Nether. | `data.yml` |

### Mob-Management

Im Control Panel → **Mob Management**. Pro Mob-Typ:

- **Klick:** Spawnen dieses Typs in dieser Dimension erlauben / verbieten.
  Verboten heißt: jede Art von Spawn wird verhindert (natürlich, Spawner,
  Spawn-Ei, Commands). Vorsicht beim **Enderdrachen** – ein Verbot verhindert
  auch das Wiederbeleben des Drachen.
- **Shift-Klick:** automatisches Aufräumen durchschalten:
  aus → alle 5 → 10 → 30 → 60 Minuten → aus. Dabei werden **alle** Entities
  dieses Typs in der Dimension entfernt (auch benannte oder gezähmte) –
  außer Reittiere, auf denen gerade ein Spieler sitzt.

| Dimension | Verwaltete Mobs |
|---|---|
| Nether | Blaze, Ghast, Magma Cube, Wither Skeleton, Zombified Piglin, Piglin, Piglin Brute, Hoglin, Zoglin, Strider, Enderman |
| End | Enderman, Shulker, Ender Dragon |

Einstellungen stehen in `data.yml`. Der Zähler bis zum nächsten Aufräumen
beginnt nach einem Neustart von vorn.

### World Border

Im Control Panel → **World Border**:

- Klick auf die Karte: Durchmesser **Links −100**, **Shift-Links −1000**,
  **Rechts +100**, **Shift-Rechts +1000**.
- Vorgaben: 1.000 / 5.000 / 10.000 / Maximum (Minecraft-Maximum knapp 60 Mio.).
- **Center at 0, 0** setzt den Mittelpunkt auf den Welt-Ursprung.
- Die Grenze wird von Minecraft in der Welt gespeichert. Bei mehreren Welten
  desselben Typs wird die erste geladene Welt verwendet.

### Dashboard und Spielerliste

- **Dashboard** (Karte im Control Panel): Spieler-Rekord der Dimension
  (wird alle 10 Sekunden geprüft und gespeichert), aktuelle World-Border-Größe,
  geladene Chunks, die 5 häufigsten Entity-Typen und die Server-TPS.
  Die Werte werden nur berechnet, solange jemand das Panel offen hat
  (höchstens alle 5 Sekunden).
- **Players Inside:** Köpfe aller Spieler in der Dimension (bis 45). Klick
  auf einen Kopf teleportiert dich zu diesem Spieler.

### Logging

- Aktiv mit `logging.enabled: true`, Datei unter `logging.file`
  (Standard `plugins/DimensionToggle/logs/dimensiontoggle.log`).
- Format:
  ```
  [2026-09-27 14:03:11] Admin -> DISABLED | Dimension: NETHER
  [2026-09-27 22:00:00] SYSTEM -> SCHEDULE CLOSED | Dimension: END | Automatically at 22:00
  ```
  `SYSTEM` = automatisch ausgelöst (z.B. durch den Zeitplan oder das Ende eines Wartungs-Countdowns).
- Geloggte Aktionen: `ENABLED`, `DISABLED`, `SOFT-LOCKED`, `SOFT-UNLOCKED`,
  `LOCKDOWN`, `LOCKDOWN LIFTED`, `LIMIT CHANGED`, `MAINTENANCE SCHEDULED`,
  `MAINTENANCE CANCELLED`, `MAINTENANCE EXECUTED`, `MAINTENANCE ENDED`,
  `SCHEDULE OPENED`, `SCHEDULE CLOSED`, `SCHEDULE OPEN SKIPPED`.

### Update-Checker

- Prüft regelmäßig auf Modrinth, ob es eine neuere Version gibt
  (`update-checker.check-interval-minutes`, mindestens 5, Standard 60).
- Neue Version → Hinweis in der Konsole und im Chat für OPs und
  `dimensiontoggle.admin`, außerdem bei jedem Join, solange die neue Version
  bekannt ist.
- `/dt checkupdate` prüft sofort. Abschalten mit `update-checker.enabled: false`.

---

## 8. Der In-Game-Editor

`/dt editor` öffnet das Hauptmenü. Countdowns, Spielerzahlen und das
Dashboard aktualisieren sich im offenen Menü jede Sekunde live.

```
Hauptmenü
├── Nether ──► Control Panel (Nether)
│               ├── Players Inside   (Spielerliste, Klick = Teleport)
│               ├── Maintenance      (Presets, eigene Zeit, Abbrechen)
│               ├── Schedule         (Öffnungs-/Schließzeit, An/Aus, Countdown)
│               ├── Mob Management   (Spawnen + Aufräumen pro Mob)
│               └── World Border     (Größe, Presets, Mittelpunkt)
├── End ─────► Control Panel (End)   (wie Nether, plus Elytra + Gateways)
├── Lockdown  (Klick = Lockdown an/aus)
└── Schließen
```

### Control Panel

| Button | Aktion |
|---|---|
| Großer Schalter (oben Mitte) | Dimension aktivieren / deaktivieren |
| Soft-Lock | Soft-Lock an/aus |
| Players Inside | Spielerliste öffnen |
| Player Limit | Links −1, Shift-Links −10, Rechts +1, Shift-Rechts +10 (aktiviert das Limit) |
| Keep Inventory | an/aus |
| Maintenance | Wartungsmenü öffnen |
| Schedule | Zeitplanmenü öffnen |
| Mob Management | Mob-Menü öffnen |
| Mob Spawning | an/aus |
| World Border | World-Border-Menü öffnen |
| PvP | an/aus (nur mit PvPManager) |
| Elytra-Flug *(End)* | an/aus |
| Gateways *(End)* | gesperrt/erlaubt |
| Spawner *(Nether)* | an/aus |
| Dashboard (Karte) | nur Anzeige |
| Zurück | Hauptmenü |

Aktive Schalter „glühen“ (Verzauberungs-Glanz), damit man den Zustand auf
einen Blick sieht.

### Wartung mit eigener Zeit (Amboss)

1. Im Wartungsmenü auf **Custom Time** klicken – ein Amboss öffnet sich.
2. Das Papier im Amboss umbenennen, z.B. in `1h30m`, `45m` oder `2h`.
3. Auf das Ergebnis rechts klicken → der Countdown startet.

Bei ungültiger Eingabe passiert nichts; einfach neu eintippen.

### Zeitplan-Menü

- Schalter oben: ganzen Zeitplan der Dimension an/aus.
- Grüne Farbstoffe: Öffnungszeit wählen, rote: Schließzeit wählen
  (06:00, 08:00, 12:00, 18:00, 20:00, 22:00, 00:00). Andere Uhrzeiten
  direkt in der `config.yml` eintragen.
- Glocke: Countdown-Warnungen an/aus.

Alles, was im Editor geändert wird, gilt sofort. Werte aus der `config.yml`
(Limit, Zeitplan, Gateways) werden direkt in die Datei geschrieben, ohne
deine Kommentare oder die Formatierung zu verändern.

---

## 9. Konfiguration

### Dateien

```
plugins/DimensionToggle/
├── config.yml                 Einstellungen + alle Broadcast-Texte
├── data.yml                   Gespeicherter Zustand (nicht von Hand bearbeiten)
├── languages/
│   ├── en/messages.yml        Command-Rückmeldungen Englisch
│   └── de/messages.yml        Command-Rückmeldungen Deutsch
├── gui/
│   ├── main/config.yml        Texte des Hauptmenüs
│   ├── nether/config.yml      Texte aller Nether-Menüs
│   └── end/config.yml         Texte aller End-Menüs
└── logs/dimensiontoggle.log   Aktions-Log
```

Nach Änderungen an Dateien: `/dt reload`.

### config.yml – Referenz

**Allgemein**

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `language` | `"en"` | Sprache der Command-Rückmeldungen (Ordnername unter `languages/`). |
| `dimensions.nether.enabled` / `dimensions.end.enabled` | `true` | Zustand beim **allerersten** Start. Danach zählt nur `data.yml`. |
| `sounds-enabled` | `true` | Sounds bei allen Benachrichtigungen an/aus. |
| `block-end-gateways` | `true` | End-Gateway-Teleports sperren. |

**`nether` / `end`** – Benachrichtigungen beim Aktivieren/Deaktivieren

- `activate` und `deactivate` sind je ein [Benachrichtigungs-Block](#benachrichtigungen).
- `portal-blocked-sound`: `sound`, `volume`, `pitch` – Sound, wenn ein Spieler am Portal abgewiesen wird.

**`maintenance`**

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `action-command` | `""` | Command (ohne `/`), der bei Ablauf für jeden Spieler in der Dimension ausgeführt wird. Leer = Teleport zum Spawn. |
| `kick-to-spawn` | `true` | Spieler bei Ablauf rausbringen. |
| `broadcast-scope` | `"server"` | `"server"` = alle, `"dimension"` = nur Spieler in der Dimension. |
| `countdown.warnings` | `[600, 300, 120, 60, 30, 10, 5, 4, 3, 2, 1]` | Sekunden vor Ablauf, zu denen gewarnt wird. |
| `notification` | `"BossBar"` | Kanal für **alle** Wartungsmeldungen. |
| `title.fadein/stay/fadeout` | `10/40/10` | Title-Timing in Ticks (20 Ticks = 1 Sekunde). |
| `bossbar.color/style/duration` | `YELLOW/PROGRESS/5` | Bossbar-Farbe, -Stil, Anzeigedauer in Sekunden. |
| `sound.warning`, `sound.execute` | – | Sound bei Warnungen / bei Ablauf (`sound`, `volume`, `pitch`). |
| `messages.<ereignis>.<kanal>` | – | Texte für `scheduled`, `warning`, `executed`, `cancelled`, `ended`. |

**`schedule`**

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `<dimension>.enabled` | `false` | Zeitplan für diese Dimension an. |
| `<dimension>.open-time` | `"08:00"` | Öffnungszeit. |
| `<dimension>.close-time` | `"22:00"` | Schließzeit. |
| `<dimension>.countdown-enabled` | `true` | Vorwarnungen an/aus. |
| `<dimension>.countdown-warnings` | `[600, 300, 60, 10]` | Sekunden vorher. |
| `action-command` | `""` | Command beim automatischen Schließen. Leer = Teleport zum Spawn. |
| `notification` | `"ActionBar"` | Kanal für alle Zeitplan-Meldungen. |
| `title`, `bossbar`, `sound.warning` | – | wie bei `maintenance`. |
| `messages.warning/opened/closed.<kanal>` | – | Texte. |

**`softlock`**

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `broadcast-scope` | `"server"` | `"server"` = alle, `"dimension"` = nur Spieler in der Dimension. |

**`lockdown`**

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `action-command` | `""` | Command für jeden Spieler in Nether/End bei Aktivierung. Leer = Teleport zum Spawn. |
| `kick-players` | `true` | Spieler bei Aktivierung rausbringen. |
| `notification` | `"Title"` | Kanal für beide Lockdown-Meldungen. |
| `title`, `bossbar` | – | Timing/Farbe wie oben. |
| `messages.activated/lifted.<kanal>` | – | Texte. |

**`limits`**

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `nether.enabled` / `nether.max-players` | `false` / `10` | Limit für den Nether. |
| `end.enabled` / `end.max-players` | `false` / `5` | Limit fürs End. |

**`logging`** – `enabled` (`true`), `file` (`"logs/dimensiontoggle.log"`).

**`update-checker`** – `enabled` (`true`), `check-interval-minutes` (`60`, mindestens 5).

### Benachrichtigungen

Jede Meldung hat Texte für **alle** Kanäle. Das Feld `notification` wählt,
welcher davon tatsächlich angezeigt wird – so kannst du den Kanal wechseln,
ohne Texte neu schreiben zu müssen.

| Wert für `notification` | Anzeige |
|---|---|
| `CHAT` | Chat-Nachricht (`chat`) |
| `ACTIONBAR` | Text über der Hotbar (`actionbar`) |
| `BOSSBAR` | Bossbar oben (`bossbar`) |
| `TITLE` | Großer Title + Untertitel (`title`, `subtitle`) |
| `NONE` | Nichts |

Groß-/Kleinschreibung ist egal (`"Title"`, `"BossBar"` usw.).

Felder eines Blocks unter `nether.activate` usw.:

| Feld | Bedeutung |
|---|---|
| `notification` | Kanal (siehe oben) |
| `title`, `subtitle`, `chat`, `actionbar`, `bossbar` | Texte je Kanal |
| `fadein`, `stay`, `fadeout` | Title-Timing in Ticks |
| `bossbar-color` | `PINK`, `BLUE`, `RED`, `GREEN`, `YELLOW`, `PURPLE`, `WHITE` |
| `bossbar-style` | `PROGRESS`, `NOTCHED_6`, `NOTCHED_10`, `NOTCHED_12`, `NOTCHED_20` |
| `bossbar-duration` | Anzeigedauer der Bossbar in Sekunden |
| `sound`, `sound-volume`, `sound-pitch` | Sound als Bukkit-Name, z.B. `ENTITY_PLAYER_LEVELUP` |

### Farben und Formatierung

In allen Texten (config.yml, messages.yml, GUI-Configs) funktionieren – auch gemischt:

- Klassische Codes: `&a` grün, `&c` rot, `&l` fett, `&o` kursiv, `&r` zurücksetzen usw.
- Hex-Farben: `&#FF8800`
- MiniMessage: `<gradient:#A78BFA:#7C3AED>Text</gradient>`, `<bold>`, `<gray>` …
- Small Caps einfach als Unicode-Zeichen: `ᴀᴅᴍɪɴ ᴇᴅɪᴛᴏʀ`

Bei GUI-Item-Namen und -Lore wird die Minecraft-Standard-Kursivschrift automatisch entfernt.

### Platzhalter

| Platzhalter | Wo | Inhalt |
|---|---|---|
| `%dimension%` | fast überall | Name der Dimension aus `messages.yml` → `names` |
| `%duration%` | Wartung (`scheduled`), Command-Rückmeldung | Gesamtdauer, z.B. „10 Minuten“ |
| `%time%` | Wartungs-/Zeitplan-Warnungen | Restzeit |
| `%action%` | Zeitplan-Warnung | „öffnet“ / „schließt“ (`action-opens` / `action-closes`) |
| `%limit%` | Limit-Meldungen | Spielerlimit |
| `%status%`, `%players%` | `/dt status` | Zustand, Spielerzahl |
| `%seconds%` | Status „Wartung läuft“ | Restsekunden |
| `%error%` | Reload-Fehler | Fehlermeldung |
| `%version%`, `%current%`, `%behind%`, `%count%` | Update-Hinweis | neue / aktuelle Version, Versionen Rückstand |

In den GUI-Configs sind die jeweils verfügbaren Platzhalter direkt an den Texten
erkennbar (`%count%`, `%limit-status%`, `%peak%`, `%size%`, `%tps%` usw.).

### Sprachen

- `language: "de"` oder `"en"` in der `config.yml`, danach `/dt reload`.
- Die Sprachdatei enthält alle Command-Rückmeldungen, `/dt help`, `/dt status`,
  Portal-Meldungen und die Dimensionsnamen (`names.nether`, `names.end`).
- **Eigene Sprache:** Ordner `languages/de` kopieren (z.B. nach `languages/fr`),
  übersetzen, `language: "fr"` setzen, `/dt reload`. Fehlt der Ordner, wird
  Englisch verwendet (mit Warnung in der Konsole). Fehlt ein einzelner
  Schlüssel, erscheint im Spiel `[Fehlende Nachricht: …]`.
- Die Broadcast-Texte für An/Aus, Wartung, Zeitplan und Lockdown stehen nicht
  in der Sprachdatei, sondern in der `config.yml` direkt neben ihren
  Einstellungen – dort übersetzen.

### GUI-Texte

Alle Titel, Item-Namen und Lore-Zeilen des Editors stehen in `gui/main`,
`gui/nether` und `gui/end` (`config.yml`). Nether und End haben getrennte
Dateien, damit jede Dimension eigene Farben haben kann. Nach Änderungen
`/dt reload` und das Menü neu öffnen.

### data.yml

Hier speichert das Plugin seinen Zustand – nicht von Hand bearbeiten, während
der Server läuft:

| Schlüssel | Inhalt |
|---|---|
| `dimensions.<dimension>.enabled` / `.locked` | An/Aus, Soft-Lock |
| `lockdown.active` | Lockdown aktiv |
| `peak-players.<dimension>` | Spieler-Rekord |
| `mob-management.<dimension>.<MOB>.enabled` / `.cleanup-minutes` | Mob-Management |
| `nether.spawners-enabled`, `end.elytra-fly-enabled` | Schalter |
| `pvp.<dimension>` | PvP-Einstellung (nur wenn im Editor geändert) |

Gespeichert wird absturzsicher im Hintergrund. Ist die Datei trotzdem einmal
unlesbar, benennt das Plugin sie in `data.yml.corrupt-<Datum>-<Uhrzeit>` um,
meldet das in der Konsole und startet mit Standardwerten – die alte Datei
bleibt zur Wiederherstellung erhalten.

### Config-Updates

- Beim Start werden fehlende **Hauptabschnitte** aus der neuen Standard-Config
  am Ende deiner Datei ergänzt (mit einem Hinweis-Kommentar darüber).
- Bestehende Werte werden nie verändert.
- Neue Optionen **innerhalb** vorhandener Abschnitte werden nicht automatisch
  ergänzt. Fehlt eine Option, nutzt das Plugin einen eingebauten Standardwert –
  zum Anpassen die Zeile aus der Standard-Config der neuen Version übernehmen.

---

## 10. FAQ und Fehlerbehebung

**Ich komme trotz deaktiviertem Nether rein.**
OPs haben standardmäßig `dimensiontoggle.bypass`. Mit einem Nicht-OP-Account
testen oder die Permission entziehen:
`/lp user <name> permission set dimensiontoggle.bypass false`.

**Spieler kommen per `/home`, `/tp` oder `/warp` in eine gesperrte Dimension.**
Das Plugin sperrt Portale und End-Gateways, keine Teleports anderer Plugins
(siehe [Bekannte Einschränkungen](#11-bekannte-einschränkungen)). Homes/Warps
in Nether/End ggf. im jeweiligen Plugin einschränken.

**Der Zeitplan schaltet nicht.**
- Ist `schedule.<dimension>.enabled: true`?
- Stimmt die Uhrzeit mit der **Server**-Zeit überein (Zeitzone des Hosts)?
- Steht eine Warnung „Invalid time …“ in der Konsole?
- Ist ein Lockdown aktiv? Dann wird nicht automatisch geöffnet.
- War die Dimension schon im Zielzustand? Dann passiert nichts.

**Der PvP-Button ist grau („Unavailable“).**
Er ist nur aktiv, wenn das Plugin **PvPManager** installiert ist.

**Der Action-Command funktioniert nicht.**
Er wird ohne `/` eingetragen und **als der Spieler** ausgeführt – der Spieler
braucht also die Permission für diesen Command (z.B. `essentials.spawn`).

**Ich kann das Spielerlimit im Editor nicht ausschalten.**
Im Editor kann man es nur verstellen. Zum Abschalten in der `config.yml`
`limits.<dimension>.enabled: false` setzen und `/dt reload`.

**Meine Textänderungen erscheinen nicht.**
`/dt reload` ausführen. GUI-Texte stehen in `gui/…/config.yml`, Broadcasts in
der `config.yml`, Command-Texte in `languages/<sprache>/messages.yml`.

**Nach einem Update fehlt eine neue Option in meiner config.yml.**
Siehe [Config-Updates](#config-updates) – Optionen in bestehenden Abschnitten
von Hand aus der Standard-Config übernehmen.

**In der Konsole steht „data.yml could not be read … moved it to data.yml.corrupt-…“.**
Die Datei war beschädigt (z.B. von Hand falsch bearbeitet). Das Plugin läuft
mit Standardwerten weiter; die alte Datei liegt daneben und kann repariert
und zurückbenannt werden (Server vorher stoppen).

**Der Update-Checker meldet „status 404“.**
Das Modrinth-Projekt ist (noch) nicht erreichbar. Mit
`update-checker.enabled: false` abschalten.

**Wartung war nach einem Neustart weg.**
Laufende Countdowns werden nicht gespeichert – nach dem Neustart neu starten.

---

## 11. Bekannte Einschränkungen

- Nur **Portale** (Nether-/Endportal) und **End-Gateways** werden geprüft.
  Teleports per Command oder durch andere Plugins, Einloggen an einer Position
  in einer gesperrten Dimension und Respawn per Seelenanker werden nicht blockiert.
- Das Aufheben des Lockdowns aktiviert **beide** Dimensionen, auch wenn eine
  davon vorher schon deaktiviert war.
- Mob-Aufräumen entfernt alle Entities des gewählten Typs, auch benannte oder
  gezähmte (nur gerittene Reittiere werden verschont).
- Laufende Wartungs-Countdowns überstehen keinen Neustart.
- Config-Updates ergänzen nur neue Hauptabschnitte automatisch.
- Folia wird nicht unterstützt.

---

## 12. Für Entwickler

**Bauen**

```
mvn clean package
```

Ergebnis: `target/DimensionToggle-<version>.jar`. Benötigt Java 21 und Zugriff
auf das PaperMC-Maven-Repository (`repo.papermc.io`).

**Tests**

```
mvn test
```

Automatisierte Tests mit JUnit 5 und MockBukkit (`src/test`): Zeit- und
Dauer-Parsing, Zeitplan, Persistenz, GUI, Portal-Listener, Commands,
verteiltes Rausbringen von Spielern.

**Aufbau** (Paket `net.dimensiontoggle`)

| Paket | Inhalt |
|---|---|
| `command` | `/dt` – alle Subcommands in einer Registry (Hilfe + Tab-Completion) |
| `listener` | Portal- und Gateway-Sperren |
| `manager` | Ein Manager pro Feature (Dimension, Wartung, Zeitplan, Benachrichtigungen …) |
| `gui` | Editor – jede Aktion steckt als Text im Item, nicht in der Slot-Nummer |
| `config` | Laden, Update-Merge und kommentarerhaltendes Schreiben der YAML-Dateien |
| `io` | Hintergrund-Thread und absturzsicheres Schreiben für alle Dateien |
| `model` | `ToggleDimension` (NETHER/END), Benachrichtigungs-Kanäle |

Mehr Details zur Architektur und den Konventionen stehen in der
[CLAUDE.md](CLAUDE.md).

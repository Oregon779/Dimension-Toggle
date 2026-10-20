# DimensionToggle – Wiki

Mit **DimensionToggle** steuerst du auf deinem Paper-Server, wer wann in den
**Nether** und ins **End** darf: an/aus schalten, Wartung mit Countdown,
tägliche Öffnungszeiten, Notfall-Lockdown, Spielerlimits und vieles mehr –
per Command oder komplett per Klick im In-Game-Editor.

> Dieses Wiki beschreibt den aktuellen Stand des Plugins (Version 3.3.0, in Entwicklung).
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
| **Overworld & Performance** | Mob-Kontrolle für die Overworld, Farm-Limiter, Entity-Cap, Villager-Optimierer, Item-Aufräumer, AFK-Sichtweite, Auto-Performance-Modus. |
| **Lag-Werkzeuge** | `/lagmap` (vollste Chunks), Redstone-Meter, Chunk-Loader-Melder – jeweils mit Klick-Teleport. |
| **Performance-Website** | Live-TPS/MSPT, Speicher, Welten und Verlauf im Browser (`/dt web`). |
| **Integrationen** | PlaceholderAPI-Platzhalter, bStats. |
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

- **Paper 26.2** (oder Purpur auf Basis von 26.2). Spigot/CraftBukkit wird nicht unterstützt, das Plugin nutzt die Paper-API.
- **Java 25** (benötigt Paper 26 sowieso)
- Kein **Folia** (nicht unterstützt)
- Optional: **PvPManager** – nur nötig, damit der PvP-Schalter im Editor freigeschaltet ist

**Installation**

1. `DimensionToggle-<version>.jar` in den Ordner `plugins/` legen.
2. Server starten. Das Plugin legt `plugins/DimensionToggle/` mit allen Dateien an.
3. Optional: in `config.yml` die Sprache setzen (`language: "de"`) und `/dt reload` ausführen.

**Update auf eine neue Version**

1. Alte Jar durch die neue ersetzen, Server neu starten.
2. Neue Optionen werden automatisch in deine `config.yml`, die Sprachdateien
   und die GUI-Configs eingefügt – auch innerhalb vorhandener Abschnitte, an
   der passenden Stelle und mit Kommentar. Deine Werte, Kommentare und deine
   Formatierung bleiben erhalten, vorher wird ein Backup angelegt (siehe
   [Config-Updates](#config-updates)).

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
| `/dt lagmap` (= `/lagmap`) | Menü mit den vollsten Chunks aller Welten. Klick = Teleport, Shift-Klick (2×) = aufräumen. | `dimensiontoggle.lagmap` |
| `/dt lagmap chat [score\|entities\|mobs\|items\|blocks]` | Dieselbe Liste als klickbarer Chat-Text, sortiert nach der gewählten Spalte (geht auch in der Konsole). | `dimensiontoggle.lagmap` |
| `/dt redstone [chat]` | Chunks mit dem schnellsten Redstone der letzten 20 Sekunden. | `dimensiontoggle.redstone` |
| `/dt chunkloaders [chat]` | Chunks, die geladen sind, obwohl kein Spieler in der Nähe ist – mit Grund. | `dimensiontoggle.chunkloaders` |
| `/dt web` | Link zur Performance-Website (anklickbar). | `dimensiontoggle.performance` |
| `/dt web reset-token` | Neuen geheimen Website-Token erzeugen – alte Links funktionieren dann nicht mehr. | `dimensiontoggle.performance` |

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
| `dimensiontoggle.lagmap` | OP | `/lagmap`: vollste Chunks ansehen, hinteleportieren, aufräumen. |
| `dimensiontoggle.redstone` | OP | `/dt redstone` |
| `dimensiontoggle.chunkloaders` | OP | `/dt chunkloaders` |
| `dimensiontoggle.performance` | OP | `/dt web` (Link zur Website) |
| `dimensiontoggle.performance.notify` | OP | Bekommt Redstone- und Auto-Performance-Warnungen im Chat. |
| `dimensiontoggle.afk.bypass` | niemand | Sichtweite wird bei AFK nie gesenkt. |

`dimensiontoggle.admin` enthält alle Lag- und Performance-Rechte (außer dem AFK-Bypass).
Moderatoren kannst du gezielt nur die Lag-Werkzeuge geben, z.B.
`/lp group mod permission set dimensiontoggle.lagmap true`.

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
Command für jeden betroffenen Spieler ausgeführt, sonst werden die Spieler
zum Spawn der Hauptwelt teleportiert.

- `spawn` → wird **als der Spieler** ausgeführt (er braucht die Permission dafür).
- `spawn %player%` → wird **von der Konsole** für jeden Spieler ausgeführt
  (empfohlen: keine Permission und keine Teleport-Verzögerung nötig).
- **Sicherheitsnetz:** Wer 5 Sekunden später immer noch in der geschlossenen
  Dimension ist (Command unbekannt, fehlende Rechte, abgebrochene
  Teleport-Verzögerung …), wird automatisch zum Spawn teleportiert. Das gilt
  auch für Spieler, die in eine bereits deaktivierte Dimension gelangt sind.

Bei vielen Spielern passiert das verteilt über mehrere Ticks (20 Spieler pro
Tick), damit der Server nicht laggt.

**Reihenfolge der Prüfung beim Portal-Betreten**

1. Spieler hat `dimensiontoggle.bypass` → darf immer rein.
2. Dimension deaktiviert → blockiert („Zugang deaktiviert“).
3. Soft-Lock aktiv → blockiert („gesperrt“).
4. Limit aktiv und erreicht → blockiert („voll“), außer mit `dimensiontoggle.bypass.limit.<dimension>`.

**Verlassen** einer Dimension (zurück in die Oberwelt) ist immer erlaubt.
Wer in einem gesperrten Portal stehen bleibt, bekommt die Meldung höchstens
alle 2 Sekunden.

Geprüft werden **Netherportale, Endportale und End-Gateways** – auch wenn
der Spieler in einem Boot, einer Lore oder auf einem Tier sitzt. Sitzt ein
Spieler ohne Zugang im Fahrzeug, bleibt das ganze Fahrzeug stehen; mehrere
Mitfahrer zählen gemeinsam fürs Spielerlimit.

**Enderperlen** werden genauso geprüft: Eine Perle, die in ein gesperrtes
Portal (oder ein gesperrtes End-Gateway) geworfen wird, fliegt nicht hindurch,
sondern landet davor wie an einer Wand – der Spieler bekommt dieselbe Meldung
wie beim Durchlaufen. Kommt eine Perle trotzdem auf die andere Seite (z.B.
durch ein anderes Plugin), wird der Spieler beim Landen nicht in die gesperrte
Dimension teleportiert; die Perle ist dann verbraucht. Andere Teleports (siehe [Bekannte Einschränkungen](#11-bekannte-einschränkungen))
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
- Gilt auch für Enderperlen, die durch ein Gateway geworfen werden, und für
  Spieler in Fahrzeugen.
- Spieler mit `dimensiontoggle.bypass` sind ausgenommen.
- Umschaltbar im End-Control-Panel (schreibt direkt in die `config.yml`).

### Weitere Schalter im Control Panel

| Schalter | Dimension | Wirkung | Wo gespeichert |
|---|---|---|---|
| **Keep Inventory** | beide | Gamerule `keep_inventory` der Welt(en) dieser Dimension. | In der Welt (Minecraft) |
| **Mob Spawning** | beide | Gamerule `spawn_mobs` – natürliches Spawnen an/aus. Spawner sind davon nicht betroffen. | In der Welt (Minecraft) |
| **PvP** | beide | Gamerule `pvp` der Welt(en) dieser Dimension. Nur aktiv, wenn das Plugin **PvPManager** installiert ist, sonst ist der Button grau. | In der Welt (Minecraft) |
| **Elytra-Flug** | nur End | Verhindert das Starten eines Elytra-Gleitflugs im End. Bypass ausgenommen. | `data.yml` |
| **Spawner** | nur Nether | Blockiert alle Spawns aus Monster-Spawnern im Nether. | `data.yml` |

### Mob-Management

Im Control Panel → **Mob Management**. Pro Mob-Typ:

- **Klick:** Spawnen dieses Typs in dieser Dimension erlauben / verbieten.
  Verboten heißt: jede Art von Spawn wird verhindert (natürlich, Spawner,
  Spawn-Ei, Commands). Vorsicht beim **Enderdrachen** – ein Verbot verhindert
  auch das Wiederbeleben des Drachen.
- **Shift-Klick:** automatisches Aufräumen durchschalten:
  aus → alle 5 → 10 → 30 → 60 Minuten → aus.
- **Rechtsklick:** eigenes Aufräum-Intervall im Chat eingeben (siehe
  [Eigene Zeiten im Chat](#eigene-zeiten-im-chat)), 1 Minute bis 24 Stunden,
  `0` = aus.
- Beim Aufräumen werden alle Entities dieses Typs in der Dimension entfernt –
  **außer** Mobs mit Namensschild, gezähmten Tieren, Dorfbewohnern mit Beruf
  (alle drei einzeln abschaltbar unter `mob-management.protect-*`) und
  Reittieren, auf denen gerade ein Spieler sitzt.

| Dimension | Verwaltete Mobs |
|---|---|
| Nether | Blaze, Ghast, Magma Cube, Wither Skeleton, Zombified Piglin, Piglin, Piglin Brute, Hoglin, Zoglin, Strider, Enderman |
| End | Enderman, Shulker, Ender Dragon |
| Overworld | alle 76 Lebewesen der Overworld in 4 Gruppen (siehe [Overworld & Performance](#overworld--performance)) |

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

### Overworld & Performance

Im Editor über den **Grasblock** im Hauptmenü erreichbar. Die Overworld
selbst kann nicht gesperrt werden – hier geht es um Mobs und Performance.
**Alles, was das Spiel verändert, ist standardmäßig aus.**

**Mob-Kontrolle Overworld**
- *Mob Spawning*: Gamerule `spawn_mobs` der Overworld an/aus.
- *Mob Management*: wie bei Nether/End, für **alle Lebewesen der Overworld**.
  Zuerst eine Gruppe wählen, darin mit den Pfeilen unten blättern:
  - **Monster** (25): Zombie, Wüstenzombie, Ertrunkener, Zombiedorfbewohner,
    Skelett, Eiswanderer, Sumpfskelett, Parched, Creeper, Spinne, Höhlenspinne,
    Enderman, Endermite, Hexe, Schleim, Schwefelwürfel, Phantom,
    Silberfischchen, Plünderer, Diener, Magier, Plagegeist, Verwüster, Breeze,
    Knarz.
  - **Tiere** (29, 2 Seiten): Kuh, Pilzkuh, Schwein, Schaf, Huhn, Kaninchen,
    Pferd, Esel, Maultier, Skelett- und Zombiepferd, Kamel, Kamel-Husk, Lama,
    Händlerlama, Ziege, Wolf, Katze, Ozelot, Fuchs, Panda, Eisbär, Papagei,
    Biene, Fledermaus, Frosch, Gürteltier, Schnüffler, Glücklicher Ghast.
  - **Wasser** (14): Kabeljau, Lachs, Kugelfisch, Tropenfisch, Tintenfisch,
    Leuchttintenfisch, Delfin, Schildkröte, Axolotl, Kaulquappe, Nautilus,
    Zombie-Nautilus, Wächter, Großer Wächter.
  - **Sonstige** (8): Dorfbewohner, Fahrender Händler, Eisengolem,
    Schneegolem, Kupfergolem, Hilfsgeist, Warden, Wither.

  Klick = Spawnen erlauben/verbieten, Shift-Klick = Aufräum-Presets,
  Rechtsklick = eigenes Intervall im Chat. Standardmäßig ist alles erlaubt.
  Vorsicht: Ein Verbot gilt für jede Spawn-Art – z.B. verhindert ein Verbot
  von *Eisengolem* auch Dorf- und gebaute Golems, eins von *Wither* das Bauen
  des Withers.

**Farm-Limiter** (`farm-limiter`)
- Sucht alle 30 s nach großen Tiergruppen: ab `group-size` (Standard 20)
  Tieren **derselben Art in einem Chunk** werden KI und Kollision dieser Tiere
  abgeschaltet. Sie stehen dann still und schieben sich nicht gegenseitig.
- **Schlägt ein Spieler eines der Tiere**, bekommt genau dieses Tier seine KI
  zurück (Töten/Ernten funktioniert also normal).
- Benannte, gezähmte, angeleinte und gerittene Tiere werden nie eingefroren.
- Ausschalten weckt alle eingefrorenen Tiere wieder auf (auch in später
  geladenen Chunks). **Vor dem Entfernen des Plugins ausschalten** – sonst
  bleiben eingefrorene Tiere eingefroren.
- Eingefrorene Tiere laufen nicht von selbst zum Partner – zum Züchten erst
  einmal schlagen.

**Entity-Cap** (`entity-cap`)
- Höchstens `max-per-chunk` (Standard 40) Tiere bzw. Dorfbewohner **eines
  Typs** pro Chunk. Darüber hinaus wird Züchten und Spawnen (Eier, Spawner,
  natürlich) blockiert; der Züchter bekommt eine Meldung.
- Spawns per Command oder durch andere Plugins werden nie blockiert.

**Villager-Optimierer** (`villager-optimizer`)
- Dorfbewohner, die sich `idle-minutes` lang (Standard 5) nicht bewegt haben –
  typisch für Handelshallen –, verlieren ihre KI. **Handeln funktioniert
  weiter**, und sie werden zweimal pro Minecraft-Tag automatisch aufgefüllt.
- Benannte und angeleinte Dorfbewohner bleiben unverändert. Ein Schlag gibt
  die KI zurück; Ausschalten weckt alle auf.

**Item-Aufräumer** (`item-cleaner`)
- Alle `interval-minutes` (Standard 10) werden Items entfernt, die mindestens
  `min-age-seconds` (Standard 60) am Boden liegen – mit Chat-Warnungen vorher
  (Standard 60/30/10 s). XP-Kugeln nur mit `include-xp-orbs: true`.
- **Rechtsklick** auf den Item-Aufräumer im Overworld-Panel: eigenes
  Intervall im Chat eingeben (1 Minute bis 24 Stunden). Eine Zeit schaltet den
  Aufräumer gleich ein, `0`/`aus` schaltet ihn aus (das letzte Intervall
  bleibt gespeichert). Linksklick schaltet wie bisher an/aus.

**AFK-Sichtweite** (`afk-view`)
- Nach `afk-minutes` (Standard 5) ohne Aktivität sinkt die Sichtweite des
  Spielers auf `view-distance` (Standard 2). Bewegen, Umschauen, Chat, Commands
  oder Klicks stellen sie sofort wieder her.
- `simulation-distance: -1` (Standard) lässt die Simulationsdistanz in Ruhe –
  AFK-Farmen laufen weiter. Ein Wert ≥ 2 senkt auch sie (dann stehen Farmen
  in der Nähe des AFK-Spielers still).
- Bypass: `dimensiontoggle.afk.bypass`.

**Auto-Performance-Modus** (`auto-performance`)
- Liegt die Server-Last 60 s lang über `activate-mspt` (Standard 45 ms pro
  Tick; 50 ms = Grenze für 20 TPS), schaltet sich der Modus ein:
  AFK-Sichtweite (nach `afk-minutes` = 2) und Farm-Limiter (ab
  `farm-group-size` = 10) laufen dann **auch wenn sie ausgeschaltet sind**.
- Bleibt die Last 120 s unter `recover-mspt` (Standard 35), geht alles zurück
  in den Normalzustand. Staff mit `dimensiontoggle.performance.notify` wird
  jeweils informiert.

### Lag-Werkzeuge

Alle drei durchsuchen **alle Welten**, zeigen die Treffer im Menü (Klick =
Teleport in die Chunk-Mitte) oder mit `chat` als klickbare Liste.

**`/lagmap`** – Top 10 der vollsten Chunks mit Entities, Mobs, Items am Boden,
Hoppern und anderen Block-Entities. Standard-Sortierung ist der **Lag-Score**
(gewichtete Summe, Gewichte unter `lagmap.weights`; Mobs und Hopper zählen
mehr). Im Menü per Knopf umsortierbar. **Shift-Klick auf einen Chunk, dann
nochmal Shift-Klick** innerhalb von 5 s räumt ihn auf: Items, XP und Mobs –
aber keine benannten, gezähmten oder angeleinten Mobs und keine Dorfbewohner.

**`/dt redstone`** – zählt Redstone-Signaländerungen und Kolbenbewegungen pro
Chunk in einem 20-Sekunden-Fenster und zeigt die schnellsten Chunks
(Updates pro Sekunde). Ab `redstone-meter.alert-per-second` (Standard 100)
bekommt Staff eine klickbare Warnung (höchstens alle 5 Minuten pro Chunk).

**`/dt chunkloaders`** – Chunks, die *tickend* geladen sind, obwohl kein
Spieler in Sichtweite ist. Als Grund wird angezeigt: `/forceload`,
ein Plugin (mit Namen), eine Enderperle oder „unbekannt“ (meist Portal- oder
Entity-Lader).

### Performance-Website

Eine kleine, nur lesende Webseite im dunklen Design mit TPS (1/5/15 min),
MSPT inkl. Spitze, Spielern, Arbeitsspeicher, CPU, Laufzeit, Status von
Nether/End, allen Welten (Spieler, Chunks, Entities, Block-Entities) und den
vollsten Chunks. Jede Karte zeigt mit Farbe **und** Symbol, wie es steht
(● flüssig, ▲ ausgelastet, ✖ Lag). Zwei Diagramme zeigen MSPT und TPS der
letzten Stunde – mit der Maus (oder dem Finger) darüberfahren zeigt den Wert
zu jedem Zeitpunkt. Funktioniert auch auf dem Handy. Sprache folgt der
Plugin-Sprache, die Seite lädt sich alle 5 Sekunden neu.

**Einrichten:**
1. In der `config.yml` `web.enabled: true` setzen und einen freien `web.port`
   wählen (Standard 8105).
2. Bei gemieteten Servern (Pterodactyl o.ä.) den Port im Panel als
   zusätzlichen Port freigeben.
3. Optional `web.public-url` setzen, z.B. `"http://play.meinserver.de:8105"` –
   diese Adresse zeigt `/dt web` an.
4. `/dt reload`, dann `/dt web` → Link anklicken.

Der Link enthält einen **geheimen Token**; ohne ihn ist die Seite gesperrt
(`web.require-token: false` macht sie öffentlich). `/dt web reset-token`
erzeugt einen neuen Token, falls ein Link in falsche Hände geraten ist.
Die Seite kann nichts verändern – sie zeigt nur an.

### PlaceholderAPI

Ist PlaceholderAPI installiert, gibt es diese Platzhalter (z.B. für
Scoreboard oder Tablist):

| Platzhalter | Wert |
|---|---|
| `%dimensiontoggle_nether_status%` / `_end_status` | Text aus `messages.yml` (`placeholder-enabled/disabled/locked/maintenance`) |
| `%dimensiontoggle_nether_enabled%` / `_end_enabled` | `true` / `false` |
| `%dimensiontoggle_nether_locked%` / `_end_locked` | `true` / `false` |
| `%dimensiontoggle_nether_players%` / `_end_players` | Spieler in der Dimension |
| `%dimensiontoggle_nether_limit%` / `_end_limit` | Spielerlimit oder `-` |
| `%dimensiontoggle_nether_maintenance%` / `_end_maintenance` | Sekunden bis zur Wartungs-Schließung (0 = keine) |
| `%dimensiontoggle_lockdown%` | `true` / `false` |
| `%dimensiontoggle_tps%` / `%dimensiontoggle_mspt%` | aktuelle TPS / ms pro Tick |

### bStats

Das Plugin unterstützt anonyme Nutzungsstatistiken über bStats (Anzahl
Server/Spieler, Sprache, welche Features genutzt werden). Abschalten geht wie
bei jedem Plugin in `plugins/bStats/config.yml`.

---

## 8. Der In-Game-Editor

`/dt editor` öffnet das Hauptmenü. Countdowns, Spielerzahlen und das
Dashboard aktualisieren sich im offenen Menü jede Sekunde live.

**Aussehen:** Jeder Bereich hat seine Farbe – Hauptmenü lila, Nether
rot/orange, End violett, Overworld grün. Der Rahmen ist in dieser Farbe (mit
Akzent-Ecken), das Innere dunkel. Jeder Button nennt oben klein seine Gruppe
(z.B. ZUGANG, REGELN), zeigt seinen Zustand mit ● grün = an / ● rot = aus
und am Ende in Gelb, was ein Klick macht. Aktive Schalter glühen zusätzlich.
Klicks haben einen kurzen Sound (an, aus, Fehler – einstellbar unter
`gui-sounds` in der `config.yml`).

```
Hauptmenü
├── Nether ──► Control Panel (Nether)
│               ├── Players Inside   (Spielerliste, Klick = Teleport)
│               ├── Maintenance      (Presets, eigene Zeit, Abbrechen)
│               ├── Schedule         (Öffnungs-/Schließzeit, An/Aus, Countdown)
│               ├── Mob Management   (Spawnen + Aufräumen pro Mob)
│               └── World Border     (Größe, Presets, Mittelpunkt)
├── End ─────► Control Panel (End)   (wie Nether, plus Elytra + Gateways)
├── Overworld ► Overworld & Performance
│               ├── Mob Spawning / Mob Management (Overworld)
│               │       └── Monster · Tiere · Wasser · Sonstige (blätterbar)
│               ├── Farm-Limiter, Entity-Cap, Villager-Optimierer,
│               │   Item-Aufräumer, AFK-Sichtweite, Auto-Performance (an/aus)
│               ├── Lagmap · Redstone · Chunk-Loader (Klick = Teleport)
│               ├── Website (Link im Chat)
│               └── Server-Performance (TPS, MSPT, RAM, Chunks, Entities)
├── Server    (Spieler online, TPS, MSPT, Plugin-Version – nur Anzeige)
├── Lockdown  (Klick = Lockdown an/aus)
└── Schließen
```

### Control Panel

Aufbau (5 Reihen): oben die Übersicht (Karte), Reihe 1 **Zugang** (Schalter,
Soft-Lock, Spielerlimit, Spieler drinnen) und **Automatik** (Wartung,
Zeitplan), Reihe 2 **Regeln** (Keep Inventory, PvP, Mob Spawning, Spawner bzw.
Elytra + Gateways), Reihe 3 **Verwaltung** (Mob Management, World Border),
unten „Zurück“.

| Button | Aktion |
|---|---|
| Schalter (Reihe 1 links) | Dimension aktivieren / deaktivieren |
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
| Übersicht (Karte, oben) | nur Anzeige |
| Zurück | Hauptmenü |

Aktive Schalter „glühen“ (Verzauberungs-Glanz), damit man den Zustand auf
einen Blick sieht.

### Eigene Zeiten im Chat

Für das Aufräum-Intervall eines Mobs (**Rechtsklick** im Mob-Menü) und für den
Item-Aufräumer (**Rechtsklick** im Overworld-Panel):

1. Das Menü schließt sich, im Chat steht, was gerade eingestellt ist.
2. Die neue Zeit in den Chat schreiben: `15` oder `15m` (Minuten), `2h`,
   `1h30m` – erlaubt sind **1 Minute bis 24 Stunden**. `0`, `aus` oder `off`
   schaltet aus, `abbrechen` oder `cancel` bricht ab.
3. Danach öffnet sich das Menü, aus dem man kam, wieder.

Die Eingabe landet **nicht** im öffentlichen Chat. Bei einer ungültigen
Eingabe kommt ein Hinweis und man kann es direkt nochmal versuchen. Nach 30
Sekunden ohne Eingabe wird abgebrochen (es ändert sich nichts).

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
│   ├── end/config.yml         Texte aller End-Menüs
│   └── overworld/config.yml   Overworld-Panel und Lag-Berichte
├── config-state.yml           Stand der Config-Updates (nicht von Hand bearbeiten)
├── backups/                   Sicherungen vor automatischen Config-Updates
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
| `gui-sounds.enabled` | `true` | Klick-Sounds im Editor an/aus. |
| `gui-sounds.click` / `toggle-on` / `toggle-off` / `error` | `UI_BUTTON_CLICK` / `BLOCK_NOTE_BLOCK_PLING` / `BLOCK_NOTE_BLOCK_BASS` / `ENTITY_VILLAGER_NO` | Je `sound`, `volume`, `pitch`: normaler Klick, etwas eingeschaltet, ausgeschaltet, Fehler (z.B. ungültige Eingabe). |
| `mob-management.protect-named` | `true` | Automatisches Mob-Aufräumen lässt Mobs mit Namensschild stehen. |
| `mob-management.protect-tamed` | `true` | … lässt gezähmte Tiere stehen. |
| `mob-management.protect-villagers-with-profession` | `true` | … lässt Dorfbewohner mit Beruf stehen (Arbeitslose und Nichtsnutze werden entfernt). |
| `block-end-gateways` | `true` | End-Gateway-Teleports sperren. |

**`nether` / `end`** – Benachrichtigungen beim Aktivieren/Deaktivieren

- `activate` und `deactivate` sind je ein [Benachrichtigungs-Block](#benachrichtigungen).
- `portal-blocked-sound`: `sound`, `volume`, `pitch` – Sound, wenn ein Spieler am Portal abgewiesen wird.

**`maintenance`**

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `action-command` | `""` | Command (ohne `/`), der bei Ablauf für jeden Spieler in der Dimension ausgeführt wird. Mit `%player%` läuft er über die Konsole. Leer = Teleport zum Spawn. |
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
| `action-command` | `""` | Command beim automatischen Schließen (mit `%player%` über die Konsole). Leer = Teleport zum Spawn. |
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
| `action-command` | `""` | Command für jeden Spieler in Nether/End bei Aktivierung (mit `%player%` über die Konsole). Leer = Teleport zum Spawn. |
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

**Overworld & Performance**

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `lagmap.top` | `10` | Wie viele Chunks `/lagmap` zeigt (Menü max. 14). |
| `lagmap.weights.mob/item/other-entity/hopper/block-entity` | `3/1/1/4/1` | Gewichte für den Lag-Score. |
| `farm-limiter.enabled` | `false` | Farm-Limiter an. |
| `farm-limiter.group-size` | `20` | Ab so vielen Tieren einer Art pro Chunk wird eingefroren. |
| `farm-limiter.check-interval-seconds` | `30` | Wie oft gesucht wird (min. 5). |
| `entity-cap.enabled` / `max-per-chunk` | `false` / `40` | Entity-Cap an, Obergrenze pro Typ und Chunk. |
| `villager-optimizer.enabled` / `idle-minutes` | `false` / `5` | Villager-Optimierer an, Stillstandszeit. |
| `item-cleaner.enabled` | `false` | Item-Aufräumer an. |
| `item-cleaner.interval-minutes` | `10` | Abstand zwischen den Aufräumrunden. |
| `item-cleaner.warnings` | `[60, 30, 10]` | Sekunden vorher, zu denen gewarnt wird. |
| `item-cleaner.min-age-seconds` | `60` | Nur Items, die mindestens so lange liegen. |
| `item-cleaner.include-xp-orbs` | `false` | XP-Kugeln auch entfernen. |
| `afk-view.enabled` | `false` | AFK-Sichtweite an. |
| `afk-view.afk-minutes` | `5` | Ab wann ein Spieler als AFK gilt. |
| `afk-view.view-distance` | `2` | Sichtweite während AFK. |
| `afk-view.simulation-distance` | `-1` | Simulationsdistanz während AFK (-1 = unverändert). |
| `afk-view.notify-player` | `true` | Spieler bekommt eine Nachricht. |
| `redstone-meter.enabled` | `true` | Redstone-Messung an. |
| `redstone-meter.alert-per-second` | `100` | Ab so vielen Updates/s Staff-Warnung (0 = aus). |
| `auto-performance.enabled` | `false` | Auto-Performance-Modus an. |
| `auto-performance.activate-mspt` / `recover-mspt` | `45` / `35` | Ein- und Ausschaltschwelle (ms pro Tick). |
| `auto-performance.afk-minutes` / `farm-group-size` | `2` / `10` | Strengere Werte, solange der Modus aktiv ist. |
| `web.enabled` | `false` | Website an. |
| `web.port` / `bind-address` | `8105` / `"0.0.0.0"` | Port und Netzwerkadresse. |
| `web.public-url` | `""` | Adresse, die `/dt web` anzeigt. |
| `web.require-token` | `true` | Website nur mit geheimem Token. |

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
| `sound`, `sound-volume`, `sound-pitch` | Sound als klassischer Name (`ENTITY_PLAYER_LEVELUP`) oder Minecraft-Key (`entity.player.levelup`) |

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
`gui/nether`, `gui/end` und `gui/overworld` (`config.yml`). Jede Datei hat
die Farben ihres Bereichs. Nach Änderungen `/dt reload` und das Menü neu
öffnen.

Mit 3.3.0 hat der Editor ein neues Design bekommen. Beim Update werden die
GUI-Dateien deshalb **einmalig** durch die neuen ersetzt – die alten liegen
danach in `backups/` (z.B. `backups/gui-nether-config-…yml`). Wer eigene
GUI-Texte hatte, überträgt sie von dort. Danach bleiben eigene Änderungen wie
gewohnt erhalten.

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
| `mob-management.overworld.<MOB>...` | Mob-Management der Overworld |
| `web.token` | Geheimer Token der Performance-Website |

Gespeichert wird absturzsicher im Hintergrund. Ist die Datei trotzdem einmal
unlesbar, benennt das Plugin sie in `data.yml.corrupt-<Datum>-<Uhrzeit>` um,
meldet das in der Konsole und startet mit Standardwerten – die alte Datei
bleibt zur Wiederherstellung erhalten.

### Config-Updates

Beim Start und bei `/dt reload` gleicht das Plugin `config.yml`, die
mitgelieferten Sprachdateien (`languages/en|de/messages.yml`) und die
GUI-Configs mit den Standarddateien der installierten Version ab – **bevor**
irgendein Wert gelesen wird.

- **Neue Optionen werden ergänzt**, auf jeder Ebene (z.B.
  `farm-limiter.ignore-named`) – an der passenden Stelle (hinter der Option,
  die in der Standard-Config davor steht), mit dem Kommentar aus der
  Standard-Config und in deiner Einrückung (2 oder 4 Leerzeichen).
- **Deine Werte, Kommentare, Leerzeilen und die Reihenfolge bleiben erhalten.**
  Es werden nur Zeilen eingefügt, nichts wird neu geschrieben.
- **Gelöschte Optionen bleiben gelöscht.** Das Plugin merkt sich in
  `config-state.yml`, welche Optionen es schon kennt; ergänzt werden nur
  Optionen, die in der neuen Version wirklich neu sind. Fehlt eine Option,
  nutzt das Plugin seinen eingebauten Standardwert. Beim allerersten Start mit
  dieser Funktion (Update von 3.2.0 oder älter) werden einmalig alle fehlenden
  Optionen ergänzt.
- **Umbenannte oder entfernte Optionen** übernimmt das Plugin über
  versionierte Migrationen (z.B. entfernt es die seit 1.3.0 ungenutzten
  `notify`-Abschnitte von `maintenance`, `schedule` und `lockdown`). Bei einem
  kompletten Neu-Design (3.3.0: die GUI-Dateien) kann eine Migration eine Datei
  auch einmal ganz ersetzen – immer mit Backup.
- **Backup:** Bevor eine Datei geändert wird, landet die alte Fassung in
  `backups/` (z.B. `backups/config-2026-10-17_14-03-22.yml`). Pro Datei werden
  die letzten 5 Sicherungen behalten. Unveränderte Dateien werden weder
  geschrieben noch gesichert.
- **Sicherheitsprüfung:** Jede Einfügung wird sofort mit dem echten
  YAML-Parser nachgelesen. Würde sich dadurch irgendein vorhandener Wert ändern
  oder die neue Option nicht korrekt gelesen, wird sie ausgelassen und in der
  Konsole gemeldet („could not add …“). Eine Datei mit YAML-Fehlern wird gar
  nicht angefasst.
- In der Konsole steht nach einem Update, was ergänzt wurde und wo das Backup
  liegt.
- Eigene Sprachordner (z.B. `languages/fr/`) haben keine Standarddatei und
  werden daher nicht abgeglichen.

---

## 10. FAQ und Fehlerbehebung

**Ich komme trotz deaktiviertem Nether rein.**
OPs haben standardmäßig `dimensiontoggle.bypass`. Mit einem Nicht-OP-Account
testen oder die Permission entziehen:
`/lp user <name> permission set dimensiontoggle.bypass false`.

**Spieler kommen per `/home`, `/tp` oder `/warp` in eine gesperrte Dimension.**
Das Plugin sperrt Portale und End-Gateways (auch per Fahrzeug und Enderperle), keine Teleports anderer Plugins
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
Ohne `%player%` wird er **als der Spieler** ausgeführt – der Spieler braucht
dann die Permission (z.B. `essentials.spawn`), und eine Teleport-Verzögerung
bricht ab, sobald er sich bewegt. Besser: `spawn %player%` eintragen, dann
führt die Konsole den Command aus. Spieler, die danach trotzdem noch drin
sind, teleportiert das Plugin nach 5 Sekunden selbst zum Spawn (Hinweis in
der Konsole).

**Ich kann das Spielerlimit im Editor nicht ausschalten.**
Im Editor kann man es nur verstellen. Zum Abschalten in der `config.yml`
`limits.<dimension>.enabled: false` setzen und `/dt reload`.

**Meine Textänderungen erscheinen nicht.**
`/dt reload` ausführen. GUI-Texte stehen in `gui/…/config.yml`, Broadcasts in
der `config.yml`, Command-Texte in `languages/<sprache>/messages.yml`.

**Nach einem Update fehlt eine neue Option in meiner config.yml.**
Hast du die Option früher einmal selbst gelöscht, wird sie absichtlich nicht
wieder eingefügt (siehe [Config-Updates](#config-updates)) – dann die Zeile aus
der Standard-Config übernehmen. Sollen alle fehlenden Optionen einmal neu
ergänzt werden, `config-state.yml` löschen und `/dt reload` ausführen. Steht
in der Konsole „could not add …“, war die Stelle in deiner Datei zu ungewöhnlich
formatiert – dann ebenfalls von Hand ergänzen.

**In der Konsole steht „data.yml could not be read … moved it to data.yml.corrupt-…“.**
Die Datei war beschädigt (z.B. von Hand falsch bearbeitet). Das Plugin läuft
mit Standardwerten weiter; die alte Datei liegt daneben und kann repariert
und zurückbenannt werden (Server vorher stoppen).

**Der Update-Checker meldet „status 404“.**
Das Modrinth-Projekt ist (noch) nicht erreichbar. Mit
`update-checker.enabled: false` abschalten.

**Wartung war nach einem Neustart weg.**
Laufende Countdowns werden nicht gespeichert – nach dem Neustart neu starten.

**Die Performance-Website lässt sich nicht öffnen.**
- Ist `web.enabled: true` und wurde `/dt reload` ausgeführt? In der Konsole
  steht beim Start „Performance website running on port …“ oder warum es
  nicht geklappt hat.
- Ist der Port frei und (bei gemieteten Servern) im Panel freigegeben?
- Ohne Token kommt „Forbidden“ – den Link aus `/dt web` verwenden.

**Tiere stehen nach dem Entfernen des Plugins still.**
Der Farm-Limiter (oder Villager-Optimierer) war noch an. Plugin wieder
einsetzen, Feature ausschalten (weckt alle Tiere auf), dann entfernen.

**`/lagmap` zeigt einen Chunk, aber vor Ort ist nichts.**
Die Liste wird kurz zwischengespeichert (10 s) – im Menü auf „Refresh“
klicken. Items und Mobs können sich inzwischen auch bewegt haben.

---

## 11. Bekannte Einschränkungen

- Nur **Portale** (Nether-/Endportal) und **End-Gateways** werden geprüft
  (zu Fuß, im Fahrzeug und per Enderperle).
  Teleports per Command oder durch andere Plugins, Einloggen an einer Position
  in einer gesperrten Dimension und Respawn per Seelenanker werden nicht blockiert.
- Das Aufheben des Lockdowns aktiviert **beide** Dimensionen, auch wenn eine
  davon vorher schon deaktiviert war.
- Mob-Aufräumen verschont nur benannte Mobs, gezähmte Tiere, Dorfbewohner mit
  Beruf und gerittene Reittiere – angeleinte oder in Booten sitzende Mobs
  werden mit entfernt.
- Laufende Wartungs-Countdowns überstehen keinen Neustart.
- Vom Farm-Limiter oder Villager-Optimierer eingefrorene Tiere bleiben
  eingefroren, wenn das Plugin entfernt wird, ohne die Features vorher
  auszuschalten.
- Das Redstone-Meter misst Signaländerungen und Kolben, nicht jede einzelne
  Block-Aktualisierung (z.B. reine Observer-Ketten ohne Redstone-Staub werden
  nur über die Kolben/Signale erfasst, die sie auslösen).
- Config-Updates ändern keine vorhandenen Werte – auch keine Listen (z.B.
  `warnings: [60, 30, 10]`), wenn sich deren Standard in einer neuen Version
  ändert – und gleichen selbst angelegte Sprachordner nicht ab.
- Folia wird nicht unterstützt.

---

## 12. Für Entwickler

**Bauen**

```
mvn clean package
```

Ergebnis: `target/DimensionToggle-<version>.jar`. Benötigt Java 25 und Zugriff
auf das PaperMC-Maven-Repository (`repo.papermc.io`).

**Tests**

```
mvn test
```

Automatisierte Tests mit JUnit 6 und MockBukkit (`src/test`): Zeit- und
Dauer-Parsing, Zeitplan, Persistenz, GUI, Portal-Listener, Commands,
verteiltes Rausbringen von Spielern, Lag-Werkzeuge, Optimierer, Website,
PlaceholderAPI.

**Aufbau** (Paket `net.dimensiontoggle`)

| Paket | Inhalt |
|---|---|
| `command` | `/dt` – alle Subcommands in einer Registry (Hilfe + Tab-Completion) |
| `listener` | Portal- und Gateway-Sperren |
| `manager` | Ein Manager pro Feature (Dimension, Wartung, Zeitplan, Benachrichtigungen …) |
| `gui` | Editor – jede Aktion steckt als Text im Item, nicht in der Slot-Nummer |
| `config` | Laden, automatische Config-Updates (Merge, Migrationen, Backups) und kommentarerhaltendes Schreiben der YAML-Dateien |
| `io` | Hintergrund-Thread und absturzsicheres Schreiben für alle Dateien |
| `performance` | Overworld & Performance: Monitor, Lagmap, Redstone-Meter, Chunk-Loader, Optimierer, Website |
| `hook` | PlaceholderAPI-Erweiterung |
| `model` | `ToggleDimension` (NETHER/END), `WorldScope` (+ Overworld), Benachrichtigungs-Kanäle |

Mehr Details zur Architektur und den Konventionen stehen in der
[CLAUDE.md](CLAUDE.md).

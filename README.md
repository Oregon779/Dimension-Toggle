# DimensionToggle

Paper-Plugin zum Aktivieren/Deaktivieren von Nether und End – mit
Wartungsmodus, Zeitplan, Soft-Lock, Lockdown, Spielerlimits, Mob-Management,
World Border, PvP-/Keep-Inventory-/Elytra-Schaltern, Logging und einem
kompletten Klick-Editor im Spiel (`/dt editor`).

**📖 Komplette Anleitung: [WIKI.md](WIKI.md)** – alle Features, Commands,
Permissions, Config-Optionen, der Editor und eine FAQ.

## Auf einen Blick

- **Voraussetzungen:** Paper 1.21.x (getestet gegen 1.21.1), Java 21, kein Folia
- **Installation:** Jar in `plugins/` legen, Server starten
- **Bedienung:** `/dt editor` (Klick-Editor) oder die Commands unten
- **Sprachen:** Deutsch und Englisch (`language` in der `config.yml`)

## Commands

| Command | Beschreibung | Permission |
|---|---|---|
| `/dt editor` | Klick-Editor öffnen | `dimensiontoggle.admin` |
| `/dt status` | Status + Spielerzahl je Dimension | `dimensiontoggle.status` |
| `/dt nether <on\|off>` / `/dt end <on\|off>` | Dimension aktivieren/deaktivieren | `dimensiontoggle.admin` |
| `/dt softlock <nether\|end>` | Soft-Lock umschalten (niemand Neues rein) | `dimensiontoggle.admin` |
| `/dt lockdown` | Alle Dimensionen sofort zu – nochmal = aufheben | `dimensiontoggle.admin` |
| `/dt limit <nether\|end> <zahl>` | Spielerlimit setzen (ohne Reload) | `dimensiontoggle.admin` |
| `/dt maintenance <nether\|end> <zeit>` | Wartungs-Countdown, z.B. `10m`, `1h30m` | `dimensiontoggle.admin` |
| `/dt maintenance <nether\|end> cancel` | Countdown abbrechen / Wartung beenden | `dimensiontoggle.admin` |
| `/dt reload` | Configs, Sprachdatei und GUI-Texte neu laden | `dimensiontoggle.admin` |
| `/dt checkupdate` | Sofort auf Modrinth nach Updates suchen | `dimensiontoggle.admin` |
| `/dt help` | Zeigt die Commands, die man nutzen darf | – |

Aliase: `/dt`, `/dimtoggle`. Details: [WIKI.md → Commands](WIKI.md#4-commands).

## Permissions

| Permission | Standard | Bedeutung |
|---|---|---|
| `dimensiontoggle.admin` | OP | Alle Verwaltungs-Commands, Editor, Update-Hinweise |
| `dimensiontoggle.status` | alle | `/dt status` |
| `dimensiontoggle.bypass` | OP | Ignoriert Sperre, Soft-Lock, Limit, Gateway- und Elytra-Sperre |
| `dimensiontoggle.bypass.limit.nether` | OP | Nur das Nether-Limit ignorieren |
| `dimensiontoggle.bypass.limit.end` | OP | Nur das End-Limit ignorieren |

## Bauen

```
mvn clean package     # Jar: target/DimensionToggle-<version>.jar
mvn test              # automatisierte Tests (JUnit 5 + MockBukkit)
```

In IntelliJ: Maven-Fenster → **DimensionToggle → Lifecycle → package**.
Benötigt Java 21 und Zugriff auf `repo.papermc.io`.

## Update auf dem Server

Server stoppen → **nur** die alte `.jar` aus `plugins/` löschen (den Ordner
`plugins/DimensionToggle/` mit deinen Einstellungen behalten) → neue `.jar`
hineinlegen → Server starten. Neue Hauptabschnitte der Configs werden
automatisch ergänzt, deine Werte bleiben unverändert. Neue Optionen innerhalb
bestehender Abschnitte bitte von Hand übernehmen – siehe
[WIKI.md → Config-Updates](WIKI.md#config-updates).

---

# Versionsverlauf

## Version 1.2.0 - Änderungen gegenüber 1.1.0

- Farben (`&`, `&#hex`, MiniMessage-Tags wie `<gradient>`) funktionieren
  jetzt immer gleichzeitig, kein Umschalter mehr nötig
- Bei Wartung/Zeitplan/Lockdown kannst du jetzt einen eigenen Command
  festlegen, der für jeden betroffenen Spieler ausgeführt wird (z.B. `spawn`)
  statt fest zum Weltspawn zu teleportieren
- Bei Wartung, Zeitplan und Lockdown ist jetzt einzeln einstellbar, ob die
  Nachrichten im Chat, als ActionBar, BossBar und/oder Title erscheinen
- `/dt limit <nether|end> <zahl>` - Spieler-Limit sofort ändern, ohne Reload
- `/dt status` zeigt jetzt auch, wie viele Spieler sich je Dimension aufhalten

**Action-Command statt Teleport:** In `config.yml` bei `maintenance`,
`schedule` und `lockdown` gibt es `action-command`. Trägst du dort z.B.
`spawn` ein, wird für jeden betroffenen Spieler `/spawn` ausgeführt, statt ihn
zum Weltspawn zu teleportieren. Leer lassen = Teleport zum Spawn der Hauptwelt.

**Benachrichtigungskanäle:** *(überholt seit 1.3.0 – die Texte stehen jetzt in
der `config.yml` und ein einzelnes Feld `notification` wählt den Kanal, siehe
[WIKI.md → Benachrichtigungen](WIKI.md#benachrichtigungen).)* In 1.2.0 ließen
sich `chat`, `actionbar`, `bossbar` und `title` über `maintenance.notify`,
`schedule.notify` und `lockdown.notify` einzeln schalten.

---

## Version 1.3.0 - Änderungen gegenüber 1.2.0

- **Bugfix**: Beim Lockdown kamen bisher zusätzlich zur Lockdown-Nachricht
  auch noch die normalen "Nether/End deaktiviert"-Meldungen (Title/ActionBar/
  BossBar/Sound). Das ist jetzt behoben - es kommt nur noch die
  Lockdown-Nachricht (gilt auch für Wartung und Zeitplan).
- Alle Wartungs-, Zeitplan- und Lockdown-Texte stehen jetzt **direkt in der
  config.yml** (unter `maintenance.messages`, `schedule.messages`,
  `lockdown.messages`) - genau wie bei `nether`/`end`, statt in messages.yml.
- **`/dt lockdown` ist jetzt ein Umschalter**: einmal ausführen aktiviert den
  Lockdown, nochmal ausführen hebt ihn wieder auf (beide Dimensionen werden
  wieder aktiviert).
- "Ein Plugin von Oregona!" steht jetzt am Ende der config.yml.

**Hinweis zum Update-Mechanismus:** Da diese Version eine neue Unter-Ebene
(`messages:`) in bereits vorhandene Abschnitte einführt, wurde die
automatische Update-Logik erweitert (`ConfigUpdater.ensureNestedKey`), damit
auch das zuverlässig ergänzt wird, ohne bestehende Werte zu verändern - das
wurde hier ebenfalls mit echten Beispieldateien getestet.

---

## Version 1.4.0 - Änderungen gegenüber 1.3.0

- **Zeitplan-Countdown abschaltbar**: `schedule.nether.countdown-enabled` /
  `schedule.end.countdown-enabled` (Standard: true). Bei `false` schaltet die
  Dimension ohne Vorwarnung zur eingestellten Uhrzeit um.
- **Update-Mechanismus generalisiert**: Die Erkennung fehlender Optionen
  funktioniert jetzt für beliebig tief verschachtelte neue Einstellungen
  (nicht mehr nur eine Ebene), damit zukünftige Updates zuverlässig nur
  Neues ergänzen.
- **Durchlaufende Countdown-BossBar**: Ist bei Wartung/Zeitplan `notify.bossbar:
  true` aktiv, erscheint jetzt EINE BossBar, die sichtbar herunterläuft (Text
  + Fortschrittsbalken aktualisieren sich jede Sekunde), statt bei jeder
  Warnung eine neue aufpoppen zu lassen. Genutzt wird dafür der Text aus
  `maintenance.messages.warning.bossbar` bzw. `schedule.messages.warning.bossbar`.
- **Flexible Zeitangaben bei `/dt maintenance`**: Jetzt z.B. `/dt maintenance
  nether 1h30m`, `/dt maintenance nether 5m` oder `/dt maintenance nether 30s`
  möglich. Eine reine Zahl (`/dt maintenance nether 10`) wird weiterhin wie
  bisher als Minuten interpretiert.
- **Spieler-Limit 0**: War bereits korrekt implementiert - `/dt limit nether 0`
  lässt niemanden ohne `dimensiontoggle.bypass` mehr rein (verifiziert, siehe
  Kommentar in `PortalListener`).

Alle Änderungen wurden hier wieder isoliert mit einem echten JDK getestet,
inklusive einer kompletten End-to-End-Simulation eines Updates von 1.3.0 auf
1.4.0 mit eigenen, individuell angepassten Werten.

---

## Version 1.5.0 - Änderungen gegenüber 1.4.0

- **Neue Permissions**: `dimensiontoggle.bypass.limit.nether` und
  `dimensiontoggle.bypass.limit.end` - erlauben das Betreten einer vollen
  Dimension getrennt für Nether/End. `dimensiontoggle.bypass` gewährt
  weiterhin automatisch beide (via Permission-Children).
- **`/dt` ohne Argumente** zeigt jetzt eine sortierte Übersicht (für Admins
  identisch zu `/dt help`) statt einer gequetschten Einzeiler-Meldung.
  Tab-Completion und Help-Liste sind jetzt alphabetisch sortiert.
- **Autor auf "Stone Plugins" gesetzt** (sichtbar u.a. bei `/plugins`-Details).
- **Bugfix doppelte BossBar bei Wartung**: Es erscheint jetzt nur noch die
  eine durchlaufende Countdown-BossBar, nicht mehr zusätzlich ein
  Start-Popup.
- **Übersetzbare Zeiteinheiten**: "Stunde(n)", "Minute(n)", "Sekunde(n)" sind
  jetzt in `messages.yml` als `unit-hours`, `unit-minutes`, `unit-seconds`
  editierbar (z.B. für eine englische Übersetzung), statt im Code
  festzustehen. Alle Formatierungen laufen jetzt zusätzlich über eine
  einzige zentrale Stelle statt über drei leicht unterschiedliche Kopien.
- **Bugfix Konsolen-Warnungen "ungültige Farbe/ungültiger Stil"**: Die
  mitgelieferte config.yml enthielt seit der ersten Version ungültige
  BossBar-Werte (`SOLID` statt `PROGRESS`, `GOLD` statt `YELLOW` - beides
  gibt es in der Minecraft-API nicht). Das ist jetzt für neue Installationen
  korrigiert. Für bereits bestehende Configs mit den alten Werten erkennt
  das Plugin diese jetzt zusätzlich automatisch und wandelt sie im
  Hintergrund korrekt um - ganz ohne dass du etwas in deiner Config ändern
  musst.

**Hinweis:** Der Farb/Stil-Bugfix in der config.yml selbst gilt nur für
neue Installationen (unser Update-Mechanismus überschreibt nie bestehende
Werte, auch keine fehlerhaften). Falls du wirklich "SOLID"/"GOLD" sauber in
deiner Datei stehen haben willst, kannst du es manuell auf `PROGRESS` bzw.
`YELLOW` ändern - nötig ist das aber nicht mehr, da der neue Code beide
Varianten ab sofort korrekt versteht.

---

## Version 1.6.0 - Änderungen gegenüber 1.5.0

### Mehrsprachigkeit
- `config.yml -> language: "en"` (oder `"de"`) wählt die Sprache
- Zwei Ordner werden immer angelegt: `plugins/DimensionToggle/languages/en/`
  und `.../languages/de/`, jeweils mit eigener `messages.yml`
- **Neuinstallationen sind standardmäßig komplett auf Englisch**
- Eigene weitere Sprachen: einfach `languages/en` kopieren, übersetzen, den
  Ordnernamen als `language`-Wert in `config.yml` eintragen

### Migration bestehender Installationen
Hattest du vorher schon eine `messages.yml` direkt im Plugin-Ordner (ältere
Version), wird diese beim ersten Start automatisch erkannt und in die neue
Sprachordner-Struktur übernommen - deine eigenen Textanpassungen gehen
**nicht** verloren. Eine einfache Erkennung schaut, ob die Datei eher
deutsche oder englische Wörter enthält, und legt sie entsprechend unter
`languages/de/` oder `languages/en/` ab. Die Originaldatei bleibt zusätzlich
als `messages.yml.old` erhalten. Falls Deutsch erkannt wurde, mach in der
`config.yml` noch `language: "de"`, damit die übernommenen Texte auch
tatsächlich verwendet werden (steht auch als Hinweis im Server-Log).

### Neuer Command-Effekt: Dimension aus der Wartung holen
`/dt maintenance <nether|end> cancel` kann jetzt zwei Dinge tun:
- Läuft noch ein Countdown: bricht ihn ab (wie bisher)
- Läuft kein Countdown mehr, aber die Dimension ist noch aus einer
  abgeschlossenen Wartung heraus deaktiviert: öffnet sie wieder, mit eigener
  "Wartung beendet"-Nachricht

### Config aufgeräumt für Veröffentlichung
- `config.yml` enthält jetzt **nur noch Einstellungen** (Farben, Zeiten,
  Sounds, Schalter) - keinerlei Anzeige-Text mehr. Alle Texte leben jetzt
  ausschließlich in den Sprachdateien.
- Alle Kommentare in `config.yml` sind jetzt auf Englisch (die Datei ist
  sprachneutral und wirkt sich nicht auf `language` aus)
- Einheitliche Autor-Angabe: "A plugin by Stone Plugins."

**Wichtiger Hinweis für bestehende Configs:** Da Text jetzt aus `config.yml`
entfernt wurde, bleiben in deiner *bestehenden* `config.yml` die alten,
jetzt ungenutzten Text-Felder (z.B. alte Title-Texte unter `nether.titles`)
einfach als Datenleiche liegen - sie werden nicht gelöscht (unser
Update-Mechanismus löscht nie etwas), aber auch nicht mehr genutzt. Das
Plugin funktioniert trotzdem einwandfrei; du kannst diese alten Felder bei
Gelegenheit manuell entfernen, musst es aber nicht.

---

## Version 1.7.0 - Änderungen gegenüber 1.6.0

- **Update-Checker (Modrinth)**: Prüft periodisch, ob eine neuere Version
  verfügbar ist, und informiert Spieler mit `dimensiontoggle.admin` per
  Chat (einmal pro erkannter Version). Muss in `config.yml` unter
  `update-checker.modrinth-project` mit deinem tatsächlichen Modrinth-Slug
  konfiguriert werden - bleibt sonst inaktiv, ohne Fehler zu werfen.
- **Konsolen-Startup-Log**: Zeigt beim Serverstart Schritt für Schritt an,
  was geladen wird ("Loading configuration...", "Registering commands...",
  usw.), endet mit "A plugin by Stone Plugins."
- **Soft-Lock (`/dt lock <nether|end>`)**: Neuer dritter Zustand zusätzlich
  zu an/aus. Blockiert nur den *Eintritt* neuer Spieler, wer schon drin ist,
  kann normal weiterspielen und die Dimension jederzeit verlassen. Wird
  auch bei `/dt status` angezeigt.
- **Bugfix**: Die Log-Datei (`logs/dimensiontoggle.log`) wird jetzt sofort
  beim Serverstart angelegt, statt erst beim ersten protokollierten
  Ereignis.

### Offene Punkte aus deiner Ideen-Liste

Drei deiner Vorschläge (Item-Bezahlung zum Freischalten, Warteschlangen-
System, Admin-Ziele pro Dimension) habe ich bewusst noch nicht gebaut -
die brauchen erst noch ein paar Design-Entscheidungen (z.B. einmalige
Zahlung vs. Zahlung pro Betreten, Fortschritt pro Spieler oder serverweit
gemeinsam), damit ich nicht am Ziel vorbei baue. Dazu gab es im Chat
gezielte Rückfragen.

---

## Version 1.8.0 - Änderungen gegenüber 1.7.0

### Item-Bezahlung (`config.yml -> payment`)
Einmalige Freischaltung pro Spieler, dauerhaft. Beim Portal-Betreten wird
automatisch geprüft, ob die konfigurierten Items im Inventar sind - wenn
ja, werden sie abgezogen und die Dimension ist für diesen Spieler für immer
freigeschaltet (gespeichert in `data.yml`). Bypass:
`dimensiontoggle.bypass.payment.nether` / `.end`.

### Warteschlange (`config.yml -> queue`)
Greift, wenn eine Dimension voll (Spieler-Limit) oder deaktiviert ist -
konfigurierbar getrennt für beide Fälle (`trigger-on-disabled` /
`trigger-on-full`). Spieler bekommen ihre Position mitgeteilt und werden
automatisch reinteleportiert, sobald wieder Platz ist (Prüfung alle 2
Sekunden). Verlassen mit `/dt queue leave <nether|end>`. Bypass:
`dimensiontoggle.bypass.queue.nether` / `.end`.

### Admin-Ziele (`goals.yml`, neue eigene Datei)
Serverweit gemeinsamer Fortschritt - alle Spieler tragen zum selben Ziel
bei. Zwei Objective-Typen: `BREAK` (Blöcke abbauen) und `COLLECT` (Items
vom Boden aufsammeln). Ist ein Ziel komplett erreicht, kann automatisch
die Dimension freigeschaltet werden (`on-complete: "unlock"`). Fortschritt
wird alle 30 Sekunden gespeichert (nicht bei jedem einzelnen Blockabbau,
um Festplattenzugriffe zu sparen), Kommentare/Struktur bleiben dabei
erhalten.

**Wichtige Einschränkung bei COLLECT:** Erkannt wird das Aufsammeln vom
Boden (z.B. nach dem Abbauen oder wenn ein Item liegt). Items, die per
`/give`, aus einer Truhe entnommen oder gecraftet werden, zählen nicht mit.

### Reihenfolge beim Portal-Durchgang
Damit sich die neuen Systeme sinnvoll ergänzen, prüft das Plugin beim
Betreten-Wollen jetzt in dieser Reihenfolge: deaktiviert (→ ggf.
Warteschlange) → soft-gesperrt → Bezahlung nötig → Spieler-Limit voll
(→ ggf. Warteschlange). `dimensiontoggle.bypass` überspringt alles davon.

---

## Version 1.9.0 - Änderungen gegenüber 1.8.0 (Goals-System erweitert)

### Vier Geltungsbereiche statt zwei
`goals.yml` hat jetzt vier Top-Level-Sektionen: `global` (zählt in jeder
Welt zusammen), `overworld`, `nether`, `end`. Ein Ereignis (Block abbauen,
Item aufsammeln, Mob töten) zählt IMMER zusätzlich auf ein passendes
globales Ziel ein, falls eins konfiguriert ist - unabhängig von der Welt.

### Neuer Objective-Typ: KILL
Zusätzlich zu `BREAK` und `COLLECT` gibt es jetzt `KILL` - zählt getötete
Mobs. `entity: ANY` zählt jeden Mob-Typ, oder ein konkreter Typ wie
`entity: ZOMBIE` für nur diesen.

### Automatische Belohnungs-Commands
`on-complete` ist jetzt ein Block statt eines einfachen Texts:

```yaml
on-complete:
  unlock: true        # nur bei nether/end relevant
  broadcast: true
  commands:
    - "give %player% diamond 5"
    - "give %player% netherite_ingot 1"
```

Jeder Command wird beim Erreichen des Ziels **einmal pro aktuell online
Spieler** über die Konsole ausgeführt (`%player%` wird durch den jeweiligen
Namen ersetzt) - so bekommt wirklich jeder seine Belohnung automatisch,
ohne dass ein Admin von Hand was verteilen muss.

**Einschränkung:** Nur Spieler, die zum Zeitpunkt der Ziel-Erreichung online
sind, bekommen die Commands ausgeführt. Wer offline ist, geht leer aus -
ein Nachreich-System für später beitretende Spieler gibt es (noch) nicht.

### Migration bestehender goals.yml
Hattest du schon eine `goals.yml` aus Version 1.8.0 (mit `on-complete` als
einfachem Text wie `"unlock"`), wird das beim ersten Start automatisch in
die neue Block-Struktur umgewandelt - dein bisheriger Fortschritt und die
`unlock`-Einstellung gehen dabei nicht verloren. Komplett hier im Sandkasten
mit einer nachgebauten alten Datei getestet (inklusive des kompletten
Update-Merges danach, der `global`/`overworld` neu ergänzt).

---

## Nachträge zu Version 1.7.0

### Experimentelle Systeme in eigenem Ordner
`goals.yml` und die neue `queue.yml` liegen jetzt gemeinsam in
`plugins/DimensionToggle/experimental/`, da beide Systeme noch als
experimentell markiert sind. Die Queue-Einstellungen sind aus der
`config.yml` rausgezogen worden.

**Migration bestehender Installationen:** Lag bei dir schon eine
`goals.yml` direkt im Plugin-Hauptordner, wird sie automatisch (per
reinem Datei-Umzug, ohne Inhalt zu verändern) nach
`experimental/goals.yml` verschoben. Hattest du schon eine `queue:`-
Sektion in der `config.yml`, werden diese Werte einmalig in die neue
`experimental/queue.yml` übernommen (die alte, jetzt ungenutzte Sektion
bleibt zur Sicherheit einfach in der config.yml stehen).

### Update-Checker: jetzt bei jedem Login, auch für OP
Bisher wurde die "neue Version verfügbar"-Nachricht nur einmal pro
erkannter Version gezeigt. Jetzt bekommt sie **jeder Login erneut**,
solange ein Update bekannt ist - und zwar an alle, die entweder OP sind
ODER die Permission `dimensiontoggle.admin` haben (nicht nur eins von
beidem exklusiv).

---

## Nachtrag (weiterhin Version 1.7.0)

- **Entfernt**: Queue-System, Admin-Ziele (Goals) und der `/dt lockdown`-Command
  sind komplett aus dem Plugin raus (Code, Config, Nachrichten, Permissions).
  Der `experimental`-Ordner existiert dadurch nicht mehr. Soft-Lock
  (`/dt lock`) bleibt unverändert erhalten.
- **Konsolen-Ausgabe** beim Start ist jetzt farbig (nutzt Bukkit `ChatColor`,
  wird von den meisten Server-Konsolen automatisch in ANSI-Farben umgesetzt).
- **Alle Standardnachrichten** (Englisch & Deutsch) wurden im Stil
  überarbeitet - durchgängig nur mit klassischen `&`-Farbcodes (keine
  MiniMessage-Tags, keine Gradients), mit konsistenten Symbolen (✔ ✘ ⚠ ➤ ●)
  und Hervorhebungen.

---

## Version 1.8.0

- **Lockdown ist zurück** (`/dt lockdown`) - deaktiviert sofort beide
  Dimensionen, nochmal ausführen hebt es wieder auf. War kurzzeitig entfernt,
  ist jetzt wieder vollständig da (Command, Config, Nachrichten, Permissions-
  Beschreibung).
- Queue-System und Admin-Ziele (Goals) bleiben entfernt.

---

## Version 1.9.0

- **Payment (Item-Bezahlung) entfernt** - komplett raus: Code, Config,
  Permissions, Nachrichten in beiden Sprachen.
- **Konsole ist jetzt immer Englisch**, unabhängig von `config.yml -> language`
  (das steuert weiterhin nur die Spieler-Nachrichten). Betrifft den kompletten
  Startup-Log sowie alle Warn-/Fehlermeldungen.
- **Keine `&`-Farbcodes mehr in der Konsole** - die werden von Server-Konsolen
  ohnehin nicht interpretiert und sahen nur wie Zeichensalat aus. Konsole ist
  jetzt schlicht, aber lesbar.
- **Update-Verfügbar-Meldung jetzt auch in der Konsole**: identischer Inhalt
  zu dem, was ein OP im Chat sieht, wird jetzt zusätzlich als Konsolenzeile
  ausgegeben, sobald eine neue Version erkannt wird - passiert immer erst,
  nachdem der Server komplett hochgefahren ist (5 Sekunden nach Plugin-Start,
  das liegt zeitlich sicher nach "Server marked as running").

---

## Version 2.0.0 - Public release readiness pass

- **Prettier console startup** - colored banner (works in consoles that
  support ANSI, like Pterodactyl's web console - confirmed working, unlike
  the earlier plain-text attempt).
- **Notification texts moved back into config.yml, in English** - editing
  a feature (e.g. the maintenance BossBar) is one file, one section again,
  instead of needing to also touch messages.yml. General command feedback
  and UI text remain in languages/en+de/messages.yml and stay translatable.
- **Soft-lock command renamed**: `/dt lock` is now `/dt softlock`, so it
  can't be confused with `/dt lockdown` anymore (very different word,
  same distinction in config.yml: `lock:` -> `softlock:`).
- **config.yml reordered**: language -> dimensions -> notifications ->
  nether -> end -> maintenance -> schedule -> softlock -> lockdown ->
  limits -> logging -> update-checker. Core settings first, then
  per-dimension config, then time-based features, then access control,
  then infrastructure.
- **plugin.yml fully translated to English** (description, command
  description, permission descriptions) - what you see under `/plugins`
  is now English regardless of the configured player language.
- **Full public-readiness review**: swept the entire codebase (including
  the internal audit log format, not just the console) for any remaining
  German text in user/log-facing strings and translated it - console,
  `/plugins` metadata, and the log file format are now consistently
  English throughout.

---

## Version 2.1.0

- **No more color codes in the console** - the 2.0.0 colored banner is
  gone again, back to plain text everywhere (console startup, disable
  message, update-available notice).
- **Commands reordered logically** instead of alphabetically, both in
  `/dt help` and tab-completion: status -> nether -> end -> softlock ->
  lockdown -> limit -> maintenance -> reload -> checkupdate -> help.
  Matches the flow: check state, toggle dimensions, control access,
  time-based features, admin/meta commands last.

---

## Version 2.2.0 - Notification system overhaul + console cleanup

### Single-choice notification system (adopted from StoneSpawn)
Every feature (Nether/End activate/deactivate, maintenance, schedule,
lockdown) now has ONE `notification` field instead of four separate
booleans:

```yaml
nether:
  activate:
    notification: "Title"   # CHAT, ACTIONBAR, BOSSBAR, TITLE, or NONE
    title: "..."
    subtitle: "..."
    actionbar: "..."
    bossbar: "..."
```

All channel texts stay filled in at the same time - switching where
something is shown is a one-word change, not hunting down and re-filling
text every time you change your mind. `NONE` fully disables a
notification. The old `notifications.title/actionbar/bossbar` global
toggles are gone; `sounds-enabled` is the one remaining global switch
(sound is independent of the visual channel choice).

**Breaking change for existing configs:** since this replaces the old
multi-boolean structure with a fundamentally different one, the automatic
config merge cannot cleanly carry over old `notify.*` settings - review
your `config.yml` after updating to 2.2.0 and re-pick your preferred
`notification` channel per feature. Nothing is deleted, but the old
`notify.*` keys will just sit there unused.

### Console output shortened drastically
- Startup now prints exactly **one line**, e.g. `Config loaded (en),
  Nether enabled / End enabled - commands, listeners and update checker
  ready.` instead of ~9 separate step lines.
- Update checker no longer prints a "checking every X minutes" line on
  startup.
- "No update available" message shortened to `No new version available
  (running X.X.X).`

---

## Test-1.0 - Experimental GUI branch (NOT part of the regular 2.x series)

This is a separate, experimental build - use `/dt gui` to open it. It is
**not** the next regular version; the normal 2.x line continues from
2.2.3 unaffected. Consider this a preview to try out and give feedback on.

### What's in it

**Main menu** (`/dt gui`):
- Nether/End heads to open each dimension's control panel
- Lockdown button (toggles, same as `/dt lockdown`)
- Close button

**Per-dimension control panel:**
- Enable/disable toggle
- Soft-lock toggle
- Player list (heads of everyone currently inside, click to teleport to them)
- **Keep Inventory toggle** - brand new feature, flips the vanilla
  `keepInventory` gamerule for that dimension's world(s)
- Player limit: left-click -1, shift-left -10, right-click +1, shift-right
  +10, persisted to `config.yml` automatically
- Maintenance: preset countdown buttons (5m/10m/30m/1h/2h/6h) plus a cancel
  button if one is running
- Schedule: preset open/close times, a countdown toggle, and a schedule
  on/off toggle - all persisted to `config.yml`
- End only: **Elytra flight toggle** (brand new feature) and End Gateway
  toggle
- A dashboard item showing peak players seen, world border size, loaded
  chunks, and the top 5 entity types currently in that dimension
- Back button to the main menu
- The whole panel refreshes automatically once a second while open

### Honest limitations

- **No per-dimension TPS/MSPT or RAM usage** - technically not possible on
  a normal Paper server (one shared tick thread, one shared heap for the
  whole server, not per-world). The dashboard shows server-wide TPS/MSPT,
  clearly labeled as such, rather than pretending to have a number that
  doesn't exist.
- **No free-text time entry in the GUI** - Minecraft inventory GUIs can't
  take typed input. Preset buttons cover common cases; for anything else,
  use `/dt maintenance <dim> <time>` directly.
- **No day-of-week scheduling** - the schedule GUI sets a daily open/close
  time, same as the existing `/dt` config; picking specific weekdays would
  need a new backend feature that doesn't exist yet.

---

## Test-1.x (GUI branch) - Mob Management, World Border, PvP, Design overhaul

### New GUI screens
- **Mob Management** (both dimensions): every relevant mob type for that
  dimension, click to toggle whether it can spawn at all, shift-click to
  cycle an auto-cleanup interval (off/5m/10m/30m/1h) - e.g. "remove all
  Blazes every 10 minutes".
- **World Border**: click-based size adjustment (like the player limit
  button: left/right/shift-left/shift-right for -100/+100/-1000/+1000),
  plus preset sizes and a "center at 0,0" button.

### New per-dimension toggles (both Nether and End)
- General mob spawning on/off (vanilla `doMobSpawning` gamerule)
- PvP on/off - **soft integration only**: detects whether a plugin named
  "PvPManager" is installed; if not, the button is disabled and shows
  "Requires PvP Manager". If found, toggles the vanilla per-world PvP flag
  (`World#setPVP`) - if your specific PvP plugin has its own separate
  config, you likely still need to configure it there too.

### New Nether-only toggles
- Bed explosions (vanilla behavior when a bed is used in the Nether)
- Whether monster spawner blocks function

### Design pass on Main / Schedule / Maintenance GUIs
- Main menu: darker base filler, warm accent next to the Nether head,
  cool accent next to the End head.
- Maintenance menu: added a status indicator (running/idle) between the
  two preset rows; now refreshes live like the dimension panel.
- Schedule menu: headers now sit directly above their matching preset
  row instead of being visually offset; countdown/back buttons moved to
  a cleaner bottom row alongside the close-time presets.

---

## Test-1.3 - World Border bugfix + design pass + Bed Explosion removed

- **Fixed the World Border bug**: adjusting the size (especially near the
  vanilla maximum of 60,000,000) could throw an uncaught
  `IllegalArgumentException` that silently broke the button - now clamped
  to the valid range (1 to 60,000,000) and wrapped in a try-catch either way.
- **Bed Explosion toggle removed** entirely, as requested - no more
  `BedExplosionManager`, no more button, no more config entries for it.
- **Mob Management now shows a live countdown** to the next automatic
  cleanup for any mob type with an active interval (e.g. "Next cleanup
  in: 4m"), refreshing once a second like the dimension panel.
- **Main menu redesigned**: symmetric layout around a center emblem
  (Nether head / emblem / End head, then Lockdown / overview / Close),
  with a live player-count overview and warm/cool accent panes bookending
  both rows.
- **Schedule menu**: reverted to the more spacious original layout
  (toggle up top, headers with breathing room above their preset rows,
  back and countdown on their own bottom row) instead of the cramped
  version from Test-1.2.
- **World Border menu redesigned**: single big diameter display/adjuster
  up top, four color-graded presets (green -> yellow -> orange -> red)
  flanking a center "recenter" button. Also relabeled "Size" as
  "Diameter" throughout to avoid confusion with radius.

---

## Test-1.3 (continued) - Main GUI reworked, custom maintenance time, extra schedule preset

- **Main menu reworked again**: back to the original simple layout (Nether
  head, End head), with the plugin emblem kept in the center between them,
  Lockdown centered at the bottom, and Close in the bottom-right corner.
- **Schedule menu**: added a 7th time preset (20:00) to both the open and
  close rows, filling the gap between 18:00 and 22:00.
- **Custom maintenance duration**: a new "Custom Time" button in the
  Maintenance menu opens an anvil-style input - rename the item to any
  duration (e.g. `1h30m`, `45m`, `2h`) and click it to start that exact
  countdown. This is the standard trick for free-text input in an
  inventory GUI, since Minecraft inventories can't take typed input directly.
- Re-verified all console output, GUI config text, and plugin metadata are
  English-only (the earlier grep hit was a false positive from the ✔/✘
  symbols, not actual German text).

---

## Test-1.4 (continued) - Gateway bug fixed, Main GUI polish

### Bugfix: End Gateway toggle
The "Gateways Blocked/Allowed" button only ever took effect while the End
was ALSO disabled - toggling it with the End enabled (the most natural
way to test it) silently did nothing, since the blocking check was
skipped entirely whenever the End was on. It's now a genuinely
independent toggle: blocks or allows Gateway teleportation regardless of
whether the End itself is enabled.

### Main menu polish
- Accent panes (orange near Nether, purple near End) now bookend both
  rows symmetrically, not just the top one.
- Close button changed from a Barrier (which elsewhere always means
  "blocked/disabled") to an Oak Door - clearer as "leave", not "forbidden".

---

## Test-1.4 (continued) - /dt menu, weighted command order, dimension GUI regrouped

- **Command renamed**: `/dt gui` is now `/dt menu`.
- **Weight-based command ordering**: every command now has an explicit
  weight in `DimensionToggleCommand.COMMAND_REGISTRY` (highest shown
  first, lowest last). Both `/dt help` and tab-completion are generated
  from the same sorted list, so they can never drift out of sync again.
  Current order: status (100) -> menu (95) -> nether (90) -> end (85) ->
  softlock (80) -> lockdown (75) -> limit (70) -> maintenance (65) ->
  reload (20) -> checkupdate (15) -> help (10).
- **Dimension control panel regrouped** into two clear sections with a
  divider row between them:
  - Row 1 - Access & Population: toggle, soft-lock, player list, limit,
    keep-inventory, maintenance, schedule
  - Row 3 - World Settings: mob management, mob spawning, world border,
    dashboard, PvP, plus the dimension-specific extras (Nether: spawners;
    End: elytra, gateway)
  - Row 2 in between is a solid divider strip (orange for Nether,
    magenta for End) purely for visual separation.

---

## Test-1.5 (continued) - Layout overhaul, command order tweak, Nether title color

### Main menu - now 5 rows
Nether/emblem/End up top, a two-tone divider strip (orange on the Nether
side, purple on the End side) below that, then Lockdown centered and
Close in the bottom-right on the final row.

### Nether/End control panel - checkerboard rhythm
The on/off toggle sits top-center on its own row. Everything else follows
a button-gap-button-gap rhythm instead of tightly packed rows - the gaps
are genuinely empty slots (not just a different color), giving the whole
panel noticeably more breathing room.

### Command order adjusted
`/dt editor` now weighs more than `/dt status` (it's the primary way
most people will interact with the plugin), and `/dt help` moved above
`/dt reload` / `/dt checkupdate` (reference info before admin actions
that actually change something):

```
editor (100) > status (95) > nether (90) > end (85) > softlock (80) >
lockdown (75) > limit (70) > maintenance (65) > help (20) > reload (15) > checkupdate (10)
```

### Nether GUI title
Changed from purple to gold/orange (`&6`) to match the Nether's color
theme used everywhere else (fillers, dividers, sounds).

---

## Test-1.7 - Nether color, Main menu de-cluttered, checkerboard edge fixed

- **Nether's color changed to red** (`&c`) - was orange/gold before.
- **Main menu: colored glass panes removed entirely.** The two-tone
  divider strip is gone; the menu is now plain dark filler throughout,
  letting the Nether head, End head, emblem, Lockdown, and Close speak
  for themselves without background color cues.
- **Dimension panel checkerboard fixed**: every row now starts AND ends
  with an empty edge slot (gap, button, gap, button, ..., gap) instead of
  starting with a button right at the edge. Removed a leftover unused
  `dividerFor()` helper from an earlier layout attempt while at it.

---

## Test-1.8 - Glass panes restored in Nether/End panels, command sorting clarified

- **Glass panes are back** in the Nether and End control panels. The
  previous change had marked almost every non-button slot in rows 1-4 as
  a genuinely empty gap, leaving large parts of those rows without any
  filler at all. The button positions/spacing stay the same - only the
  background filler is restored everywhere it isn't an actual button.
- **Command sorting clarification**: `/dt help` does follow the weighted
  order correctly. Tab-completion (pressing Tab) will always show
  commands alphabetically regardless of plugin-side ordering - that's a
  hardcoded Minecraft client behavior that no plugin can override.

---

## Test-1.10 - Dimension panel layout simplified

- **On/off toggle** moved down one row and centered (slot 13).
- **All other features** now fill in simple reading order (left to
  right, top to bottom) with no gaps between them, instead of the
  checkerboard pattern from before.
- **Dashboard** sits at the true geometric center of the whole menu
  (slot 31) regardless of how many features are active for that
  dimension.
- **Glass panes only on the true outer edge** of the menu now - nothing
  in between items anymore. Unused interior slots (when a dimension has
  fewer features than the End's full set) are simply left empty.

---

## Test-1.13 - Permission-aware command visibility

- **Tab-completion now filters by permission.** Players only see the
  subcommands they actually have access to, instead of the full list
  regardless of rights.
- **`/dt help` is available to everyone now** (previously required
  `dimensiontoggle.admin` outright) - but it only ever lists the commands
  the sender has permission for. A player with zero permissions still
  sees `help` itself in the list; a player with just the default
  `dimensiontoggle.status` sees `status` and `help`; an admin sees
  everything, as before.
- Actual command execution was already permission-checked and is
  unchanged - this only affects what shows up in tab-completion and
  `/dt help`, not what a determined player could type manually (which
  was, and remains, blocked with a "no permission" message either way).

---

## Test-1.14 - Full review pass: performance fixes, no functional bugs found

Went through every file in the plugin looking for bugs and performance
issues. No incorrect logic was found, but two real performance problems
were fixed:

### Dashboard no longer scans every entity every second
`DimensionGuiBuilder`'s dashboard item used to count every single entity
in the Nether/End world **on every GUI refresh** - which happens once a
second for every open dimension panel. On a busy Nether that's a real,
repeated, and completely avoidable cost. Added `DashboardStatsManager`,
which computes loaded-chunk counts and top-5 entity counts **once every
5 seconds** regardless of how many admins have a panel open, and the GUI
just reads from that cache. The numbers are still accurate to within a
few seconds, which is more than good enough for a dashboard display.

### Mob-type spawn check now uses a Set instead of a List
`MobManagementManager`'s `CreatureSpawnEvent` handler fires for every
single mob spawn on the entire server. It used to check membership with
`List.contains()` (an O(n) linear scan); switched to an `EnumSet` for
O(1) lookups. The list itself only has ~11 entries so this was never
going to be catastrophic, but it's a trivial, free fix for code that
runs on every mob spawn server-wide.

### Everything else
Reviewed `DimensionManager`, `MaintenanceManager`, `ScheduleManager`,
`NotificationManager`, `GuiManager`, and all GUI builders line by line -
no bugs found, no other repeated expensive operations identified. The
countdown timers (maintenance, schedule) already only run while actively
needed, and the remaining `world.getEntities()` calls are now both
intentionally infrequent (cleanup only when its interval elapses, and
the new 5-second dashboard cache).

---

## Version 3.0.0

The experimental GUI branch (Test-1.0 through Test-1.14) is now merged
into the regular version line. This is a major release with a large new
feature (the full click-based GUI), several breaking changes, and a
round of bugfixes and performance work.

### New: full click-based GUI (`/dt editor`)

- **Main menu** — Nether/End heads, plugin emblem, Lockdown, Close
- **Per-dimension control panel** — every toggle and setting in one
  place: on/off, soft-lock, player list (click a head to teleport to
  them), player limit (click to adjust), keep-inventory, maintenance,
  schedule, mob management, general mob spawning, world border, PvP,
  plus dimension-specific extras (Nether: spawners; End: elytra flight,
  gateways)
- **Mob Management** — toggle spawning per mob type, and set an
  automatic cleanup interval per type (off/5m/10m/30m/1h), with a live
  countdown to the next cleanup
- **World Border** — adjust the diameter by clicking (like the player
  limit), pick a preset size, or recenter at 0,0
- **Maintenance** — preset countdown buttons, or type a custom duration
  via an anvil-style rename box
- **Schedule** — preset open/close times and a countdown toggle
- Every text in every menu lives in `plugins/DimensionToggle/gui/`
  (`main/`, `nether/`, `end/`), fully editable

### New features (also usable outside the GUI, where applicable)

- Keep Inventory toggle per dimension (vanilla gamerule)
- General "can mobs spawn at all" toggle per dimension
- PvP toggle per dimension — soft integration: works if a "PvPManager"
  plugin is installed, otherwise the button is disabled
- Elytra flight toggle (End)
- Monster spawner toggle (Nether)
- Peak player count tracking per dimension

### Changed

- **Notification system overhauled**: every feature now has a single
  `notification` field (CHAT / ACTIONBAR / BOSSBAR / TITLE / NONE)
  instead of four separate toggles. All channel text stays configured
  at once, so switching where something shows up is a one-word change.
  **This is a breaking change for existing configs** — review your
  `config.yml` after updating and re-pick your preferred channel per
  feature; old settings aren't lost, just no longer read.
- `/dt lock` renamed to `/dt softlock`, clearly distinct from
  `/dt lockdown`
- `/dt gui` renamed to `/dt editor`
- Commands now have an explicit weight controlling their order in
  `/dt help` and tab-completion
- Tab-completion and `/dt help` are now permission-aware — players only
  see the commands they actually have access to
- Console output cut down to a single line on startup, always in
  English regardless of the configured player language

### Fixed

- End Gateway blocking now works independently of whether the End
  itself is enabled (previously only took effect while the End was
  also disabled, making it look broken when tested normally)
- World Border size is now clamped to the vanilla maximum
  (60,000,000) — adjusting it near that limit used to throw an
  uncaught exception that silently broke the button
- Soft-lock and Lockdown triggered from the GUI now correctly broadcast
  to players (previously silent — the GUI called the state change
  directly without the notification logic the command version had)

### Performance

- Dashboard entity counts are now cached and refreshed every 5 seconds
  instead of being recalculated on every GUI refresh (previously once
  per second, per open panel) — avoids repeatedly scanning every entity
  in the world
- Mob-type spawn checks use an O(1) set lookup instead of a linear list
  scan, on the event handler that fires for every mob spawn server-wide

### Removed

- Bed explosion toggle (added, then explicitly removed per request)

---

## Version 3.1.0 - Broadcast performance pass (250+ players)

Reviewed every broadcast path (dimension toggle, lockdown, soft-lock,
maintenance, schedule) for redundant per-player work. All of them looped
over every online player and re-did the same parsing/lookup work once
per recipient - fine at 10-20 players, wasteful at 250+.

- `NotificationManager` gained `notifyAll()` / `broadcastCustomToAll()`:
  resolve the channel, parse the Component/Title once, then reuse it for
  every recipient instead of re-parsing per player.
- `SoundManager` gained the `*ForAll()` variants: resolve the `Sound`
  enum and config values once per broadcast instead of once per player.
- Temporary BossBars broadcast to everyone now use a single shared
  `BossBar` instance and a single scheduled hide task, instead of one
  instance and one task per player (Adventure's BossBar already supports
  multiple viewers natively).
- Updated `DimensionManager`, `MaintenanceManager`, and `ScheduleManager`
  to use the new bulk methods wherever they broadcast to
  `Bukkit.getOnlinePlayers()`.

Everything single-target (a message to one specific player) is
unchanged - this only affects the "notify everyone" paths.

---

## Nachträge zu Version 3.1.0 - Bugfixes, weitere Performance, Editor-Redesign

### Bugfix: Zeitplan konnte einen Tag komplett verpassen
`ScheduleManager` löste das automatische Öffnen/Schließen bisher nur bei
einem exakten `secondsUntil == 0` aus. Bei einem Lag-Spike konnte dieser
Wert übersprungen werden (z.B. Sprung von 2 direkt auf -3), wodurch die
geplante Aktion für den ganzen Tag ausfiel. Erkennt jetzt stattdessen den
Vorzeichenwechsel zwischen zwei Ticks - kann nicht mehr übersprungen werden.

### Deprecated APIs bereinigt
`AnvilInventory#getRenameText()` (von Paper 1.21 als `forRemoval=true`
markiert) durch `AnvilView#getRenameText()` ersetzt; die alte
String-basierte `ItemMeta#setDisplayName/getDisplayName` durch die
Adventure-Component-API; `JavaPlugin#getDescription()` durch
`getPluginMeta()`. Build läuft jetzt ohne Deprecation-Warnungen.

### Weitere Performance für 250-300 Spieler
- `MobManagementManager`: Der Mob-Spawn-Handler (läuft bei jedem
  Mob-Spawn serverweit) baute pro Aufruf einen String-Key zusammen -
  jetzt verschachtelte `EnumMap`s, keine Allocation mehr auf dem Hot Path.
- `DashboardStatsManager`: Scannte bisher alle 5 Sekunden dauerhaft alle
  Entities in Nether/End, unabhängig davon ob überhaupt ein Dashboard
  offen ist. Läuft jetzt nur noch bei tatsächlichem Bedarf (`refreshIfStale()`,
  aufgerufen beim Rendern des Dashboards, intern weiterhin auf max. 1x/5s
  gedeckelt).
- `MaintenanceManager`: Wartungs-Warnungen und Teleport-Nachrichten
  wurden pro betroffenem Spieler einzeln geparst/aufgelöst - jetzt über
  `MessageManager.sendToAll()` / `SoundManager.*ForAll()` einmal aufgelöst
  und wiederverwendet (gleiches Muster wie oben in der 3.1.0-Passage).
- `LogManager`: Schreibt jetzt asynchron; Config-Zugriffe und Formatierung
  bleiben synchron, um keine neue Race mit `/dt reload` einzuführen.

### Editor-GUI komplett überarbeitet
- Alle Texte in `gui/main`, `gui/nether`, `gui/end` neu gestylt: Gradient +
  Small-Caps-Header pro Bereich (Nether = Orange-Rot, End = Lila-Pink,
  Hauptmenü/Marke = Violett-Blau), einheitliche Status-Sprache (grün+✔ =
  an, grau+✖ = aus, gold = Warnung/Countdown/Wert), Lore als
  Beschreibung + aktueller Wert + Klick-Hinweis. `/dt help` und
  `/dt status` haben passende Gradient-Trennlinien in DE+EN bekommen.
- Aktive Toggle-Buttons glühen jetzt (versteckter Verzauberungs-Glanz),
  damit der Zustand eines Panels auf einen Blick erkennbar ist.
- Alle Dimension-Menüs (Control Panel, Maintenance, Schedule, Mob
  Management, World Border, Player List) haben einen zweifarbigen Rahmen
  (Akzentfarbe am äußeren Rand, neutrale Füllung innen) statt einer Farbe.
- **Hauptmenü (`/dt editor`) neu gebaut**: War mit nur 5 echten Buttons auf
  45 Slots zu ~90% Zier-Glas und wirkte vollgestopft. Jetzt 4 Zeilen (36
  Slots) mit einer leeren Pufferzeile zwischen Nether/Emblem/End und
  Lockdown/Close, komplett ohne Glas-Füllung - die Icons stehen frei.
  Titel: `DT Toggle | Admin Editor`.

---

## Nachträge zu Version 3.1.0 (Teil 2) - Stabilitäts- und Lastpass (300 Spieler)

### Bugfixes
- **Zeitplan-Uhrzeiten ohne Anführungszeichen** (`close-time: 22:00`)
  wurden vom YAML-Parser als Zahl (1320) gelesen, `8:00` gar nicht
  akzeptiert - der Zeitplan lief dann still nie. Beides funktioniert jetzt,
  ungültige Werte werden einmalig in der Konsole gemeldet.
- **Zeitplan hebt keinen Lockdown mehr auf**: eine geplante Öffnung wird
  während eines aktiven Lockdowns übersprungen (und geloggt).
- **Wartungsdauer**: riesige Werte (`/dt maintenance nether 99999999999`)
  warfen einen internen Fehler bzw. liefen über - werden jetzt sauber als
  ungültig abgelehnt.
- **data.yml / config.yml absturzsicher**: Schreiben über temporäre Datei +
  atomares Umbenennen. Eine unlesbare data.yml wird als
  `data.yml.corrupt-<Zeitstempel>` gesichert statt still überschrieben
  (vorher: nach einem Absturz beim Speichern waren alle Dimensionen wieder
  offen, Lockdown weg).
- **Editor-GUI**: Menüwechsel nicht mehr innerhalb des Klick-Events (von
  Bukkit verboten, führt zu Geister-Items); Drag in Menüs wird blockiert;
  beim Deaktivieren/Reload werden offene Menüs geschlossen (vorher konnten
  GUI-Items wie Spawner/Elytren herausgenommen werden) und Bossbars
  ausgeblendet.
- **Eigene Wartungszeit (Amboss)** nutzt jetzt einen echten Amboss - beim
  alten Pseudo-Amboss kam die eingetippte Zeit serverseitig nie an.
- **Mob-Cleanup** entfernt keine Reittiere mehr, auf denen ein Spieler
  sitzt (Strider über Lava!).
- **PvP-Schalter** bleibt nach einem Neustart erhalten.
- **Soft-Lock mit `broadcast-scope: "dimension"`** benachrichtigt jetzt die
  Spieler in der Dimension (vorher niemanden).
- **Portale**: Listener respektiert von anderen Plugins bereits
  abgebrochene Events; die Sperr-Nachricht kommt beim Stehen im Portal nur
  noch alle 2 Sekunden statt ständig. End-Gateways haben eine eigene
  Nachricht (`gateway-blocked`) statt "End deaktiviert".
- **Spielerliste / Wartungsmenü** warfen bei älteren GUI-Configs ohne
  einzelne Lore-Zeile jede Sekunde eine Exception.
- **Update-Checker**: ein HTTP-Client statt eines neuen pro Prüfung (Thread-
  Leck), keine Doppel-Meldung bei gleichzeitiger Prüfung, `/dt reload`
  übernimmt geänderte Update-Checker-Einstellungen.
- **World Border**: Der Preset „Maximum“ (60.000.000) und das Maximum beim
  Verstellen lagen über dem, was Paper akzeptiert (59.999.968) - der Button
  tat nichts außer einer Konsolen-Warnung. Begrenzt jetzt auf das echte
  Server-Maximum.
- Neue Nachrichten in `messages.yml` (DE+EN): `gateway-blocked`,
  `players-only` (Konsole bei `/dt editor`).

### Performance
- Alle Datei-Schreibvorgänge (data.yml, Config-Änderungen aus GUI und
  `/dt limit`, Log) laufen auf einem eigenen IO-Thread statt im Main-Thread,
  in fester Reihenfolge; schnelle data.yml-Änderungen werden zu einem
  Schreibvorgang zusammengefasst.
- Lockdown/Wartung/Zeitplan bringen Spieler mit 20 pro Tick aus der
  Dimension (bis 20 Spieler weiterhin sofort) statt alle im selben Tick.
- MiniMessage-Ergebnisse werden gecacht (Editor rendert jede Sekunde neu).
- Spielerzählung/Dashboard ohne Listen-/Array-Kopien
  (`getPlayerCount`/`getChunkCount`), Mob-Cleanup mit einem Entity-Durchlauf
  pro Welt, versetzte Startzeiten der Wiederholungs-Tasks.

### Dokumentation
Neue [WIKI.md](WIKI.md) mit allen Features, Commands, Permissions,
Config-Optionen, Editor-Bedienung und FAQ. Der veraltete Anfangsteil dieser
README (Stand 1.2.0) wurde durch eine aktuelle Kurzfassung ersetzt; veraltete
Kommentare in den Standard-Dateien (`config.yml`-Kopf, `messages.yml`-Kopf)
wurden korrigiert.

### Tests
Neu: automatisierte Tests mit JUnit 5 + MockBukkit (`mvn test`, 64 Tests):
Zeit-/Dauer-Parsing, Zeitplan (Lag, Neustart, Lockdown), Persistenz
(atomar, korrupte Datei, Reihenfolge, Flush beim Beenden), GUI (Klick,
Drag, Schließen beim Deaktivieren), Portal-Listener, Commands, Batch-Teleport.

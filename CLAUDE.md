# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

DimensionToggle is a Paper Minecraft plugin (Java 25, Paper API
`26.2.build.124-stable`, plugin.yml `api-version: '26.2'`) that lets server admins enable/disable the Nether and
End, with maintenance windows, scheduling, lockdown, soft-lock, player
limits, mob management, world border control, PvP/keep-inventory/elytra
toggles, and a full click-based in-game GUI editor (`/dt editor`). See
`README.md` for the full feature/version history.

**User-facing docs live in `WIKI.md`** (German, one file: commands,
permissions, every config key, editor behavior, FAQ, known limitations).
Whenever a change alters a command, permission, config key, default, GUI
button or visible behavior, update `WIKI.md` in the same commit - and add
the changelog entry to `README.md`.

## Arbeitsregeln des Nutzers (immer einhalten)

### Fragen stellen
Stelle möglichst viele Fragen. Bevor du etwas umsetzt, kläre alle unklaren
Punkte (Spiel-Logik, gewünschtes Verhalten, Texte, Defaults, Randfälle)
mit Rückfragen, statt zu raten. Lieber eine Frage zu viel als eine Annahme
zu viel.

**Fragen immer als Auswahlfragen über das `AskUserQuestion`-Tool stellen**
(nicht als Text-Fragenliste im Chat): pro Frage 2-4 Antwortoptionen,
empfohlene Option zuerst mit „(Empfohlen)“, `multiSelect` wenn mehrere
Antworten möglich sind. Bei vielen Fragen mehrere Runden (je bis zu 4
Fragen) nacheinander stellen.

### Versionierung & Build-Erstellung
Strikte Trennung zwischen Dev-Builds und Release-Builds für Modrinth:

1. **Stabile Hauptversion:** Die Hauptversion (aktuell `3.3.0`) darfst du
   NIEMALS selbstständig ändern oder erhöhen.
2. **Dev-Suffix:** Alle laufenden Änderungen, Fixes und Test-Builds tragen
   das Format `[Hauptversion]-dev[Nummer]`, z.B. `3.3.0-dev1`, `3.3.0-dev2`.
3. **Automatisches Hochzählen:** Bei jedem neuen Build zählst du
   *ausschließlich* die Dev-Nummer um +1 hoch. Die Version steht nur in
   `pom.xml` (`<version>`); `plugin.yml` übernimmt sie automatisch über
   `${project.version}` - dort nichts hart eintragen. Die Jar heißt dann
   `target/DimensionToggle-<version>.jar`.
4. **Release-Befehl:** Erst wenn der Nutzer ausdrücklich **"Baue das
   Release"** sagt, entfernst du das `-dev`-Suffix vollständig, trägst die
   reine Hauptversion ein und baust die finale `.jar`.
5. **Nächster Zyklus:** Nach einem Release und vor neuen Änderungen fragst
   du den Nutzer, wie die nächste Hauptversion lauten soll, und startest
   wieder bei `-dev1`.

Hinweis: Der `UpdateChecker` vergleicht nur die Zahlen vor dem Suffix -
`3.3.0-dev2` gilt gegenüber Modrinth als `3.3.0`.

## Roadmap 3.3.0 (mit dem Nutzer abgestimmt)

Neue Ebene „Overworld & Performance“: dritter Button „Overworld“ im
Hauptmenü mit eigenem Panel. Lag-Werkzeuge gelten für **alle Welten**.
Jedes Feature wird **einzeln** als eigener Dev-Build gebaut und vom Nutzer
getestet, bevor das nächste kommt. Jedes Werkzeug bekommt eine **eigene
Permission** (z.B. `dimensiontoggle.lagmap`); `dimensiontoggle.admin`
enthält alle als Children.

Erledigt (3.3.0-dev1): Umstieg auf Paper 26.2 / Java 25 (Nutzer-Server
läuft auf 26.2; 1.21.x wird nicht mehr unterstützt); Fahrzeuge an Portalen
geprüft; Entfernen aus Dimensionen mit `%player%`-Konsolen-Commands +
5s-Sicherheitsnetz.
Ebenfalls gewünscht: **PlaceholderAPI-Support** (Nutzer hat PlaceholderAPI
2.11.6 und VaultAPI 1.7 als Jars bereitgestellt; Vault vorerst nicht nötig).

**Stand 3.3.0-dev2: alle Punkte unten sind gebaut (dev3: automatische Config-Updates; dev5: Enderperlen-Fix; dev6: Website zurück mit neuem Design, alle Overworld-Mobs in Gruppen, Chat-Eingabe für Intervalle, Editor-Redesign; dev7: Nether/End-Panel neu angeordnet, Chat-Nachrichten neu gestaltet)** (Nutzer wollte alles auf
einmal ohne weitere Rückfragen). Offen: bStats-Plugin-ID
(`DimensionToggle.BSTATS_PLUGIN_ID`, 0 = aus) vom Nutzer besorgen.

Ursprüngliche Planung:
1. **`/lagmap`** – `/lagmap` und `/dt lagmap`; GUI (Klick =
   Teleport) plus `/lagmap chat` (klickbare Liste, auch Konsole); Top 10
   Chunks aller Welten; Spalten: Entities gesamt, Mobs, Items/XP am Boden,
   Hopper/Block-Entities; Standard-Sortierung „Lag-Score“ (gewichtet: Mobs
   und Hopper > Items), per Klick umschaltbar; Shift-Klick = Aufräumen
   (Items löschen / Mobs entfernen außer benannte+gezähmte) nur nach
   zweitem Bestätigungsklick.
2. Farm-Limiter (an/aus): große Tiergruppen → KI + Kollision aus, bis ein
   Spieler eins schlägt. Details noch per Auswahlfragen klären.
3. Mob-Spawn-Kontrolle Overworld (wie Nether/End).
4. AFK-Sichtweite (Render-/Simulationsdistanz nach AFK-Intervall).
5. Redstone-Clock-Meter.
6. Chunk-Loader-Melder.
7. Performance-Website per Command (TPS, MSPT, …) - in dev4 kurz entfernt, in dev6
   auf Wunsch zurück mit neuem dunklem Design.
8. bStats (Plugin-ID vom Nutzer nötig).
Zusätzlich gewählt: Villager-Optimierung, Entity-Cap pro Chunk,
Boden-Item-Aufräumer, Auto-Performance-Modus, Hopper-/Block-Entity-Zähler
(Teil der Lagmap). Nicht gewählt: Lag-Alarm.
Vor jedem Feature die offenen Details per `AskUserQuestion` klären.

## Build

```
mvn clean package
```

Produces `target/DimensionToggle-<version>.jar` (shaded/relocated by
maven-shade-plugin). `mvn test` runs the JUnit 5 + MockBukkit suite in
`src/test` (use `-o` once dependencies are in `~/.m2`).

**Tests**: `org.mockbukkit.mockbukkit:mockbukkit-v26.2` (4.x, JUnit 6) -
MockBukkit publishes one artifact per Minecraft version, keep it on the
line matching paper-api. `PluginTestBase` boots a mock server with
`world`/`world_nether`/`world_the_end` (in that order - `getWorlds().get(0)`
is treated as the main world), loads the plugin and stops the update
checker so no test hits Modrinth. MockBukkit leaves a few APIs
unimplemented; fill gaps in the test doubles rather than bending production
code: `TestServerMock` (`getTPS`, settable `getAverageTickTime`/
`getTickTimes` for load tests), `TestWorldMock` (`getTileEntityCount`,
view/simulation distance) and
`TestPlayerMock` (a `teleportAsync` that actually teleports, per-player
view/simulation distance - create players via `PluginTestBase.addPlayer`).
`ChunkMock.getTileEntities` is unimplemented, so the lag map's block-entity
part is only testable on a real server (tests use `scan(false)`). An `UnimplementedOperationException`
shows up as a *skipped* test, so check the skip count, not just failures.

**Java 25 in the sandbox**: Paper 26.x is compiled for Java 25 (class
version 69); the image ships Java 21. Install and select it first:

```
apt-get install -y openjdk-25-jdk-headless
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 PATH=$JAVA_HOME/bin:$PATH
```

**Network limitation in Claude Code sandboxes**: `repo.papermc.io` is
blocked by the outbound network policy, which breaks resolution of
`io.papermc.paper:paper-api`, `com.mojang:brigadier`, and
`net.md-5:bungeecord-chat` — none of the three are mirrored on Maven
Central (everything else, MockBukkit included, resolves from Central).

Workaround: ask the user to download the jar+pom for each blocked artifact
directly from `repo.papermc.io` in their own browser and upload them here,
then install each with
`mvn install:install-file -Dfile=<jar> -DpomFile=<pom>`.
Since 26.x the versions are plain releases (no SNAPSHOT timestamps), so the
URLs work directly - for the current versions:

```
https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/26.2.build.124-stable/paper-api-26.2.build.124-stable.jar (+ .pom)
https://repo.papermc.io/repository/maven-public/com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar (+ .pom)
https://repo.papermc.io/repository/maven-public/net/md-5/bungeecord-chat/1.21-R0.2-deprecated+build.21/bungeecord-chat-1.21-R0.2-deprecated+build.21.jar (+ .pom)
```

(brigadier/bungeecord versions are the ones the paper-api pom asks for.)
PlaceholderAPI (`me.clip:placeholderapi:2.11.6`, provided scope) isn't on
Central either; its repo may be blocked too - ask the user for the jar and
install it with `-DgroupId=me.clip -DartifactId=placeholderapi
-Dversion=2.11.6 -Dpackaging=jar -DgeneratePom=true`. bStats comes from
Central and is shaded + relocated to `net.dimensiontoggle.libs.bstats`.
Once installed into `~/.m2` they persist for the rest of that container's
life - only a fresh container needs them again.

**Paper 26 API notes** (things that changed vs. 1.21 and are used here):
gamerules live in `org.bukkit.GameRules` (`KEEP_INVENTORY`, `SPAWN_MOBS`,
`PVP` - PvP is a gamerule now, `World#setPVP` is deprecated); sounds come
from `Registry.SOUNDS` (`Sound.valueOf` is gone - `SoundManager` maps the
classic config names like `ENTITY_PLAYER_LEVELUP` onto registry keys);
anvils are opened via `MenuType.ANVIL.builder()` (`openAnvil` is
deprecated). Compile with `-Dmaven.compiler.showDeprecation=true` and keep
it warning-free.

## Architecture

Package layout under `net.dimensiontoggle`:

- **`model`** - `ToggleDimension` (the `NETHER`/`END` enum; maps to
  `World.Environment`, resolves the actual `World` via `findWorld()`) and
  `MessageDisplayType` (`CHAT`/`ACTIONBAR`/`BOSSBAR`/`TITLE`/`NONE`).
- **`config`** - `ConfigManager` owns `config.yml`, `data.yml` (persisted
  runtime state: enabled/locked flags, peak players, etc.), and the
  `languages/<lang>/messages.yml` files, including migrating a pre-i18n
  single `messages.yml` into the language-folder structure. `ConfigUpdater`
  and `ConfigValueWriter` are line-based YAML editors (not
  `YamlConfiguration.save()`), used specifically so that merging new keys
  into an existing user config, or writing a single value from a command
  or GUI click (`/dt limit`, dragging the world-border size, etc.), never
  clobbers the user's own comments/formatting/ordering in the file.
  `GuiConfigManager` is the equivalent loader for the `gui/*/config.yml`
  files. **Automatic config updates**: `ConfigSynchronizer.sync(file,
  resourcePath)` runs for config.yml, the shipped language files and every
  GUI config *before* each one is read (start + `/dt reload`). It (1) runs
  pending `ConfigMigrations` (versioned, idempotent rename/remove steps -
  add one there when an option is renamed or dropped; a *new* option needs
  nothing but the default file; `Step.reset()` replaces a whole file once,
  used for the 3.3.0 GUI redesign and message restyle - gui and language
  files are at version 3, and stored
  versions never step back, so new migrations must use higher numbers), (2) inserts options missing from the user
  file via `ConfigUpdater.merge` - nested too, after the nearest preceding
  sibling, with the default's comments, in the user's indentation, every
  insertion re-parsed and rejected if any old value changes (`YamlLines` is
  the line-structure parser behind it), (3) backs the old file up to
  `backups/` (5 kept per file) and writes atomically. `config-state.yml`
  remembers each file's migration version and known option paths, so only
  options new to the plugin are added - an option the admin deleted stays
  deleted. Code must therefore still tolerate any key being absent (use a
  default, e.g. `GuiItems.line()` instead of `List.of()`).
- **`io`** - `IoExecutor` (one daemon thread, so writes land in submission
  order; `drain()` before re-reading, `shutdown()` in `onDisable`) and
  `AtomicFiles` (temp file + atomic move). Every file write - data.yml
  (coalesced via `ConfigManager.saveData()`), config edits
  (`persistConfigEdit`), log lines - goes through both: never write files
  on the main thread, never write a file in place. `onDisable` drains the
  queue and then calls `saveDataSync()`.
- **`manager`** - one manager per feature area (`DimensionManager`,
  `MaintenanceManager`, `ScheduleManager`, `NotificationManager`,
  `SoundManager`, `MessageManager`, `LogManager`, `MobManagementManager`,
  `KeepInventoryManager`, `ElytraFlyManager`, `PvpIntegrationManager`,
  `SpawnerToggleManager`, `PeakPlayerManager`, `DashboardStatsManager`,
  `UpdateChecker`). All are constructed and wired together once in
  `DimensionToggle.onEnable()` and reached from anywhere via
  `plugin.getXManager()` getters - there's no DI framework.
- **`performance`** - the Overworld & performance layer (3.3.0):
  `PerformanceMonitor` (5 s main-thread samples, 1 h history, published as
  an immutable snapshot), `LagMapScanner`, `RedstoneMeter` (event counters
  per chunk, 20 s windows), `ChunkLoaderDetector`, `ChunkReports` (shared
  clickable chat lists + `/dt tpchunk`), optimizers `FarmLimiter`,
  `VillagerOptimizer` (both via `EntityFreezer`: AI off + PDC tag, woken by
  `FreezeListener` on player hit / chunk load), `EntityCapListener`,
  `ItemCleaner`, `AfkViewManager`, `AutoPerformanceManager`, and
  `web.PerformanceWebServer` (JDK `HttpServer`, own thread, token in
  data.yml, serves only the monitor snapshot - never touch Bukkit API from
  it; page in `src/main/resources/web/index.html`, excluded from resource
  filtering). Gameplay-changing optimizers default to OFF and are toggled
  in the Overworld panel (`OverworldGuiBuilder`); lag reports share
  `ChunkReportGuiBuilder`. `model.WorldScope` (OVERWORLD/NETHER/END) is
  used where the Overworld counts too (mob management, spawn gamerule) -
  `ToggleDimension` stays NETHER/END only, so lockdown etc. never touch the
  Overworld.
- **`hook`** - `PlaceholderHook` (PlaceholderAPI, soft-depend; only loaded
  when the plugin is present).
- **`listener`** - `PortalListener` blocks entry into a
  disabled/soft-locked/full dimension via `PlayerPortalEvent`, and
  separately blocks End Gateway teleports via `PlayerTeleportEvent`
  (independent of whether the End itself is enabled).
- **`command`** - `DimensionToggleCommand` (`/dt`, aliases `dimtoggle`).
  Subcommands are declared once in `COMMAND_REGISTRY` (name + permission +
  sort weight) and drive both `/dt help` and tab-completion from the same
  list, so the two can't drift out of sync.
- **`gui`** - the click-based `/dt editor` UI. `GuiManager` is the single
  `Listener`/dispatcher: every clickable item carries an action string in
  its `PersistentDataContainer` (set by `GuiItems.build(...)`), and
  `GuiManager.onClick` / `handleAction` / `handlePrefixedAction` route on
  that string (`"DIM_TOGGLE"`, `"MAINT_START_5m"`, `"WB_PRESET_10000"`,
  ...) rather than on slot index (tests look buttons up by action, not
  slot). Each menu has its own stateless
  `*GuiBuilder.build(plugin, ...)` returning a fresh `Inventory`;
  `GuiManager` tracks which menu a player currently has open in
  `openMenus` and re-`build()`s it once/second via `refreshAll()` so live
  values (countdowns, player counts, dashboard stats) stay current. Bukkit
  forbids opening/closing inventories inside `InventoryClickEvent`, so
  click handlers change state immediately but route any menu switch or
  redraw through `afterClick()` (next tick, only if the same menu is still
  open). `closeAllMenus()` runs in `onDisable` - menus left open after
  disable are no longer click-protected.
  Look: `GuiItems.frame(inventory, plugin, Theme, rows)` draws the themed
  frame (MAIN purple, NETHER red, END magenta, OVERWORLD lime; accent
  corners, black inside) - call it last, it only fills empty slots;
  `GuiItems.place(inventory, row, items)` spreads a button group over a row
  (Overworld panel); `GuiItems.placeOnAxis` keeps a group together around the
  middle column, even counts leaving exactly the middle free (Nether/End
  control panel, 6 rows). Feature icons never switch to a barrier when off -
  state is glow + the configured green/red name. All GUI items hide
  attribute tooltips. Chat: `MessageManager.send` adds the prefix,
  `sendRaw` doesn't (help/status/report list lines).
  Click feedback: `handleAction` sets `clickSound` (`toggled(on)` for
  switches), played via `SoundManager.playGui` (`gui-sounds` in config.yml).
  `ChatInput` (registered listener, owned by `GuiManager`) asks for an
  interval in the chat: closes the menu, cancels the `AsyncChatEvent` at
  LOWEST, applies on the main thread, reopens the same menu; 30 s timeout.
  The Overworld mob list is grouped by `MobManagementManager.Category` and
  paged (28 per page); the viewer's group/page lives in `GuiManager.mobViews`.

**Everything user-visible is config-driven, not hardcoded in Java.** GUI
titles/item names/lore live in `gui/main|nether|end/config.yml`; command
feedback and the `/dt help`/`/dt status` chat output live in
`languages/en|de/messages.yml`; the actual dimension-toggle/maintenance/
schedule/lockdown broadcast text plus its timing/color/sound settings live
directly in `config.yml` (deliberately *not* in messages.yml, so one
feature's whole "what does this look/sound like" is one file, one
section). All of it is rendered through `MessageManager.parse()`, which
accepts classic `&`/`&#hex` codes *and* raw MiniMessage tags (gradients,
literal small-caps Unicode, etc.) in the same string - legacy codes are
rewritten to MiniMessage tags before the whole string is handed to
`MiniMessage.deserialize()`, so both styles can be mixed freely.

**Broadcast performance pattern** (this matters at the 250-300 concurrent
player scale the plugin targets): anything that messages or plays a sound
to many players at once - dimension toggle, lockdown, soft-lock,
maintenance, schedule - resolves the parsed `Component`/`Sound`/`BossBar`
*once* and reuses it for every recipient, via the `*ForAll()` /
`notifyAll()` / `broadcastCustomToAll()` / `sendToAll()` methods on
`NotificationManager` / `SoundManager` / `MessageManager`. Never loop
calling the single-target `notify()` / `play()` / `send()` per player for
a broadcast path - add a bulk variant instead, matching the existing ones.
Related: `MessageManager.parse()` caches Components (bounded LRU keyed by
the final string), and `DimensionManager.removePlayersFromDimension()`
moves players out 20 per tick (tracking UUIDs, re-checking each one) so a
lockdown on a full dimension isn't a single-tick spike. Repeating tasks use
staggered start offsets (20/23/101/207/409/613/811/1211/1217 ticks and the
redstone window at 407) so they don't pile onto the same tick - keep new
ones off those.

**Dimension-scoped code should stay uniform over `ToggleDimension.values()`**
rather than branching on which dimension it is, except where Nether and
End genuinely differ (End has elytra-flight/gateway toggles; Nether has
the spawner toggle) - see `DimensionGuiBuilder` for the pattern of a
shared feature list plus an `if (dimension == ToggleDimension.END)` tail
for the asymmetric bits.

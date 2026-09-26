# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

DimensionToggle is a Paper/Spigot Minecraft plugin (Java 21, Paper API
`1.21.1-R0.1-SNAPSHOT`) that lets server admins enable/disable the Nether and
End, with maintenance windows, scheduling, lockdown, soft-lock, player
limits, mob management, world border control, PvP/keep-inventory/elytra
toggles, and a full click-based in-game GUI editor (`/dt editor`). See
`README.md` for the full feature/version history.

## Build

```
mvn clean package
```

Produces `target/DimensionToggle-<version>.jar` (shaded/relocated by
maven-shade-plugin). `mvn test` runs the JUnit 5 + MockBukkit suite in
`src/test` (use `-o` once dependencies are in `~/.m2`).

**Tests**: `MockBukkit-v1.21` is pinned to `3.133.2` on purpose - it is
the line built against paper-api 1.21.1 (newer 4.x targets newer Paper and
a different package). `PluginTestBase` boots a mock server with
`world`/`world_nether`/`world_the_end` (in that order - `getWorlds().get(0)`
is treated as the main world), loads the plugin and stops the update
checker so no test hits Modrinth. MockBukkit 3.x leaves a few APIs
unimplemented; fill gaps in the test doubles rather than bending production
code: `TestServerMock` (Component inventory titles, `getTPS`) and
`TestPlayerMock` (a `teleportAsync` that actually teleports - create
players via `PluginTestBase.addPlayer`). An `UnimplementedOperationException`
shows up as a *skipped* test, so check the skip count, not just failures.

**Network limitation in Claude Code sandboxes**: `repo.papermc.io` is
blocked by the outbound network policy, which breaks resolution of
`io.papermc.paper:paper-api`, `com.mojang:brigadier`, and
`net.md-5:bungeecord-chat` — none of the three are mirrored on Maven
Central, so a plain `mvn package` fails on all three even though every
other dependency resolves fine from Central through the sandbox proxy.

Workaround: ask the user to download the jar+pom for each blocked artifact
directly from `repo.papermc.io` in their own browser (unaffected by the
sandbox's policy) and upload them here, then install locally:

```
mvn install:install-file -Dfile=<jar> -DpomFile=<pom> \
  -DgroupId=<groupId> -DartifactId=<artifactId> -Dversion=<version> -Dpackaging=jar
```

`paper-api` is a SNAPSHOT, so the literal `...-SNAPSHOT.jar` URL 404s on a
plain browser GET — have the user fetch
`.../paper-api/<version>/maven-metadata.xml` first to read the real
timestamped filename (`<snapshot><timestamp>/<buildNumber></snapshot>`),
download that, then install it under the plain `-SNAPSHOT` version string
so the project's own dependency declaration resolves against it.
`brigadier` and `bungeecord-chat` use plain (non-SNAPSHOT) versions, so
their jar/pom URLs work directly. Once installed into `~/.m2`, all three
persist for the rest of that sandbox container's life - no need to redo
this on every build within the same session, only in a fresh container.

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
  `GuiConfigManager` is the equivalent loader for the three
  `gui/*/config.yml` files. Note `ConfigUpdater.update()` only adds missing
  *top-level* sections (nested keys only for the explicit
  `NESTED_PATHS_TO_CHECK`), so code must tolerate a nested key being absent
  from an older user file (e.g. `GuiItems.line()` instead of `List.of()`).
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
  ...) rather than on slot index. Each menu has its own stateless
  `*GuiBuilder.build(plugin, ...)` returning a fresh `Inventory`;
  `GuiManager` tracks which menu a player currently has open in
  `openMenus` and re-`build()`s it once/second via `refreshAll()` so live
  values (countdowns, player counts, dashboard stats) stay current. Bukkit
  forbids opening/closing inventories inside `InventoryClickEvent`, so
  click handlers change state immediately but route any menu switch or
  redraw through `afterClick()` (next tick, only if the same menu is still
  open). `closeAllMenus()` runs in `onDisable` - menus left open after
  disable are no longer click-protected.

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
staggered start offsets (20/23/207/1211 ticks) so they don't pile onto the
same tick - keep new ones off those.

**Dimension-scoped code should stay uniform over `ToggleDimension.values()`**
rather than branching on which dimension it is, except where Nether and
End genuinely differ (End has elytra-flight/gateway toggles; Nether has
the spawner toggle) - see `DimensionGuiBuilder` for the pattern of a
shared feature list plus an `if (dimension == ToggleDimension.END)` tail
for the asymmetric bits.

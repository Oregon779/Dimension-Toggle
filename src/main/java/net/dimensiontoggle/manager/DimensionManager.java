package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DimensionManager {

    private final DimensionToggle plugin;
    private final Map<ToggleDimension, Boolean> state = new EnumMap<>(ToggleDimension.class);
    private final Map<ToggleDimension, Boolean> locked = new EnumMap<>(ToggleDimension.class);
    private boolean lockdownActive;

    public DimensionManager(DimensionToggle plugin) {
        this.plugin = plugin;
        loadState();
    }

    private void loadState() {
        for (ToggleDimension dimension : ToggleDimension.values()) {
            boolean enabled = plugin.getConfigManager().getData()
                    .getBoolean("dimensions." + dimension.getKey() + ".enabled", true);
            state.put(dimension, enabled);

            boolean isLocked = plugin.getConfigManager().getData()
                    .getBoolean("dimensions." + dimension.getKey() + ".locked", false);
            locked.put(dimension, isLocked);
        }
        lockdownActive = plugin.getConfigManager().getData().getBoolean("lockdown.active", false);
    }

    public boolean isLocked(ToggleDimension dimension) {
        return locked.getOrDefault(dimension, false);
    }

    public boolean setLocked(ToggleDimension dimension, boolean lockedValue, CommandSender actor) {
        boolean previous = isLocked(dimension);
        if (previous == lockedValue) {
            return false;
        }

        locked.put(dimension, lockedValue);
        plugin.getConfigManager().getData().set("dimensions." + dimension.getKey() + ".locked", lockedValue);
        plugin.getConfigManager().saveData();

        plugin.getLogManager().log(actor, lockedValue ? "SOFT-LOCKED" : "SOFT-UNLOCKED", dimension, null);
        return true;
    }

    public boolean toggleSoftLockWithBroadcast(ToggleDimension dimension, CommandSender actor) {
        boolean newLockedState = !isLocked(dimension);
        setLocked(dimension, newLockedState, actor);

        MessageManager messages = plugin.getMessageManager();
        Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("dimension", messages.getDimensionName(dimension.getKey()));
        messages.send(actor, newLockedState ? "softlock-enabled" : "softlock-disabled", placeholders);

        // "server" = everyone, "dimension" = only players inside it (as
        // documented in config.yml - "dimension" used to notify nobody).
        String scope = plugin.getConfigManager().getConfig().getString("softlock.broadcast-scope", "server");
        java.util.Collection<? extends Player> audience = "dimension".equalsIgnoreCase(scope)
                ? getPlayersInDimension(dimension)
                : "server".equalsIgnoreCase(scope) ? Bukkit.getOnlinePlayers() : List.of();
        if (!audience.isEmpty()) {
            // Parse once, reuse for every recipient.
            String path = newLockedState ? "softlock-enabled" : "softlock-disabled";
            net.kyori.adventure.text.Component broadcastMessage =
                    messages.parseWithPlaceholders(messages.getPrefix() + messages.get(path), placeholders);
            for (Player player : audience) {
                if (!player.equals(actor)) {
                    player.sendMessage(broadcastMessage);
                }
            }
        }

        return newLockedState;
    }

    public boolean isLockdownActive() {
        return lockdownActive;
    }

    public void setLockdownActive(boolean active) {
        lockdownActive = active;
        plugin.getConfigManager().getData().set("lockdown.active", active);
        plugin.getConfigManager().saveData();
    }

    public boolean toggleLockdownWithBroadcast(CommandSender actor) {
        boolean active = isLockdownActive();

        if (active) {
            for (ToggleDimension dimension : ToggleDimension.values()) {
                setEnabledSilently(dimension, true, actor);
            }
            setLockdownActive(false);
            plugin.getLogManager().log(actor, "LOCKDOWN LIFTED", null, "All dimensions re-enabled");

            // Was: broadcastCustom() in a per-player loop - re-parsed the
            // identical lockdown message once per online player. The bulk
            // variant resolves it once and reuses it for everyone.
            plugin.getNotificationManager().broadcastCustomToAll(Bukkit.getOnlinePlayers(), "lockdown", "lifted", Map.of());
            return false;
        }

        String actionCommand = plugin.getConfigManager().getConfig().getString("lockdown.action-command", "");
        boolean kickPlayers = plugin.getConfigManager().getConfig().getBoolean("lockdown.kick-players", true);

        for (ToggleDimension dimension : ToggleDimension.values()) {
            if (kickPlayers || (actionCommand != null && !actionCommand.isBlank())) {
                removePlayersFromDimension(dimension, actionCommand);
            }
            setEnabledSilently(dimension, false, actor);
        }
        setLockdownActive(true);
        plugin.getLogManager().log(actor, "LOCKDOWN", null, "All dimensions disabled");

        plugin.getNotificationManager().broadcastCustomToAll(Bukkit.getOnlinePlayers(), "lockdown", "activated", Map.of());
        return true;
    }

    public boolean isEnabled(ToggleDimension dimension) {
        return state.getOrDefault(dimension, true);
    }

    public boolean setEnabled(ToggleDimension dimension, boolean enabled, CommandSender actor) {
        boolean previous = isEnabled(dimension);
        if (previous == enabled) {
            return false;
        }

        state.put(dimension, enabled);
        plugin.getConfigManager().getData().set("dimensions." + dimension.getKey() + ".enabled", enabled);
        plugin.getConfigManager().saveData();

        broadcast(dimension, enabled);
        plugin.getLogManager().log(actor, enabled ? "ENABLED" : "DISABLED", dimension, null);
        return true;
    }

    public boolean setEnabled(ToggleDimension dimension, boolean enabled) {
        return setEnabled(dimension, enabled, null);
    }

    public boolean setEnabledSilently(ToggleDimension dimension, boolean enabled, CommandSender actor) {
        boolean previous = isEnabled(dimension);
        if (previous == enabled) {
            return false;
        }

        state.put(dimension, enabled);
        plugin.getConfigManager().getData().set("dimensions." + dimension.getKey() + ".enabled", enabled);
        plugin.getConfigManager().saveData();

        plugin.getLogManager().log(actor, enabled ? "ENABLED" : "DISABLED", dimension, null);
        return true;
    }

    private void broadcast(ToggleDimension dimension, boolean enabled) {
        SoundManager soundManager = plugin.getSoundManager();
        NotificationManager notificationManager = plugin.getNotificationManager();
        String key = dimension.getKey();

        // Was: notifyActivate/playActivate (etc.) called once per online
        // player, each re-reading config and re-parsing the identical
        // Title/BossBar/sound for every single one of them. At 250 players
        // that's 250x redundant work for output that's the same for
        // everyone. The bulk variants resolve everything once up front.
        var players = Bukkit.getOnlinePlayers();
        if (enabled) {
            notificationManager.notifyAll(players, key, "activate");
            soundManager.playActivateForAll(players, key);
        } else {
            notificationManager.notifyAll(players, key, "deactivate");
            soundManager.playDeactivateForAll(players, key);
        }
    }

    public List<Player> getPlayersInDimension(ToggleDimension dimension) {
        List<Player> players = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == dimension.getEnvironment()) {
                players.addAll(world.getPlayers());
            }
        }
        return players;
    }

    // Lockdown/maintenance/schedule can hit a dimension holding a large part
    // of a 300-player server. Teleporting (or running the configured command,
    // typically a sync /spawn teleport) for all of them in one tick is a
    // noticeable spike, so anything beyond the first batch is spread over the
    // following ticks. Up to REMOVALS_PER_TICK players still leave instantly.
    private static final int REMOVALS_PER_TICK = 20;

    public void removePlayersFromDimension(ToggleDimension dimension, String actionCommand) {
        List<Player> players = getPlayersInDimension(dimension);
        if (players.isEmpty()) {
            return;
        }
        String command = actionCommand == null || actionCommand.isBlank() ? null
                : actionCommand.startsWith("/") ? actionCommand.substring(1) : actionCommand;
        Location spawn = command == null ? Bukkit.getWorlds().get(0).getSpawnLocation() : null;

        // UUIDs, not Player objects, for the players still waiting their turn.
        Iterator<UUID> queue = players.stream().map(Player::getUniqueId).toList().iterator();
        if (removeBatch(queue, dimension, command, spawn)) {
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (removeBatch(queue, dimension, command, spawn)) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // Returns true once the queue is empty.
    private boolean removeBatch(Iterator<UUID> queue, ToggleDimension dimension, String command, Location spawn) {
        for (int i = 0; i < REMOVALS_PER_TICK && queue.hasNext(); i++) {
            Player player = Bukkit.getPlayer(queue.next());
            // Logged out or already left on their own in the meantime.
            if (player == null || player.getWorld().getEnvironment() != dimension.getEnvironment()) {
                continue;
            }
            if (command != null) {
                Bukkit.dispatchCommand(player, command);
            } else {
                player.teleportAsync(spawn);
            }
        }
        return !queue.hasNext();
    }

    // Called from the portal listener (with a player limit active) and the
    // editor refresh; getPlayerCount() avoids copying the player list.
    public int countPlayersInDimension(ToggleDimension dimension) {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == dimension.getEnvironment()) {
                count += world.getPlayerCount();
            }
        }
        return count;
    }
}

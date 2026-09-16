package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

        String scope = plugin.getConfigManager().getConfig().getString("softlock.broadcast-scope", "server");
        if ("server".equalsIgnoreCase(scope)) {
            // Was: messages.send() re-parsed the identical text with fresh
            // MiniMessage/regex work for every online player (250x at 250
            // players). The text and placeholders are the same for everyone
            // here, so parse it into a Component exactly once and reuse it.
            String path = newLockedState ? "softlock-enabled" : "softlock-disabled";
            net.kyori.adventure.text.Component broadcastMessage =
                    messages.parseWithPlaceholders(messages.getPrefix() + messages.get(path), placeholders);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.equals(actor)) {
                    continue;
                }
                player.sendMessage(broadcastMessage);
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

    public void teleportPlayersOutOf(ToggleDimension dimension) {
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player player : getPlayersInDimension(dimension)) {
            player.teleportAsync(spawn);
        }
    }

    public void removePlayersFromDimension(ToggleDimension dimension, String actionCommand) {
        List<Player> players = getPlayersInDimension(dimension);

        if (actionCommand != null && !actionCommand.isBlank()) {
            String command = actionCommand.startsWith("/") ? actionCommand.substring(1) : actionCommand;
            for (Player player : players) {
                Bukkit.dispatchCommand(player, command);
            }
            return;
        }

        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        for (Player player : players) {
            player.teleportAsync(spawn);
        }
    }

    public int countPlayersInDimension(ToggleDimension dimension) {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == dimension.getEnvironment()) {
                count += world.getPlayers().size();
            }
        }
        return count;
    }
}

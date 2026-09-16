package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.logging.Level;

public class SoundManager {

    private final DimensionToggle plugin;

    public SoundManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public void playActivate(Player player, String dimensionKey) {
        playPrefixed(player, dimensionKey + ".activate");
    }

    public void playDeactivate(Player player, String dimensionKey) {
        playPrefixed(player, dimensionKey + ".deactivate");
    }

    // playActivate()/playDeactivate() called in a per-player loop meant
    // re-reading the config section and re-resolving Sound.valueOf() for
    // every one of 250 players, even though the sound/volume/pitch are
    // identical for all of them in a single broadcast. Resolve once here.
    public void playActivateForAll(java.util.Collection<? extends Player> targets, String dimensionKey) {
        playPrefixedForAll(targets, dimensionKey + ".activate");
    }

    public void playDeactivateForAll(java.util.Collection<? extends Player> targets, String dimensionKey) {
        playPrefixedForAll(targets, dimensionKey + ".deactivate");
    }

    public void playPortalBlocked(Player player, String dimensionKey) {
        play(player, dimensionKey + ".portal-blocked-sound");
    }

    public void playMaintenanceWarning(Player player) {
        play(player, "maintenance.sound.warning");
    }

    public void playMaintenanceExecute(Player player) {
        play(player, "maintenance.sound.execute");
    }

    public void playMaintenanceWarningForAll(java.util.Collection<? extends Player> targets) {
        playForAll(targets, "maintenance.sound.warning");
    }

    public void playMaintenanceExecuteForAll(java.util.Collection<? extends Player> targets) {
        playForAll(targets, "maintenance.sound.execute");
    }

    public void playScheduleWarning(Player player) {
        play(player, "schedule.sound.warning");
    }

    public void playScheduleWarningForAll(java.util.Collection<? extends Player> targets) {
        playForAll(targets, "schedule.sound.warning");
    }

    private void playPrefixed(Player player, String path) {
        if (!soundsEnabled()) {
            return;
        }
        ConfigurationSection section = plugin.getConfigManager().getConfig().getConfigurationSection(path);
        if (section == null) {
            return;
        }
        playSoundSafely(player, path, section.getString("sound", ""),
                (float) section.getDouble("sound-volume", 1.0),
                (float) section.getDouble("sound-pitch", 1.0));
    }

    private void playPrefixedForAll(java.util.Collection<? extends Player> targets, String path) {
        if (!soundsEnabled() || targets.isEmpty()) {
            return;
        }
        ConfigurationSection section = plugin.getConfigManager().getConfig().getConfigurationSection(path);
        if (section == null) {
            return;
        }
        playSoundToAll(targets, path, section.getString("sound", ""),
                (float) section.getDouble("sound-volume", 1.0),
                (float) section.getDouble("sound-pitch", 1.0));
    }

    private void play(Player player, String path) {
        if (!soundsEnabled()) {
            return;
        }
        ConfigurationSection section = plugin.getConfigManager().getConfig().getConfigurationSection(path);
        if (section == null) {
            return;
        }
        playSoundSafely(player, path, section.getString("sound", ""),
                (float) section.getDouble("volume", 1.0),
                (float) section.getDouble("pitch", 1.0));
    }

    private void playForAll(java.util.Collection<? extends Player> targets, String path) {
        if (!soundsEnabled() || targets.isEmpty()) {
            return;
        }
        ConfigurationSection section = plugin.getConfigManager().getConfig().getConfigurationSection(path);
        if (section == null) {
            return;
        }
        playSoundToAll(targets, path, section.getString("sound", ""),
                (float) section.getDouble("volume", 1.0),
                (float) section.getDouble("pitch", 1.0));
    }

    private boolean soundsEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("sounds-enabled", true);
    }

    private void playSoundSafely(Player player, String path, String soundName, float volume, float pitch) {
        if (soundName == null || soundName.isBlank()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Invalid sound in the configuration ('" + path + "'): " + soundName);
        }
    }

    // Sound.valueOf() is an enum lookup, cheap on its own, but calling it
    // (plus the toUpperCase() allocation) 250 times for the same string in
    // a broadcast loop is still 250x more work than necessary - resolve the
    // enum once and reuse it for every player's playSound() call.
    private void playSoundToAll(java.util.Collection<? extends Player> targets, String path,
                                 String soundName, float volume, float pitch) {
        if (soundName == null || soundName.isBlank()) {
            return;
        }
        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Invalid sound in the configuration ('" + path + "'): " + soundName);
            return;
        }
        for (Player player : targets) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }
}

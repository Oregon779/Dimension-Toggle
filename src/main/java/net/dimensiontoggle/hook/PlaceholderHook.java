package net.dimensiontoggle.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

// %dimensiontoggle_<placeholder>% for scoreboards, tab lists, holograms...
// Only loaded when PlaceholderAPI is installed (never referenced otherwise).
//
//   nether_status / end_status       text from messages.yml (placeholder-*)
//   nether_enabled / end_enabled     true / false
//   nether_locked / end_locked       true / false
//   nether_players / end_players     players inside
//   nether_limit / end_limit         player limit, or "-" if none is active
//   nether_maintenance / ..._end     seconds until maintenance closes it (0 = none)
//   lockdown                         true / false
//   tps / mspt                       current server TPS / milliseconds per tick
public class PlaceholderHook extends PlaceholderExpansion {

    private final DimensionToggle plugin;

    public PlaceholderHook(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "dimensiontoggle";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    // Survive /papi reload - we register ourselves on enable.
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        switch (key) {
            case "lockdown":
                return String.valueOf(plugin.getDimensionManager().isLockdownActive());
            case "tps":
                return String.format(Locale.ROOT, "%.1f", Math.min(20.0, plugin.getServer().getTPS()[0]));
            case "mspt":
                return String.format(Locale.ROOT, "%.1f", plugin.getPerformanceMonitor().currentMspt());
            default:
                break;
        }
        int split = key.indexOf('_');
        if (split < 0) {
            return null;
        }
        ToggleDimension dimension = ToggleDimension.fromString(key.substring(0, split));
        if (dimension == null) {
            return null;
        }
        return switch (key.substring(split + 1)) {
            case "status" -> status(dimension);
            case "enabled" -> String.valueOf(plugin.getDimensionManager().isEnabled(dimension));
            case "locked" -> String.valueOf(plugin.getDimensionManager().isLocked(dimension));
            case "players" -> String.valueOf(plugin.getDimensionManager().countPlayersInDimension(dimension));
            case "limit" -> limit(dimension);
            case "maintenance" -> String.valueOf(plugin.getMaintenanceManager().isPending(dimension)
                    ? plugin.getMaintenanceManager().getRemainingSeconds(dimension) : 0);
            default -> null;
        };
    }

    private String status(ToggleDimension dimension) {
        String messageKey;
        if (plugin.getMaintenanceManager().isPending(dimension)) {
            messageKey = "placeholder-maintenance";
        } else if (plugin.getDimensionManager().isLocked(dimension)) {
            messageKey = "placeholder-locked";
        } else {
            messageKey = plugin.getDimensionManager().isEnabled(dimension) ? "placeholder-enabled" : "placeholder-disabled";
        }
        return plugin.getMessageManager().get(messageKey);
    }

    private String limit(ToggleDimension dimension) {
        var section = plugin.getConfigManager().getConfig().getConfigurationSection("limits." + dimension.getKey());
        if (section == null || !section.getBoolean("enabled", false)) {
            return "-";
        }
        return String.valueOf(section.getInt("max-players", 0));
    }
}

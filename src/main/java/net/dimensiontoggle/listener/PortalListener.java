package net.dimensiontoggle.listener;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.manager.MessageManager;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;

public class PortalListener implements Listener {

    private final DimensionToggle plugin;

    public PortalListener(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();

        World.Environment targetEnvironment = resolveTargetEnvironment(event);
        if (targetEnvironment == null) {
            return;
        }

        ToggleDimension dimension = ToggleDimension.fromEnvironment(targetEnvironment);
        if (dimension == null) {
            return;
        }

        if (player.hasPermission("dimensiontoggle.bypass")) {
            return;
        }

        if (!plugin.getDimensionManager().isEnabled(dimension)) {
            event.setCancelled(true);
            notifyBlocked(player, dimension);
            return;
        }

        if (plugin.getDimensionManager().isLocked(dimension)) {
            event.setCancelled(true);
            notifyLocked(player, dimension);
            return;
        }

        if (isFull(dimension) && !hasLimitBypass(player, dimension)) {
            event.setCancelled(true);
            notifyFull(player, dimension);
        }
    }

    private boolean hasLimitBypass(Player player, ToggleDimension dimension) {
        return player.hasPermission("dimensiontoggle.bypass")
                || player.hasPermission("dimensiontoggle.bypass.limit." + dimension.getKey());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEndGatewayTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            return;
        }

        if (!plugin.getConfigManager().getConfig().getBoolean("block-end-gateways", true)) {
            return;
        }

        Player player = event.getPlayer();

        if (player.hasPermission("dimensiontoggle.bypass")) {
            return;
        }

        event.setCancelled(true);
        notifyBlocked(player, ToggleDimension.END);
    }

    private World.Environment resolveTargetEnvironment(PlayerPortalEvent event) {
        switch (event.getCause()) {
            case NETHER_PORTAL -> {
                World.Environment current = event.getFrom().getWorld().getEnvironment();
                return current == World.Environment.NETHER
                        ? World.Environment.NORMAL
                        : World.Environment.NETHER;
            }
            case END_PORTAL -> {
                World.Environment current = event.getFrom().getWorld().getEnvironment();
                return current == World.Environment.THE_END
                        ? World.Environment.NORMAL
                        : World.Environment.THE_END;
            }
            default -> {
                return null;
            }
        }
    }

    private void notifyLocked(Player player, ToggleDimension dimension) {
        MessageManager messages = plugin.getMessageManager();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("dimension", messages.getDimensionName(dimension.getKey()));
        messages.send(player, "portal-locked", placeholders);
        plugin.getSoundManager().playPortalBlocked(player, dimension.getKey());
    }

    private void notifyBlocked(Player player, ToggleDimension dimension) {
        MessageManager messages = plugin.getMessageManager();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("dimension", messages.getDimensionName(dimension.getKey()));
        messages.send(player, "portal-blocked", placeholders);
        plugin.getSoundManager().playPortalBlocked(player, dimension.getKey());
    }

    private boolean isFull(ToggleDimension dimension) {
        var section = plugin.getConfigManager().getConfig()
                .getConfigurationSection("limits." + dimension.getKey());
        if (section == null || !section.getBoolean("enabled", false)) {
            return false;
        }

        int max = section.getInt("max-players", -1);
        if (max < 0) {
            return false;
        }

        return plugin.getDimensionManager().countPlayersInDimension(dimension) >= max;
    }

    private void notifyFull(Player player, ToggleDimension dimension) {
        MessageManager messages = plugin.getMessageManager();

        var section = plugin.getConfigManager().getConfig()
                .getConfigurationSection("limits." + dimension.getKey());
        int max = section == null ? 0 : section.getInt("max-players", 0);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("dimension", messages.getDimensionName(dimension.getKey()));
        placeholders.put("limit", String.valueOf(max));
        messages.send(player, "dimension-full", placeholders);

        plugin.getSoundManager().playPortalBlocked(player, dimension.getKey());
    }
}

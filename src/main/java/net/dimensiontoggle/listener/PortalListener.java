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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PortalListener implements Listener {

    // A player standing inside a blocked portal re-triggers the portal event
    // continuously. The event is still cancelled every time; only the chat
    // message + sound are throttled so they don't repeat every few ticks.
    private static final long NOTICE_COOLDOWN_MILLIS = 2000;

    private final DimensionToggle plugin;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public PortalListener(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    // ignoreCancelled: if another plugin already blocked this portal trip,
    // there is nothing to enforce and no reason to add our own message.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
            notice(player, dimension, "portal-blocked", null);
            return;
        }

        if (plugin.getDimensionManager().isLocked(dimension)) {
            event.setCancelled(true);
            notice(player, dimension, "portal-locked", null);
            return;
        }

        int limit = activeLimit(dimension);
        if (limit >= 0 && plugin.getDimensionManager().countPlayersInDimension(dimension) >= limit
                && !hasLimitBypass(player, dimension)) {
            event.setCancelled(true);
            notice(player, dimension, "dimension-full", String.valueOf(limit));
        }
    }

    private boolean hasLimitBypass(Player player, ToggleDimension dimension) {
        return player.hasPermission("dimensiontoggle.bypass")
                || player.hasPermission("dimensiontoggle.bypass.limit." + dimension.getKey());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
        // Gateways are blocked on their own - the End itself may be open, so
        // the generic "access to the End is disabled" text would be wrong.
        notice(player, ToggleDimension.END, "gateway-blocked", null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastNotice.remove(event.getPlayer().getUniqueId());
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

    // Returns the player limit if one is active for the dimension, else -1.
    private int activeLimit(ToggleDimension dimension) {
        var section = plugin.getConfigManager().getConfig()
                .getConfigurationSection("limits." + dimension.getKey());
        if (section == null || !section.getBoolean("enabled", false)) {
            return -1;
        }
        return section.getInt("max-players", -1);
    }

    private void notice(Player player, ToggleDimension dimension, String messageKey, String limit) {
        long now = System.currentTimeMillis();
        Long previous = lastNotice.get(player.getUniqueId());
        if (previous != null && now - previous < NOTICE_COOLDOWN_MILLIS) {
            return;
        }
        lastNotice.put(player.getUniqueId(), now);

        MessageManager messages = plugin.getMessageManager();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("dimension", messages.getDimensionName(dimension.getKey()));
        if (limit != null) {
            placeholders.put("limit", limit);
        }
        messages.send(player, messageKey, placeholders);
        plugin.getSoundManager().playPortalBlocked(player, dimension.getKey());
    }
}

package net.dimensiontoggle.listener;

import com.destroystokyo.paper.event.entity.EntityTeleportEndGatewayEvent;
import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.manager.MessageManager;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

        Denial denial = checkAccess(player, dimension);
        if (denial != null) {
            event.setCancelled(true);
            notice(player, dimension, denial.messageKey(), denial.limit());
        }
    }

    // Two ways past PlayerPortalEvent, both firing EntityPortalEvent instead:
    // - A player in a boat, minecart or on a mount: the *vehicle* travels and
    //   takes its passengers along.
    // - An ender pearl: it flies through the portal and pulls its thrower
    //   after it once it lands. Cancelled here, it stays on this side and
    //   lands in front of the portal like at a wall.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        List<Player> riders = travellers(event.getEntity());
        if (riders.isEmpty()) {
            return;
        }

        World.Environment fromEnvironment = event.getFrom().getWorld().getEnvironment();
        Location to = event.getTo();
        World.Environment targetEnvironment = to != null && to.getWorld() != null
                ? to.getWorld().getEnvironment()
                : guessTargetEnvironment(event.getFrom());
        if (targetEnvironment == null) {
            return;
        }

        // End -> End can only be a gateway jump.
        if (fromEnvironment == World.Environment.THE_END && targetEnvironment == World.Environment.THE_END) {
            if (gatewayDenied(riders)) {
                event.setCancelled(true);
            }
            return;
        }

        ToggleDimension dimension = ToggleDimension.fromEnvironment(targetEnvironment);
        if (dimension == null) {
            return;
        }
        // Leaving a dimension is always allowed - only entering is checked.
        if (fromEnvironment == targetEnvironment) {
            return;
        }

        // One rider without access stops the whole vehicle: it can't travel
        // half-way, and dismounting them here would drop them into the portal.
        // Riders are counted towards the limit together.
        boolean blocked = false;
        int arriving = riders.size();
        for (Player rider : riders) {
            Denial denial = checkAccess(rider, dimension, arriving);
            if (denial != null) {
                blocked = true;
                notice(rider, dimension, denial.messageKey(), denial.limit());
            }
        }
        if (blocked) {
            event.setCancelled(true);
        }
    }

    // Paper reports entities going through an End gateway with its own event.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityGateway(EntityTeleportEndGatewayEvent event) {
        if (gatewayDenied(travellers(event.getEntity()))) {
            event.setCancelled(true);
        }
    }

    // Safety net for a pearl that got through anyway (another plugin moved
    // it, a portal we didn't see): its thrower still doesn't land in a
    // closed dimension. The pearl is used up then.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlLanding(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        World from = event.getFrom().getWorld();
        World to = event.getTo().getWorld();
        // Leaving a dimension is always allowed - only entering is checked.
        if (from == null || to == null || from.getEnvironment() == to.getEnvironment()) {
            return;
        }
        ToggleDimension dimension = ToggleDimension.fromEnvironment(to.getEnvironment());
        if (dimension == null) {
            return;
        }
        Denial denial = checkAccess(event.getPlayer(), dimension);
        if (denial != null) {
            event.setCancelled(true);
            notice(event.getPlayer(), dimension, denial.messageKey(), denial.limit());
        }
    }

    // True (and the travellers told) if a gateway jump has to be stopped.
    private boolean gatewayDenied(List<Player> travellers) {
        if (travellers.isEmpty() || !gatewaysBlocked()) {
            return false;
        }
        boolean blocked = false;
        for (Player traveller : travellers) {
            if (!traveller.hasPermission("dimensiontoggle.bypass")) {
                blocked = true;
                notice(traveller, ToggleDimension.END, "gateway-blocked", null);
            }
        }
        return blocked;
    }

    private record Denial(String messageKey, String limit) {
    }

    // Same order everywhere: bypass, disabled, soft-locked, full.
    private Denial checkAccess(Player player, ToggleDimension dimension) {
        return checkAccess(player, dimension, 1);
    }

    private Denial checkAccess(Player player, ToggleDimension dimension, int arriving) {
        if (player.hasPermission("dimensiontoggle.bypass")) {
            return null;
        }
        if (!plugin.getDimensionManager().isEnabled(dimension)) {
            return new Denial("portal-blocked", null);
        }
        if (plugin.getDimensionManager().isLocked(dimension)) {
            return new Denial("portal-locked", null);
        }
        int limit = activeLimit(dimension);
        if (limit >= 0
                && plugin.getDimensionManager().countPlayersInDimension(dimension) + arriving > limit
                && !hasLimitBypass(player, dimension)) {
            return new Denial("dimension-full", String.valueOf(limit));
        }
        return null;
    }

    // The players an entity takes along through a portal: the thrower of an
    // ender pearl, or everyone riding a vehicle/mount.
    private static List<Player> travellers(Entity entity) {
        if (entity instanceof EnderPearl pearl) {
            return pearl.getShooter() instanceof Player thrower ? List.of(thrower) : List.of();
        }
        List<Player> players = new ArrayList<>();
        collectPlayers(entity, players);
        return players;
    }

    // Recursive: a player can sit on a mob that itself rides a boat.
    private static void collectPlayers(Entity entity, List<Player> into) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player player) {
                into.add(player);
            }
            collectPlayers(passenger, into);
        }
    }

    // Fallback when the server hasn't resolved a destination yet: look at
    // the portal block the entity is in.
    private static World.Environment guessTargetEnvironment(Location from) {
        Material block = from.getBlock().getType();
        World.Environment current = from.getWorld().getEnvironment();
        return switch (block) {
            case NETHER_PORTAL -> current == World.Environment.NETHER
                    ? World.Environment.NORMAL : World.Environment.NETHER;
            case END_PORTAL -> current == World.Environment.THE_END
                    ? World.Environment.NORMAL : World.Environment.THE_END;
            case END_GATEWAY -> World.Environment.THE_END;
            default -> null;
        };
    }

    private boolean gatewaysBlocked() {
        return plugin.getConfigManager().getConfig().getBoolean("block-end-gateways", true);
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

        if (!gatewaysBlocked()) {
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

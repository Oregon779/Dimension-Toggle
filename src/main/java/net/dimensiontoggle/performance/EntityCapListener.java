package net.dimensiontoggle.performance;

import net.dimensiontoggle.DimensionToggle;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

// Caps animals/villagers of one type per chunk: breeding and spawning stop
// once a chunk holds the configured number, so farms can't grow endlessly.
// Spawns by commands and other plugins are never blocked.
public class EntityCapListener implements Listener {

    private static final Set<CreatureSpawnEvent.SpawnReason> CAPPED_REASONS = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.BREEDING, CreatureSpawnEvent.SpawnReason.EGG,
            CreatureSpawnEvent.SpawnReason.DISPENSE_EGG, CreatureSpawnEvent.SpawnReason.SPAWNER,
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG, CreatureSpawnEvent.SpawnReason.NATURAL);

    private final DimensionToggle plugin;

    public EntityCapListener(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    private int cap() {
        if (!plugin.getConfigManager().getConfig().getBoolean("entity-cap.enabled", false)) {
            return -1;
        }
        return Math.max(1, plugin.getConfigManager().getConfig().getInt("entity-cap.max-per-chunk", 40));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        int cap = cap();
        if (cap < 0 || !isFull(event.getEntity().getLocation(), event.getEntityType(), cap)) {
            return;
        }
        event.setCancelled(true);
        if (event.getBreeder() instanceof Player player) {
            plugin.getMessageManager().send(player, "entity-cap-reached", Map.of(
                    "mob", pretty(event.getEntityType()), "max", String.valueOf(cap)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        // Cheapest checks first: this runs for every creature spawn.
        if (!(event.getEntity() instanceof Breedable) || !CAPPED_REASONS.contains(event.getSpawnReason())) {
            return;
        }
        int cap = cap();
        if (cap >= 0 && isFull(event.getLocation(), event.getEntityType(), cap)) {
            event.setCancelled(true);
        }
    }

    static boolean isFull(Location location, EntityType type, int cap) {
        Chunk chunk = location.getChunk();
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() == type && entity instanceof LivingEntity && ++count >= cap) {
                return true;
            }
        }
        return false;
    }

    private static String pretty(EntityType type) {
        String name = type.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}

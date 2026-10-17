package net.dimensiontoggle.performance;

import net.dimensiontoggle.DimensionToggle;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;

// Shared by the farm limiter and the villager optimizer: switches a mob's AI
// (and optionally its collision) off and remembers *who* did it in the
// entity's own data, so the right feature can wake it up again - even after
// a restart or when the chunk is loaded much later.
public final class EntityFreezer {

    public static final String FARM = "farm";
    public static final String VILLAGER = "villager";

    private EntityFreezer() {
    }

    private static NamespacedKey key(DimensionToggle plugin) {
        return new NamespacedKey(plugin, "frozen");
    }

    public static void freeze(DimensionToggle plugin, Mob mob, String reason, boolean disableCollision) {
        mob.setAware(false);
        if (disableCollision) {
            mob.setCollidable(false);
        }
        mob.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, reason);
    }

    public static void unfreeze(DimensionToggle plugin, Mob mob) {
        mob.setAware(true);
        mob.setCollidable(true);
        mob.getPersistentDataContainer().remove(key(plugin));
    }

    // null if this plugin didn't freeze the mob.
    public static String reason(DimensionToggle plugin, Mob mob) {
        return mob.getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
    }
}

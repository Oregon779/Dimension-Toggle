package net.dimensiontoggle.performance;

import net.dimensiontoggle.DimensionToggle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

// Wakes frozen mobs up: when a player hits one, and when a chunk loads with
// mobs whose freezing feature has been switched off in the meantime.
public class FreezeListener implements Listener {

    private final DimensionToggle plugin;

    public FreezeListener(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || EntityFreezer.reason(plugin, mob) == null) {
            return;
        }
        Entity damager = event.getDamager();
        boolean byPlayer = damager instanceof Player
                || (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player);
        if (byPlayer) {
            EntityFreezer.unfreeze(plugin, mob);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            String reason = EntityFreezer.reason(plugin, mob);
            if (reason == null) {
                continue;
            }
            boolean stillWanted = EntityFreezer.FARM.equals(reason)
                    ? plugin.getFarmLimiter().isActive()
                    : plugin.getVillagerOptimizer().isEnabled();
            if (!stillWanted) {
                EntityFreezer.unfreeze(plugin, mob);
            }
        }
    }
}

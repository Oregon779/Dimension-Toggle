package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;

public class ElytraFlyManager implements Listener {

    private final DimensionToggle plugin;
    private boolean enabled;

    public ElytraFlyManager(DimensionToggle plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfigManager().getData().getBoolean("end.elytra-fly-enabled", true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
        plugin.getConfigManager().getData().set("end.elytra-fly-enabled", value);
        plugin.getConfigManager().saveData();
    }

    public boolean toggle() {
        setEnabled(!enabled);
        return enabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding() || enabled) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (player.getWorld().getEnvironment() != org.bukkit.World.Environment.THE_END) {
            return;
        }
        if (player.hasPermission("dimensiontoggle.bypass")) {
            return;
        }

        event.setCancelled(true);
    }
}

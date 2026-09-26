package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public class PvpIntegrationManager implements Listener {

    private final DimensionToggle plugin;

    public PvpIntegrationManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("PvPManager") != null;
    }

    public boolean isPvpEnabled(ToggleDimension dimension) {
        World world = dimension.findWorld();
        return world != null && world.getPVP();
    }

    public boolean toggle(ToggleDimension dimension) {
        boolean newValue = !isPvpEnabled(dimension);
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == dimension.getEnvironment()) {
                world.setPVP(newValue);
            }
        }
        // World#setPVP isn't saved with the world (unlike the keepInventory /
        // doMobSpawning gamerules the other toggles use), so remember the
        // choice ourselves - otherwise it silently reverts on every restart.
        plugin.getConfigManager().getData().set(dataPath(dimension), newValue);
        plugin.getConfigManager().saveData();
        return newValue;
    }

    // Only re-applies a value the admin explicitly chose via the toggle; worlds
    // nobody touched keep whatever the server configuration says.
    public void applyStoredState() {
        for (World world : Bukkit.getWorlds()) {
            apply(world);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        apply(event.getWorld());
    }

    private void apply(World world) {
        ToggleDimension dimension = ToggleDimension.fromEnvironment(world.getEnvironment());
        if (dimension == null) {
            return;
        }
        String path = dataPath(dimension);
        if (plugin.getConfigManager().getData().isBoolean(path)) {
            world.setPVP(plugin.getConfigManager().getData().getBoolean(path));
        }
    }

    private static String dataPath(ToggleDimension dimension) {
        return "pvp." + dimension.getKey();
    }
}

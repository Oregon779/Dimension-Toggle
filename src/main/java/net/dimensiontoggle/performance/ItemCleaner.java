package net.dimensiontoggle.performance;

import net.dimensiontoggle.DimensionToggle;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;

// Removes items that have been lying on the ground for a while, every few
// minutes, with countdown warnings in chat so nobody loses their drops by
// surprise.
public class ItemCleaner {

    private final DimensionToggle plugin;
    private BukkitTask task;
    private int secondsLeft;

    public ItemCleaner(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("item-cleaner.enabled", false);
    }

    public void start() {
        stop();
        if (!isEnabled()) {
            return;
        }
        secondsLeft = intervalSeconds();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public int getSecondsLeft() {
        return task == null ? -1 : secondsLeft;
    }

    private int intervalSeconds() {
        return Math.max(1, plugin.getConfigManager().getConfig().getInt("item-cleaner.interval-minutes", 10)) * 60;
    }

    void tick() {
        secondsLeft--;
        List<Integer> warnings = plugin.getConfigManager().getConfig().getIntegerList("item-cleaner.warnings");
        if (secondsLeft > 0 && warnings.contains(secondsLeft)) {
            plugin.getMessageManager().sendToAll(Bukkit.getOnlinePlayers(), "item-cleaner-warning",
                    Map.of("seconds", String.valueOf(secondsLeft)));
        }
        if (secondsLeft <= 0) {
            int removed = clean();
            plugin.getMessageManager().sendToAll(Bukkit.getOnlinePlayers(), "item-cleaner-done",
                    Map.of("count", String.valueOf(removed)));
            secondsLeft = intervalSeconds();
        }
    }

    public int clean() {
        int minAgeTicks = Math.max(0, plugin.getConfigManager().getConfig().getInt("item-cleaner.min-age-seconds", 60)) * 20;
        boolean xp = plugin.getConfigManager().getConfig().getBoolean("item-cleaner.include-xp-orbs", false);
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            removed += removeOld(world.getEntitiesByClass(Item.class), minAgeTicks);
            if (xp) {
                removed += removeOld(world.getEntitiesByClass(ExperienceOrb.class), minAgeTicks);
            }
        }
        return removed;
    }

    private static int removeOld(java.util.Collection<? extends Entity> entities, int minAgeTicks) {
        int removed = 0;
        for (Entity entity : entities) {
            if (entity.getTicksLived() >= minAgeTicks) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }
}

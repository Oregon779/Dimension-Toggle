package net.dimensiontoggle.performance;

import net.dimensiontoggle.PluginTestBase;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizerTest extends PluginTestBase {

    private List<Cow> cows(int chunkX, int count) {
        List<Cow> list = new ArrayList<>();
        Location at = new Location(overworld, (chunkX << 4) + 8, 5, 8);
        for (int i = 0; i < count; i++) {
            list.add(overworld.spawn(at, Cow.class));
        }
        return list;
    }

    private void hit(PlayerMock player, org.bukkit.entity.Entity target) {
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(player, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(player).build(), 1.0));
    }

    @Test
    void farmLimiterFreezesOnlyBigGroupsAndAHitWakesTheAnimal() {
        plugin.getConfigManager().getConfig().set("farm-limiter.enabled", true);
        plugin.getConfigManager().getConfig().set("farm-limiter.group-size", 20);
        List<Cow> farm = cows(0, 22);
        List<Cow> small = cows(4, 5);
        Cow named = farm.get(0);
        named.customName(Component.text("Bessie"));

        assertEquals(21, plugin.getFarmLimiter().scan());
        Cow frozen = farm.get(5);
        assertFalse(frozen.isAware());
        assertFalse(frozen.isCollidable());
        assertTrue(named.isAware(), "named animals keep their AI");
        assertTrue(small.get(0).isAware(), "small groups are left alone");

        hit(server.addPlayer("Farmer"), frozen);
        assertTrue(frozen.isAware());
        assertTrue(frozen.isCollidable());

        assertEquals(20, plugin.getFarmLimiter().unfreezeAll());
        assertTrue(farm.stream().allMatch(Cow::isAware));
    }

    @Test
    void farmLimiterDoesNothingWhileOff() {
        cows(0, 30);
        assertEquals(0, plugin.getFarmLimiter().scan());
    }

    @Test
    void entityCapBlocksBreedingAndSpawningButNotCommands() {
        plugin.getConfigManager().getConfig().set("entity-cap.enabled", true);
        plugin.getConfigManager().getConfig().set("entity-cap.max-per-chunk", 3);
        List<Cow> herd = cows(0, 3);
        Cow newborn = herd.get(0);

        CreatureSpawnEvent bred = new CreatureSpawnEvent(newborn, CreatureSpawnEvent.SpawnReason.BREEDING);
        server.getPluginManager().callEvent(bred);
        assertTrue(bred.isCancelled());

        CreatureSpawnEvent command = new CreatureSpawnEvent(newborn, CreatureSpawnEvent.SpawnReason.COMMAND);
        server.getPluginManager().callEvent(command);
        assertFalse(command.isCancelled(), "admins/plugins are never blocked");

        plugin.getConfigManager().getConfig().set("entity-cap.enabled", false);
        CreatureSpawnEvent off = new CreatureSpawnEvent(newborn, CreatureSpawnEvent.SpawnReason.BREEDING);
        server.getPluginManager().callEvent(off);
        assertFalse(off.isCancelled());
    }

    @Test
    void villagerOptimizerFreezesVillagersThatStandStillAndAHitWakesThem() {
        plugin.getConfigManager().getConfig().set("villager-optimizer.enabled", true);
        plugin.getConfigManager().getConfig().set("villager-optimizer.idle-minutes", 2);
        Villager boxed = overworld.spawn(new Location(overworld, 8, 5, 8), Villager.class);
        Villager walker = overworld.spawn(new Location(overworld, 40, 5, 40), Villager.class);

        for (int minute = 0; minute < 3; minute++) {
            plugin.getVillagerOptimizer().check();
            walker.teleport(walker.getLocation().add(3, 0, 0));
        }

        assertFalse(boxed.isAware(), "trading-hall villager lost its AI");
        assertTrue(boxed.isCollidable(), "collision stays for villagers");
        assertTrue(walker.isAware());
        assertEquals(1, plugin.getVillagerOptimizer().countOptimized());

        hit(server.addPlayer("Trader"), boxed);
        assertTrue(boxed.isAware());
    }

    @Test
    void itemCleanerRemovesOnlyOldItemsAndXpWhenConfigured() {
        Location at = new Location(overworld, 8, 5, 8);
        Item old = overworld.dropItem(at, new ItemStack(Material.DIRT));
        old.setTicksLived(20 * 120);
        Item fresh = overworld.dropItem(at, new ItemStack(Material.DIRT));
        fresh.setTicksLived(20);
        ExperienceOrb orb = overworld.spawn(at, ExperienceOrb.class);
        orb.setTicksLived(20 * 120);

        assertEquals(1, plugin.getItemCleaner().clean());
        assertFalse(old.isValid());
        assertTrue(fresh.isValid());
        assertTrue(orb.isValid(), "XP orbs only with include-xp-orbs");

        plugin.getConfigManager().getConfig().set("item-cleaner.include-xp-orbs", true);
        assertEquals(1, plugin.getItemCleaner().clean());
    }

    @Test
    void autoPerformanceSwitchesOnUnderLoadAndBackOffAfterwards() {
        plugin.getConfigManager().getConfig().set("auto-performance.enabled", true);
        AutoPerformanceManager auto = plugin.getAutoPerformanceManager();
        testServer.setAverageTickTime(60);

        for (int s = 0; s < AutoPerformanceManager.ACTIVATE_AFTER_SECONDS; s += AutoPerformanceManager.CHECK_INTERVAL_SECONDS) {
            auto.check();
        }
        assertTrue(auto.isActive());
        assertTrue(plugin.getFarmLimiter().isActive(), "farm limiter runs while under load");
        assertTrue(plugin.getAfkViewManager().isActive());
        assertEquals(10, plugin.getFarmLimiter().groupSize(), "stricter group size under load");

        testServer.setAverageTickTime(20);
        for (int s = 0; s < AutoPerformanceManager.RECOVER_AFTER_SECONDS; s += AutoPerformanceManager.CHECK_INTERVAL_SECONDS) {
            auto.check();
        }
        assertFalse(auto.isActive());
        assertFalse(plugin.getFarmLimiter().isActive());
    }

    @Test
    void shortLoadSpikesDoNotTriggerAutoPerformance() {
        plugin.getConfigManager().getConfig().set("auto-performance.enabled", true);
        AutoPerformanceManager auto = plugin.getAutoPerformanceManager();
        for (int i = 0; i < 30; i++) {
            testServer.setAverageTickTime(i % 4 == 0 ? 20 : 60);
            auto.check();
        }
        assertFalse(auto.isActive());
    }

    @Test
    void afkPlayersGetALowerViewDistanceUntilTheyMove() {
        plugin.getConfigManager().getConfig().set("afk-view.enabled", true);
        plugin.getConfigManager().getConfig().set("afk-view.view-distance", 3);
        PlayerMock afk = addPlayer("Afk");
        afk.setOp(false);
        int normal = afk.getViewDistance();
        plugin.getAfkViewManager().setLastActivity(afk.getUniqueId(), System.currentTimeMillis() - 10 * 60_000);

        plugin.getAfkViewManager().check();
        assertEquals(3, afk.getViewDistance());
        assertEquals(1, plugin.getAfkViewManager().countReduced());

        afk.simulatePlayerMove(afk.getLocation().add(2, 0, 0));
        assertEquals(normal, afk.getViewDistance());
        assertEquals(0, plugin.getAfkViewManager().countReduced());
    }

    @Test
    void afkBypassPermissionKeepsTheViewDistance() {
        plugin.getConfigManager().getConfig().set("afk-view.enabled", true);
        PlayerMock staff = addPlayer("Staff");
        staff.addAttachment(plugin, "dimensiontoggle.afk.bypass", true);
        int normal = staff.getViewDistance();
        plugin.getAfkViewManager().setLastActivity(staff.getUniqueId(), 0);

        plugin.getAfkViewManager().check();
        assertEquals(normal, staff.getViewDistance());
    }
}

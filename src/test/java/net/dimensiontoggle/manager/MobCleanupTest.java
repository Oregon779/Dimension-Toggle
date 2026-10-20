package net.dimensiontoggle.manager;

import net.dimensiontoggle.PluginTestBase;
import net.dimensiontoggle.model.WorldScope;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobCleanupTest extends PluginTestBase {

    // First cleanup pass: start offset of the task + one tick.
    private static final long FIRST_PASS = 1211 + 1;

    private Location spot() {
        return overworld.getSpawnLocation();
    }

    @Test
    void cleanupSparesNamedTamedAndWorkingVillagers() {
        MobManagementManager mobs = plugin.getMobManagementManager();
        mobs.setCleanupMinutes(WorldScope.OVERWORLD, EntityType.ZOMBIE, 1);
        mobs.setCleanupMinutes(WorldScope.OVERWORLD, EntityType.WOLF, 1);
        mobs.setCleanupMinutes(WorldScope.OVERWORLD, EntityType.VILLAGER, 1);

        Zombie plain = overworld.spawn(spot(), Zombie.class);
        Zombie named = overworld.spawn(spot(), Zombie.class);
        named.customName(Component.text("Bob"));
        Wolf wild = overworld.spawn(spot(), Wolf.class);
        Wolf pet = overworld.spawn(spot(), Wolf.class);
        pet.setTamed(true);
        Villager jobless = overworld.spawn(spot(), Villager.class);
        jobless.setProfession(Villager.Profession.NONE);
        Villager farmer = overworld.spawn(spot(), Villager.class);
        farmer.setProfession(Villager.Profession.FARMER);

        server.getScheduler().performTicks(FIRST_PASS);

        assertFalse(plain.isValid());
        assertTrue(named.isValid(), "name tag protects");
        assertFalse(wild.isValid());
        assertTrue(pet.isValid(), "tamed animals stay");
        assertFalse(jobless.isValid());
        assertTrue(farmer.isValid(), "villagers with a job stay");
    }

    @Test
    void protectionsCanBeSwitchedOff() {
        plugin.getConfigManager().getConfig().set("mob-management.protect-named", false);
        plugin.getMobManagementManager().setCleanupMinutes(WorldScope.OVERWORLD, EntityType.ZOMBIE, 1);
        Zombie named = overworld.spawn(spot(), Zombie.class);
        named.customName(Component.text("Bob"));

        server.getScheduler().performTicks(FIRST_PASS);
        assertFalse(named.isValid());
    }

    @Test
    void customIntervalsAreClampedAndSaved() {
        MobManagementManager mobs = plugin.getMobManagementManager();
        mobs.setCleanupMinutes(WorldScope.NETHER, EntityType.BLAZE, 5000);
        assertEquals(MobManagementManager.MAX_CLEANUP_MINUTES, mobs.getCleanupMinutes(WorldScope.NETHER, EntityType.BLAZE));
        assertEquals(MobManagementManager.MAX_CLEANUP_MINUTES,
                plugin.getConfigManager().getData().getInt("mob-management.nether.BLAZE.cleanup-minutes"));
        mobs.setCleanupMinutes(WorldScope.NETHER, EntityType.BLAZE, 0);
        assertEquals(0, mobs.getCleanupMinutes(WorldScope.NETHER, EntityType.BLAZE));
    }
}

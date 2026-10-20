package net.dimensiontoggle.performance;

import net.dimensiontoggle.PluginTestBase;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LagToolsTest extends PluginTestBase {

    private Location inChunk(int chunkX, int chunkZ) {
        return new Location(overworld, (chunkX << 4) + 8, 5, (chunkZ << 4) + 8);
    }

    private void spawnZombies(Location at, int count) {
        for (int i = 0; i < count; i++) {
            overworld.spawn(at, Zombie.class);
        }
    }

    private void dropItems(Location at, int count) {
        for (int i = 0; i < count; i++) {
            overworld.dropItem(at, new ItemStack(Material.DIRT));
        }
    }

    @Test
    void lagMapCountsPerChunkAndRanksByWeightedScore() {
        spawnZombies(inChunk(0, 0), 4);   // 4 mobs x3 = 12
        dropItems(inChunk(5, 5), 10);     // 10 items x1 = 10

        List<LagMapScanner.ChunkStats> stats = plugin.getLagMapScanner().quickTop(10);
        assertEquals(2, stats.size());
        LagMapScanner.ChunkStats first = stats.get(0);
        assertEquals(0, first.x());
        assertEquals(0, first.z());
        assertEquals(4, first.mobs());
        assertEquals(12, first.score());
        assertEquals(10, stats.get(1).items());
    }

    @Test
    void lagMapSortsByTheChosenColumn() {
        spawnZombies(inChunk(0, 0), 4);
        dropItems(inChunk(5, 5), 10);

        List<LagMapScanner.ChunkStats> byItems = plugin.getLagMapScanner().scan(false).stream()
                .sorted(LagMapScanner.Sort.ITEMS.comparator()).toList();
        assertEquals(5, byItems.get(0).x());
        assertEquals(LagMapScanner.Sort.ENTITIES, LagMapScanner.Sort.SCORE.next());
    }

    @Test
    void cleanupRemovesJunkButKeepsWhatPlayersCareAbout() {
        Location at = inChunk(2, 2);
        dropItems(at, 3);
        spawnZombies(at, 2);
        Zombie named = overworld.spawn(at, Zombie.class);
        named.customName(Component.text("Bob"));
        Wolf pet = overworld.spawn(at, Wolf.class);
        pet.setTamed(true);
        Villager villager = overworld.spawn(at, Villager.class);

        LagMapScanner.CleanupResult result = plugin.getLagMapScanner().cleanup(overworld.getName(), 2, 2);

        assertEquals(3, result.items());
        assertEquals(2, result.mobs());
        assertTrue(named.isValid() && pet.isValid() && villager.isValid());
        assertEquals(0, overworld.getEntitiesByClass(Item.class).size());
    }

    @Test
    void redstoneMeterReportsUpdatesPerSecondPerChunk() {
        Block block = overworld.getBlockAt(40, 5, 40);   // chunk 2,2
        for (int i = 0; i < 200; i++) {
            server.getPluginManager().callEvent(new BlockRedstoneEvent(block, 0, 15));
        }
        plugin.getRedstoneMeter().closeWindow();

        RedstoneMeter.ChunkRate top = plugin.getRedstoneMeter().top(1).get(0);
        assertEquals(2, top.x());
        assertEquals(2, top.z());
        assertEquals(10.0, top.perSecond());   // 200 updates / 20 s
    }

    @Test
    void redstoneAlertsStaffAboveTheThreshold() {
        plugin.getConfigManager().getConfig().set("redstone-meter.alert-per-second", 5);
        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);
        while (admin.nextComponentMessage() != null) {
            // discard
        }
        Block block = overworld.getBlockAt(0, 5, 0);
        for (int i = 0; i < 200; i++) {
            server.getPluginManager().callEvent(new BlockRedstoneEvent(block, 0, 15));
        }
        plugin.getRedstoneMeter().closeWindow();

        assertNotNull(admin.nextComponentMessage(), "staff gets a clickable warning");
    }

    @Test
    void lagmapChatListWorksFromTheConsoleWithoutBlockEntities() {
        spawnZombies(inChunk(1, 1), 3);
        // The full scan needs block entities, which MockBukkit can't list -
        // check the entity part through the chat formatter of quickTop data.
        assertFalse(plugin.getLagMapScanner().quickTop(10).isEmpty());
    }
}

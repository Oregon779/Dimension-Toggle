package net.dimensiontoggle.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// For every file the plugin ships: take away each single option in turn,
// let the updater put it back, and check the result is again exactly the
// default file's content. Proves the merge copes with the real files.
class ShippedFilesMergeTest {

    private static List<String> resource(String path) throws Exception {
        try (InputStream in = ShippedFilesMergeTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n", -1));
        }
    }

    private static Map<String, Object> leaves(List<String> lines) throws Exception {
        YamlConfiguration yaml = ConfigUpdater.parse(lines);
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> e : yaml.getValues(true).entrySet()) {
            if (!(e.getValue() instanceof ConfigurationSection)) {
                values.put(e.getKey(), e.getValue());
            }
        }
        return values;
    }

    @ParameterizedTest
    @ValueSource(strings = {"config.yml", "languages/en/messages.yml", "languages/de/messages.yml",
            "gui/main/config.yml", "gui/nether/config.yml", "gui/end/config.yml", "gui/overworld/config.yml"})
    void everySingleOptionCanBeRestored(String file) throws Exception {
        List<String> defaults = resource(file);
        Map<String, Object> expected = leaves(defaults);
        List<String> failures = new ArrayList<>();

        for (String path : ConfigUpdater.paths(defaults)) {
            List<String> withoutIt = ConfigUpdater.remove(defaults, path);
            if (withoutIt == null) {
                failures.add("could not remove " + ConfigUpdater.display(path));
                continue;
            }
            ConfigUpdater.Result result = ConfigUpdater.merge(withoutIt, defaults, null);
            if (!result.skipped().isEmpty() || !leaves(result.lines()).equals(expected)) {
                failures.add("not restored: " + ConfigUpdater.display(path) + " " + result.skipped());
            }
        }
        assertTrue(failures.isEmpty(), file + ": " + failures);
    }

    @ParameterizedTest
    @ValueSource(strings = {"config.yml", "gui/nether/config.yml"})
    void anUpToDateFileIsNotChanged(String file) throws Exception {
        List<String> defaults = resource(file);
        ConfigUpdater.Result result = ConfigUpdater.merge(defaults, defaults, null);
        assertEquals(defaults, result.lines());
        assertTrue(result.added().isEmpty());
    }
}

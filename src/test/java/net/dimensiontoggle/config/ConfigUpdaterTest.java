package net.dimensiontoggle.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpdaterTest {

    private static List<String> lines(String text) {
        return List.of(text.split("\n", -1));
    }

    private static String text(List<String> lines) {
        return String.join("\n", lines);
    }

    private static YamlConfiguration yaml(List<String> lines) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text(lines));
        return yaml;
    }

    private static final String DEFAULTS = """
            # Main settings
            language: "en"

            features:
              # The example feature
              example:
                enabled: true
                # How strong it is
                strength: 5
              other:
                enabled: false

            # =====================
            #   NEW SECTION
            # =====================

            # Brand new feature
            brand-new:
              enabled: false
              list:
                - "a"
                - "b"

            logging:
              enabled: true
            """;

    @Test
    void addsMissingNestedOptionAtItsPlaceWithItsComment() throws Exception {
        String user = """
                # My server config - edited by hand!
                language: "de"   # German please

                features:
                  # The example feature
                  example:
                    enabled: false
                  other:
                    enabled: true

                logging:
                  enabled: false
                """;
        ConfigUpdater.Result result = ConfigUpdater.merge(lines(user), lines(DEFAULTS), null);

        assertTrue(result.skipped().isEmpty(), String.valueOf(result.skipped()));
        String merged = text(result.lines());
        assertTrue(merged.contains("  example:\n    enabled: false\n    # How strong it is\n    strength: 5\n  other:"),
                merged);
        YamlConfiguration yaml = yaml(result.lines());
        // Old values untouched ...
        assertEquals("de", yaml.getString("language"));
        assertFalse(yaml.getBoolean("features.example.enabled"));
        assertTrue(yaml.getBoolean("features.other.enabled"));
        assertFalse(yaml.getBoolean("logging.enabled"));
        // ... new ones with the default value.
        assertEquals(5, yaml.getInt("features.example.strength"));
        assertEquals(List.of("a", "b"), yaml.getStringList("brand-new.list"));
        // Comments of the user survive.
        assertTrue(merged.contains("# My server config - edited by hand!"));
        assertTrue(merged.contains("# German please"));
    }

    @Test
    void newTopLevelSectionLandsBetweenItsNeighboursWithItsBanner() {
        String user = """
                language: "en"

                features:
                  example:
                    enabled: true
                    strength: 5
                  other:
                    enabled: false

                logging:
                  enabled: true
                """;
        String merged = text(ConfigUpdater.merge(lines(user), lines(DEFAULTS), null).lines());

        int features = merged.indexOf("features:");
        int banner = merged.indexOf("#   NEW SECTION");
        int brandNew = merged.indexOf("brand-new:");
        int logging = merged.indexOf("logging:");
        assertTrue(features < banner && banner < brandNew && brandNew < logging, merged);
    }

    @Test
    void usesTheIndentationOfTheUsersFile() throws Exception {
        String user = """
                language: "en"
                features:
                    example:
                        enabled: true
                    other:
                        enabled: false
                logging:
                    enabled: true
                """;
        ConfigUpdater.Result result = ConfigUpdater.merge(lines(user), lines(DEFAULTS), null);

        assertTrue(result.skipped().isEmpty(), String.valueOf(result.skipped()));
        assertTrue(text(result.lines()).contains("\n        strength: 5"), text(result.lines()));
        assertEquals(5, yaml(result.lines()).getInt("features.example.strength"));
    }

    @Test
    void onlyEligibleOptionsAreAdded() throws Exception {
        String user = """
                language: "en"
                features:
                  example:
                    enabled: true
                  other:
                    enabled: false
                logging:
                  enabled: true
                """;
        // strength was known before (admin deleted it), brand-new is new.
        List<String> eligible = List.of(ConfigUpdater.join("brand-new"));
        ConfigUpdater.Result result = ConfigUpdater.merge(lines(user), lines(DEFAULTS), eligible);

        YamlConfiguration yaml = yaml(result.lines());
        assertFalse(yaml.contains("features.example.strength"), "deleted on purpose - stays deleted");
        assertTrue(yaml.contains("brand-new.enabled"));
    }

    @Test
    void doesNotRecreateASectionTheAdminRemoved() throws Exception {
        String user = """
                language: "en"
                logging:
                  enabled: true
                """;
        // Only the child is "new" - its removed parent is not recreated.
        ConfigUpdater.Result result = ConfigUpdater.merge(lines(user), lines(DEFAULTS),
                List.of(ConfigUpdater.join("features", "example", "strength")));
        assertFalse(result.changed());
        assertFalse(yaml(result.lines()).contains("features"));
    }

    @Test
    void valueWhereASectionIsExpectedIsLeftAlone() throws Exception {
        String user = """
                language: "en"
                features: none
                logging:
                  enabled: true
                """;
        ConfigUpdater.Result result = ConfigUpdater.merge(lines(user), lines(DEFAULTS), null);
        assertEquals("none", yaml(result.lines()).getString("features"));
    }

    @Test
    void brokenYamlIsNeverTouched() {
        List<String> broken = lines("language: \"en\nfeatures: [unclosed\n");
        ConfigUpdater.Result result = ConfigUpdater.merge(broken, lines(DEFAULTS), null);
        assertFalse(result.changed());
        assertEquals(broken, result.lines());
        assertFalse(result.skipped().isEmpty());
    }

    @Test
    void customKeysAndListsOfTheUserStay() throws Exception {
        String user = """
                language: "en"
                my-own-key: 42
                features:
                  example:
                    enabled: true
                    strength: 9
                  other:
                    enabled: false
                brand-new:
                  enabled: true
                  list:
                  - "x"
                  - "y"
                  - "z"
                logging:
                  enabled: true
                """;
        ConfigUpdater.Result result = ConfigUpdater.merge(lines(user), lines(DEFAULTS), null);
        assertFalse(result.changed(), "nothing is missing");
        YamlConfiguration yaml = yaml(result.lines());
        assertEquals(42, yaml.getInt("my-own-key"));
        assertEquals(List.of("x", "y", "z"), yaml.getStringList("brand-new.list"));
    }

    @Test
    void addingIntoASectionWithSameIndentListsKeepsTheList() throws Exception {
        String defaults = """
                section:
                  items:
                  - "a"
                  added: true
                """;
        String user = """
                section:
                  items:
                  - "mine"
                """;
        ConfigUpdater.Result result = ConfigUpdater.merge(lines(user), lines(defaults), null);
        YamlConfiguration yaml = yaml(result.lines());
        assertEquals(List.of("mine"), yaml.getStringList("section.items"));
        assertTrue(yaml.getBoolean("section.added"));
    }

    @Test
    void removeDropsTheOptionWithItsComment() throws Exception {
        List<String> result = ConfigUpdater.remove(lines(DEFAULTS), ConfigUpdater.join("features", "example", "strength"));
        assertFalse(text(result).contains("How strong it is"));
        YamlConfiguration yaml = yaml(result);
        assertFalse(yaml.contains("features.example.strength"));
        assertTrue(yaml.getBoolean("features.example.enabled"));
        assertNull(ConfigUpdater.remove(lines(DEFAULTS), ConfigUpdater.join("nope")));
    }

    @Test
    void renameKeepsTheAdminsValue() throws Exception {
        String user = """
                features:
                  example:
                    enabled: true
                    power: 9   # my value
                  other:
                    enabled: false
                """;
        List<String> result = ConfigUpdater.rename(lines(user),
                ConfigUpdater.join("features", "example", "power"), ConfigUpdater.join("features", "other", "strength"));
        YamlConfiguration yaml = yaml(result);
        assertFalse(yaml.contains("features.example.power"));
        assertEquals(9, yaml.getInt("features.other.strength"));
        assertTrue(text(result).contains("# my value"));
        // Idempotent: nothing to rename anymore.
        assertNull(ConfigUpdater.rename(result,
                ConfigUpdater.join("features", "example", "power"), ConfigUpdater.join("features", "other", "strength")));
    }
}

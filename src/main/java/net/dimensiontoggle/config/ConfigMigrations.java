package net.dimensiontoggle.config;

import java.util.List;

// Versioned structural changes to the shipped YAML files - the things the
// automatic merge can't know: an option was renamed, moved or dropped.
//
// Adding a NEW option needs no migration at all (just put it into the
// default file). Add a migration only when an existing option changes its
// name/place or disappears:
//
//     new Migration("config.yml", 3, "rename x to y",
//             List.of(Step.rename(ConfigUpdater.join("old", "path"),
//                                 ConfigUpdater.join("new", "path"))))
//
// Versions count up per file. Every step is idempotent (rename only runs if
// the old option exists and the new one doesn't, remove only if the option
// exists), so running a migration twice - or on a file that never had the
// old option - is harmless.
public final class ConfigMigrations {

    public sealed interface Step permits Rename, Remove {

        static Step rename(String oldPath, String newPath) {
            return new Rename(oldPath, newPath);
        }

        static Step remove(String path) {
            return new Remove(path);
        }
    }

    public record Rename(String oldPath, String newPath) implements Step {
    }

    public record Remove(String path) implements Step {
    }

    public record Migration(String resourcePath, int version, String description, List<Step> steps) {
    }

    private static final List<Step> WEBSITE_MESSAGES = List.of(
            Step.remove(ConfigUpdater.join("help-web")),
            Step.remove(ConfigUpdater.join("web-link")),
            Step.remove(ConfigUpdater.join("web-disabled")),
            Step.remove(ConfigUpdater.join("web-token-reset")));

    public static final List<Migration> ALL = List.of(
            // 1.2.0 switched each channel on/off under <feature>.notify; since
            // 1.3.0 a single "notification" field picks the channel and the
            // texts live under <feature>.messages. The old sections are no
            // longer read by anything.
            new Migration("config.yml", 2, "remove the unused notify sections from 1.2.0", List.of(
                    Step.remove(ConfigUpdater.join("maintenance", "notify")),
                    Step.remove(ConfigUpdater.join("schedule", "notify")),
                    Step.remove(ConfigUpdater.join("lockdown", "notify")))),
            // The performance website of the 3.3.0 dev builds was removed
            // again (with /dt web and its GUI button).
            new Migration("config.yml", 3, "remove the performance website", List.of(
                    Step.remove(ConfigUpdater.join("web")))),
            new Migration("languages/en/messages.yml", 2, "remove the performance website texts",
                    WEBSITE_MESSAGES),
            new Migration("languages/de/messages.yml", 2, "remove the performance website texts",
                    WEBSITE_MESSAGES),
            new Migration("gui/overworld/config.yml", 2, "remove the performance website button", List.of(
                    Step.remove(ConfigUpdater.join("panel", "web"))))
    );

    private ConfigMigrations() {
    }

    // The version a file is at once all its migrations have run (1 if it
    // has none).
    public static int latestVersion(String resourcePath) {
        int latest = 1;
        for (Migration migration : ALL) {
            if (migration.resourcePath().equals(resourcePath)) {
                latest = Math.max(latest, migration.version());
            }
        }
        return latest;
    }

    public static List<Migration> pending(String resourcePath, int fromVersion) {
        return ALL.stream()
                .filter(m -> m.resourcePath().equals(resourcePath) && m.version() > fromVersion)
                .sorted(java.util.Comparator.comparingInt(Migration::version))
                .toList();
    }
}

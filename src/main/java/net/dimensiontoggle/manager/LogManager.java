package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.model.ToggleDimension;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogManager {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DimensionToggle plugin;

    public LogManager(DimensionToggle plugin) {
        this.plugin = plugin;
        ensureLogFileExistsNow();
    }

    private void ensureLogFileExistsNow() {
        File logFile = resolveLogFile();
        if (logFile == null || logFile.exists()) {
            return;
        }
        try {
            logFile.createNewFile();
        } catch (IOException e) {
            plugin.getLogger().warning("Could not pre-create the log file: " + e.getMessage());
        }
    }

    public void log(CommandSender actor, String action, ToggleDimension dimension, String details) {
        if (!plugin.getConfigManager().getConfig().getBoolean("logging.enabled", true)) {
            return;
        }

        File logFile = resolveLogFile();
        if (logFile == null) {
            return;
        }

        String actorName = actor == null ? "SYSTEM" : actor.getName();
        String dimensionName = dimension == null ? "-" : dimension.getKey().toUpperCase();
        String timestamp = LocalDateTime.now().format(FORMAT);

        String line = "[" + timestamp + "] " + actorName + " -> " + action
                + " | Dimension: " + dimensionName
                + (details == null || details.isBlank() ? "" : " | " + details);

        // The actual disk write is the only part that can block on a slow/busy
        // disk, so that's the only part pushed off the main thread - everything
        // above (config reads, formatting) still happens on the calling thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeLine(logFile, line));
    }

    private void writeLine(File logFile, String line) {
        try (FileWriter fw = new FileWriter(logFile, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(line);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write to the log file: " + e.getMessage());
        }
    }

    private File resolveLogFile() {
        String relativePath = plugin.getConfigManager().getConfig()
                .getString("logging.file", "logs/dimensiontoggle.log");

        File logFile = new File(plugin.getDataFolder(), relativePath);
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create the log folder: " + parent.getAbsolutePath());
            return null;
        }
        return logFile;
    }
}

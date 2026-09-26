package net.dimensiontoggle.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

// Writes go to a temp file in the same directory first, then replace the
// target in one rename. A crash or power loss mid-write leaves either the old
// file or the new one on disk - never a truncated half, which Bukkit's YAML
// loader would otherwise read back as an empty config and silently reset.
public final class AtomicFiles {

    private AtomicFiles() {
    }

    public static void write(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // Same output as Files.write(path, lines): every line, including the last,
    // followed by the platform line separator.
    public static void write(Path target, List<String> lines) throws IOException {
        StringBuilder content = new StringBuilder();
        for (String line : lines) {
            content.append(line).append(System.lineSeparator());
        }
        write(target, content.toString());
    }
}

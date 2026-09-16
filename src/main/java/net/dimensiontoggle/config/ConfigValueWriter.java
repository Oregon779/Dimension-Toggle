package net.dimensiontoggle.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ConfigValueWriter {

    private ConfigValueWriter() {
    }

    public static boolean setLimit(Path file, String dimensionKey, int maxPlayers) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        String currentTop = null;
        String currentSecond = null;
        boolean changed = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int indent = leadingSpaces(line);

            if (indent == 0 && trimmed.contains(":")) {
                currentTop = trimmed.substring(0, trimmed.indexOf(':'));
                currentSecond = null;
                continue;
            }

            if (indent == 2 && "limits".equals(currentTop) && trimmed.contains(":")) {
                currentSecond = trimmed.substring(0, trimmed.indexOf(':'));
                continue;
            }

            if ("limits".equals(currentTop) && dimensionKey.equals(currentSecond)) {
                String indentStr = line.substring(0, indent);
                if (trimmed.startsWith("max-players:")) {
                    lines.set(i, indentStr + "max-players: " + maxPlayers);
                    changed = true;
                } else if (trimmed.startsWith("enabled:")) {
                    lines.set(i, indentStr + "enabled: true");
                    changed = true;
                }
            }
        }

        if (changed) {
            Files.write(file, lines, StandardCharsets.UTF_8);
        }
        return changed;
    }

    public static boolean setNestedValue(Path file, String rawValue, String... path) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        int rangeStart = 0;
        int rangeEnd = lines.size();
        int indent = 0;

        for (int i = 0; i < path.length - 1; i++) {
            int[] bounds = findKeyBounds(lines, rangeStart, rangeEnd, indent, path[i]);
            if (bounds == null) {
                return false;
            }
            rangeStart = bounds[0] + 1;
            rangeEnd = bounds[1];
            indent += 2;
        }

        String leafKey = path[path.length - 1];
        for (int i = rangeStart; i < rangeEnd; i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (leadingSpaces(line) == indent && trimmed.startsWith(leafKey + ":")) {
                String indentStr = line.substring(0, indent);
                lines.set(i, indentStr + leafKey + ": " + rawValue);
                Files.write(file, lines, StandardCharsets.UTF_8);
                return true;
            }
        }
        return false;
    }

    private static int[] findKeyBounds(List<String> lines, int rangeStart, int rangeEnd, int indent, String key) {
        int start = -1;
        for (int i = rangeStart; i < rangeEnd; i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (leadingSpaces(line) == indent && trimmed.startsWith(key + ":")) {
                start = i;
                break;
            }
        }
        if (start == -1) {
            return null;
        }
        int end = rangeEnd;
        for (int i = start + 1; i < rangeEnd; i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            if (leadingSpaces(line) <= indent) {
                end = i;
                break;
            }
        }
        return new int[]{start, end};
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }
}

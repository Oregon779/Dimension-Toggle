package net.dimensiontoggle.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigUpdater {

    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^([A-Za-z0-9_.-]+):.*$");

    private ConfigUpdater() {
    }

    public static boolean update(Path userFile, InputStream defaultResource, String updateHeaderComment) throws IOException {
        List<String> userLines = Files.readAllLines(userFile, StandardCharsets.UTF_8);
        List<String> defaultLines = readAll(defaultResource);

        Set<String> existingKeys = extractTopLevelKeys(userLines);
        List<List<String>> missingBlocks = extractMissingBlocks(defaultLines, existingKeys);

        if (missingBlocks.isEmpty()) {
            return false;
        }

        List<String> output = new ArrayList<>(userLines);

        if (!output.isEmpty() && !output.get(output.size() - 1).isBlank()) {
            output.add("");
        }
        output.add("");
        for (String headerLine : updateHeaderComment.split("\n")) {
            output.add(headerLine);
        }

        for (List<String> block : missingBlocks) {
            output.add("");
            output.addAll(block);
        }

        Files.write(userFile, output, StandardCharsets.UTF_8);
        return true;
    }

    public static boolean ensureNestedPath(Path userFile, InputStream defaultResource, String dotPath) throws IOException {
        String[] parts = dotPath.split("\\.");
        List<String> userLines = Files.readAllLines(userFile, StandardCharsets.UTF_8);

        int[] userRange = {0, userLines.size()};
        int indent = 0;
        for (int p = 0; p < parts.length - 1; p++) {
            int[] bounds = findKeySectionBounds(userLines, userRange[0], userRange[1], indent, parts[p]);
            if (bounds == null) {
                return false;
            }
            userRange = new int[]{bounds[0] + 1, bounds[1]};
            indent += 2;
        }

        String leafKey = parts[parts.length - 1];
        if (findKeySectionBounds(userLines, userRange[0], userRange[1], indent, leafKey) != null) {
            return false;
        }

        List<String> defaultLines = readAll(defaultResource);
        int[] defRange = {0, defaultLines.size()};
        int defIndent = 0;
        for (int p = 0; p < parts.length - 1; p++) {
            int[] bounds = findKeySectionBounds(defaultLines, defRange[0], defRange[1], defIndent, parts[p]);
            if (bounds == null) {
                return false;
            }
            defRange = new int[]{bounds[0] + 1, bounds[1]};
            defIndent += 2;
        }

        int[] leafBounds = findKeySectionBounds(defaultLines, defRange[0], defRange[1], defIndent, leafKey);
        if (leafBounds == null) {
            return false;
        }
        int blockStart = leafBounds[0];
        while (blockStart > defRange[0]
                && leadingSpaces(defaultLines.get(blockStart - 1)) == defIndent
                && defaultLines.get(blockStart - 1).trim().startsWith("#")) {
            blockStart--;
        }
        List<String> block = new ArrayList<>(defaultLines.subList(blockStart, leafBounds[1]));
        while (!block.isEmpty() && block.get(block.size() - 1).isBlank()) {
            block.remove(block.size() - 1);
        }
        if (block.isEmpty()) {
            return false;
        }

        int insertAt = userRange[1];
        while (insertAt > userRange[0] && userLines.get(insertAt - 1).isBlank()) {
            insertAt--;
        }

        List<String> output = new ArrayList<>(userLines.subList(0, insertAt));
        output.addAll(block);
        output.addAll(userLines.subList(insertAt, userLines.size()));

        Files.write(userFile, output, StandardCharsets.UTF_8);
        return true;
    }

    private static int[] findKeySectionBounds(List<String> lines, int rangeStart, int rangeEnd, int indent, String key) {
        int start = -1;
        for (int i = rangeStart; i < rangeEnd; i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }
            if (leadingSpaces(line) == indent && line.trim().startsWith(key + ":")) {
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

    private static List<String> readAll(InputStream in) throws IOException {
        List<String> lines = new ArrayList<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static Set<String> extractTopLevelKeys(List<String> lines) {
        Set<String> keys = new HashSet<>();
        for (String line : lines) {
            if (isTopLevelKeyLine(line)) {
                keys.add(topLevelKeyName(line));
            }
        }
        return keys;
    }

    private static boolean isTopLevelKeyLine(String line) {
        if (line.isEmpty() || line.startsWith(" ") || line.startsWith("\t") || line.startsWith("#")) {
            return false;
        }
        return TOP_LEVEL_KEY.matcher(line).matches();
    }

    private static String topLevelKeyName(String line) {
        Matcher matcher = TOP_LEVEL_KEY.matcher(line);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static List<List<String>> extractMissingBlocks(List<String> defaultLines, Set<String> existingKeys) {
        List<Integer> keyLineIndices = new ArrayList<>();
        List<String> keyNames = new ArrayList<>();

        for (int i = 0; i < defaultLines.size(); i++) {
            if (isTopLevelKeyLine(defaultLines.get(i))) {
                keyLineIndices.add(i);
                keyNames.add(topLevelKeyName(defaultLines.get(i)));
            }
        }

        List<List<String>> blocks = new ArrayList<>();

        for (int k = 0; k < keyLineIndices.size(); k++) {
            int keyLine = keyLineIndices.get(k);
            String keyName = keyNames.get(k);

            int blockStart = keyLine;
            while (blockStart > 0 && defaultLines.get(blockStart - 1).startsWith("#")) {
                blockStart--;
            }

            int blockEnd;
            if (k + 1 < keyLineIndices.size()) {
                int nextKeyLine = keyLineIndices.get(k + 1);
                int nextBlockStart = nextKeyLine;
                while (nextBlockStart > 0 && defaultLines.get(nextBlockStart - 1).startsWith("#")) {
                    nextBlockStart--;
                }
                blockEnd = nextBlockStart;
            } else {
                blockEnd = defaultLines.size();
            }

            if (!existingKeys.contains(keyName)) {
                List<String> block = new ArrayList<>(defaultLines.subList(blockStart, blockEnd));

                while (!block.isEmpty() && block.get(block.size() - 1).isBlank()) {
                    block.remove(block.size() - 1);
                }
                blocks.add(block);
            }
        }

        return blocks;
    }
}

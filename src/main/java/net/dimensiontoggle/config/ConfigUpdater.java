package net.dimensiontoggle.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Brings a user's YAML file up to date with the plugin's default file
// without rewriting it: every missing option is copied from the default
// file as *text* (with its comments) into the right section, right after
// the option that precedes it in the default file, using the user's own
// indentation. Nothing that exists is changed, so comments, formatting,
// ordering and customised values survive.
//
// Every single edit is verified by re-reading the result with the real YAML
// parser: it must still parse, every value the user had must be unchanged,
// and the new option must carry the default value. An edit that fails the
// check is dropped (reported in Result#skipped) instead of risking the file.
public final class ConfigUpdater {

    // Paths use this separator internally so keys containing dots can never
    // be confused with nesting.
    static final char SEP = '\u001F';

    public record Result(List<String> lines, List<String> added, List<String> skipped) {

        public boolean changed() {
            return !added.isEmpty();
        }
    }

    private ConfigUpdater() {
    }

    // -------------------------------------------------------------------
    //   Parsing helpers
    // -------------------------------------------------------------------

    static YamlConfiguration parse(List<String> lines) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator(SEP);
        yaml.loadFromString(String.join("\n", lines));
        return yaml;
    }

    // All option paths of a file (sections and values), parents first, in
    // file order - in the "a<SEP>b" form used throughout this class.
    public static List<String> paths(List<String> lines) throws InvalidConfigurationException {
        return new ArrayList<>(parse(lines).getKeys(true));
    }

    static List<String> split(String path) {
        return Arrays.asList(path.split(String.valueOf(SEP), -1));
    }

    // "a<SEP>b" -> "a.b" for log messages.
    public static String display(String path) {
        return path.replace(SEP, '.');
    }

    public static String join(String... keys) {
        return String.join(String.valueOf(SEP), keys);
    }

    private static Map<String, Object> leaves(ConfigurationSection section) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : section.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection)) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        return values;
    }

    // -------------------------------------------------------------------
    //   Merge
    // -------------------------------------------------------------------

    // eligible: the paths that may be added if missing (null = all). Used to
    // only add options that are new in this plugin version, so options an
    // admin deleted on purpose stay deleted.
    public static Result merge(List<String> userLines, List<String> defaultLines, Collection<String> eligible) {
        List<String> lines = new ArrayList<>(userLines);
        List<String> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        YamlConfiguration defaults;
        YamlConfiguration user;
        try {
            defaults = parse(defaultLines);
            user = parse(lines);
        } catch (InvalidConfigurationException e) {
            skipped.add("(file is not valid YAML - left untouched: " + firstLine(e.getMessage()) + ")");
            return new Result(userLines, added, skipped);
        }
        Set<String> allowed = eligible == null ? null : new LinkedHashSet<>(eligible);
        YamlLines.Node defaultRoot = YamlLines.parse(defaultLines);

        for (String path : defaults.getKeys(true)) {
            if (user.contains(path, true) || insideAny(path, added)) {
                continue;
            }
            if (allowed != null && !allowed.contains(path)) {
                continue; // known before and missing now: deleted on purpose
            }
            List<String> keys = split(path);
            if (YamlLines.find(YamlLines.parse(lines), keys) != null) {
                // The key is written there but empty ("key:" / "key: ~"), so
                // YAML reports it as unset. Never write it a second time -
                // its missing children (if any) are filled in under it.
                continue;
            }
            String parentPath = String.join(String.valueOf(SEP), keys.subList(0, keys.size() - 1));
            if (!parentPath.isEmpty() && !user.isConfigurationSection(parentPath)
                    && !isEmptySectionHeader(lines, keys.subList(0, keys.size() - 1))) {
                // The section around it was removed or replaced by a value;
                // recreating it would undo the admin's change.
                continue;
            }
            List<String> candidate = insert(lines, defaultLines, defaultRoot, keys);
            if (candidate == null) {
                skipped.add(display(path));
                continue;
            }
            YamlConfiguration merged = verify(candidate, user, defaults, path);
            if (merged == null) {
                skipped.add(display(path));
                continue;
            }
            lines = candidate;
            user = merged;
            added.add(path);
        }
        return new Result(added.isEmpty() ? userLines : lines, added, skipped);
    }

    // "section:" left without any options - YAML reads it as an empty value,
    // but it is still the place where that section's options belong.
    private static boolean isEmptySectionHeader(List<String> lines, List<String> keys) {
        YamlLines.Node node = YamlLines.find(YamlLines.parse(lines), keys);
        return node != null && node.emptyValue && node.children.isEmpty();
    }

    private static boolean insideAny(String path, List<String> parents) {
        for (String parent : parents) {
            if (path.startsWith(parent + SEP)) {
                return true;
            }
        }
        return false;
    }

    // The new text with the default block for `keys` inserted, or null if
    // the place can't be determined.
    private static List<String> insert(List<String> lines, List<String> defaultLines, YamlLines.Node defaultRoot,
                                       List<String> keys) {
        YamlLines.Node defaultNode = YamlLines.find(defaultRoot, keys);
        if (defaultNode == null) {
            return null;
        }
        YamlLines.Node userRoot = YamlLines.parse(lines);
        YamlLines.Node userParent = YamlLines.find(userRoot, keys.subList(0, keys.size() - 1));
        if (userParent == null) {
            return null;
        }

        // Where: after the closest option that comes before it in the default
        // file and exists in the user's file.
        List<YamlLines.Node> defaultSiblings = defaultNode.parent.children;
        int position = defaultSiblings.indexOf(defaultNode);
        YamlLines.Node anchor = null;
        for (int i = position - 1; i >= 0 && anchor == null; i--) {
            anchor = userParent.child(defaultSiblings.get(i).key);
        }
        boolean topLevel = userParent.isRoot();
        int insertAt;
        if (anchor != null) {
            insertAt = anchor.end;
        } else if (!userParent.children.isEmpty()) {
            insertAt = topLevel ? userParent.children.get(0).commentStart(lines) : userParent.keyLine + 1;
        } else {
            insertAt = topLevel ? lines.size() : userParent.keyLine + 1;
        }

        // What: the default block including the comments/banners between the
        // previous option and this one.
        int regionStart;
        if (position > 0) {
            regionStart = defaultSiblings.get(position - 1).end;
        } else if (defaultNode.parent.isRoot()) {
            regionStart = defaultNode.commentStart(defaultLines);
        } else {
            regionStart = defaultNode.parent.keyLine + 1;
        }
        List<String> block = new ArrayList<>(defaultLines.subList(regionStart, defaultNode.end));
        boolean leadingBlank = false;
        while (!block.isEmpty() && block.get(0).isBlank()) {
            block.remove(0);
            leadingBlank = true;
        }
        if (block.isEmpty()) {
            return null;
        }

        // How deep: like the user's existing options in that section.
        int targetIndent;
        if (!userParent.children.isEmpty()) {
            targetIndent = userParent.children.get(0).indent;
        } else if (topLevel) {
            targetIndent = 0;
        } else {
            int defaultStep = defaultNode.indent - Math.max(0, defaultNode.parent.indent);
            targetIndent = userParent.indent + Math.max(2, defaultStep);
        }
        block = YamlLines.reindent(block, targetIndent - defaultNode.indent);

        if ((leadingBlank || topLevel) && anchor != null) {
            block.add(0, "");
        }
        if (topLevel && insertAt < lines.size() && !lines.get(insertAt).isBlank()) {
            block.add("");
        }

        List<String> result = new ArrayList<>(lines.size() + block.size());
        result.addAll(lines.subList(0, insertAt));
        result.addAll(block);
        result.addAll(lines.subList(insertAt, lines.size()));
        return result;
    }

    // The parsed result if `candidate` keeps every old value and adds `path`
    // with its default value, otherwise null.
    private static YamlConfiguration verify(List<String> candidate, YamlConfiguration before,
                                            YamlConfiguration defaults, String path) {
        YamlConfiguration after;
        try {
            after = parse(candidate);
        } catch (InvalidConfigurationException e) {
            return null;
        }
        for (Map.Entry<String, Object> old : leaves(before).entrySet()) {
            if (!Objects.equals(old.getValue(), after.get(old.getKey()))) {
                return null;
            }
        }
        Object expected = defaults.get(path);
        if (expected instanceof ConfigurationSection section) {
            ConfigurationSection actual = after.getConfigurationSection(path);
            if (actual == null) {
                return null;
            }
            for (Map.Entry<String, Object> leaf : leaves(section).entrySet()) {
                if (!Objects.equals(leaf.getValue(), actual.get(leaf.getKey()))) {
                    return null;
                }
            }
        } else if (!Objects.equals(expected, after.get(path))) {
            return null;
        }
        return after;
    }

    // -------------------------------------------------------------------
    //   Edits used by versioned migrations
    // -------------------------------------------------------------------

    // Removes an option (with the comments directly above it). Returns null
    // if the option doesn't exist or the result wouldn't be valid.
    public static List<String> remove(List<String> lines, String path) {
        YamlLines.Node node = YamlLines.find(YamlLines.parse(lines), split(path));
        if (node == null) {
            return null;
        }
        int from = node.commentStart(lines);
        int to = node.end;
        // Swallow one blank line so no double gap is left behind.
        if (to < lines.size() && lines.get(to).isBlank() && (from == 0 || lines.get(from - 1).isBlank())) {
            to++;
        }
        List<String> result = new ArrayList<>(lines.subList(0, from));
        result.addAll(lines.subList(to, lines.size()));
        try {
            YamlConfiguration before = parse(lines);
            YamlConfiguration after = parse(result);
            for (Map.Entry<String, Object> old : leaves(before).entrySet()) {
                boolean removedHere = old.getKey().equals(path) || old.getKey().startsWith(path + SEP);
                if (!removedHere && !Objects.equals(old.getValue(), after.get(old.getKey()))) {
                    return null;
                }
            }
        } catch (InvalidConfigurationException e) {
            return null;
        }
        return result;
    }

    // Moves an option (value, children and comments) to a new path, keeping
    // the admin's value. Only if the old option exists and the new one
    // doesn't yet; the new parent section must exist. Returns null otherwise.
    public static List<String> rename(List<String> lines, String oldPath, String newPath) {
        YamlConfiguration before;
        try {
            before = parse(lines);
        } catch (InvalidConfigurationException e) {
            return null;
        }
        if (!before.contains(oldPath, true) || before.contains(newPath, true)) {
            return null;
        }
        List<String> newKeys = split(newPath);
        String newParent = String.join(String.valueOf(SEP), newKeys.subList(0, newKeys.size() - 1));
        if (!newParent.isEmpty() && !before.isConfigurationSection(newParent)) {
            return null;
        }

        YamlLines.Node root = YamlLines.parse(lines);
        YamlLines.Node node = YamlLines.find(root, split(oldPath));
        if (node == null) {
            return null;
        }
        List<String> block = new ArrayList<>(lines.subList(node.commentStart(lines), node.end));
        int keyOffset = node.keyLine - node.commentStart(lines);
        String keyLine = block.get(keyOffset);
        int colon = keyColon(keyLine);
        if (colon < 0) {
            return null;
        }
        block.set(keyOffset, " ".repeat(node.indent) + YamlLines.writeKey(newKeys.get(newKeys.size() - 1))
                + keyLine.substring(colon));

        List<String> without = remove(lines, oldPath);
        if (without == null) {
            return null;
        }
        YamlLines.Node newRoot = YamlLines.parse(without);
        YamlLines.Node parent = YamlLines.find(newRoot, newKeys.subList(0, newKeys.size() - 1));
        if (parent == null) {
            return null;
        }
        int targetIndent = !parent.children.isEmpty() ? parent.children.get(0).indent
                : parent.isRoot() ? 0 : parent.indent + 2;
        block = YamlLines.reindent(block, targetIndent - node.indent);
        int insertAt = !parent.children.isEmpty() ? parent.children.get(parent.children.size() - 1).end
                : parent.isRoot() ? without.size() : parent.keyLine + 1;
        List<String> result = new ArrayList<>(without.subList(0, insertAt));
        result.addAll(block);
        result.addAll(without.subList(insertAt, without.size()));
        try {
            YamlConfiguration after = parse(result);
            if (!Objects.equals(valueOf(before, oldPath), valueOf(after, newPath))) {
                return null;
            }
        } catch (InvalidConfigurationException e) {
            return null;
        }
        return result;
    }

    private static Object valueOf(YamlConfiguration yaml, String path) {
        Object value = yaml.get(path);
        return value instanceof ConfigurationSection section ? leaves(section) : value;
    }

    // Position of the ':' that ends the key on a key line.
    private static int keyColon(String line) {
        String trimmed = line.trim();
        int offset = line.indexOf(trimmed);
        if (trimmed.startsWith("\"") || trimmed.startsWith("'")) {
            char quote = trimmed.charAt(0);
            for (int i = 1; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (c == '\\' && quote == '"') {
                    i++;
                } else if (c == quote) {
                    int colon = trimmed.indexOf(':', i);
                    return colon < 0 ? -1 : offset + colon;
                }
            }
            return -1;
        }
        int colon = trimmed.indexOf(':');
        return colon < 0 ? -1 : offset + colon;
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}

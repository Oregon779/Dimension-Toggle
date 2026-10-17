package net.dimensiontoggle.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A structural view of a YAML file's *lines*: which line holds which key,
// how deep it is indented and where its block ends. Lets ConfigUpdater edit
// the text surgically (insert/remove/rename one key) so comments, blank
// lines, ordering and quoting of everything else stay exactly as they were.
//
// It understands the subset of YAML that plugin configs use - block
// mappings, scalar values, block/flow lists, block scalars (| and >) - and
// never needs to understand more: every edit is verified afterwards by
// re-reading the result with the real YAML parser.
final class YamlLines {

    // key: value   |   "quoted key": value   |   'quoted key': value
    private static final Pattern KEY_LINE = Pattern.compile(
            "^( *)(?:\"((?:[^\"\\\\]|\\\\.)*)\"|'((?:[^']|'')*)'|([^\\s#'\"\\-][^:#]*?|-[^\\s:#][^:#]*?))\\s*:(?:\\s+(.*))?$");
    private static final Pattern BLOCK_SCALAR = Pattern.compile("^[|>][-+0-9]*\\s*(#.*)?$");

    static final class Node {
        final String key;
        final int keyLine;
        final int indent;
        final Node parent;
        final List<Node> children = new ArrayList<>();
        // "key:" with nothing (or only a comment) after it.
        boolean emptyValue;
        // Exclusive: the line after the last line that belongs to this key
        // (its value, children, list items, deeper comments) - trailing
        // blank lines and comments of the next sibling are not included.
        int end;

        Node(String key, int keyLine, int indent, Node parent) {
            this.key = key;
            this.keyLine = keyLine;
            this.indent = indent;
            this.parent = parent;
        }

        boolean isRoot() {
            return parent == null;
        }

        Node child(String childKey) {
            for (Node child : children) {
                if (child.key.equals(childKey)) {
                    return child;
                }
            }
            return null;
        }

        // Index of the first line of the comment block directly above the key
        // (no blank line in between) - those comments describe the key.
        int commentStart(List<String> lines) {
            int start = keyLine;
            while (start > 0) {
                String above = lines.get(start - 1);
                if (above.isBlank() || !above.trim().startsWith("#")) {
                    break;
                }
                start--;
            }
            return start;
        }
    }

    private YamlLines() {
    }

    static Node parse(List<String> lines) {
        Node root = new Node("", -1, -1, null);
        root.end = lines.size();
        List<Node> stack = new ArrayList<>();
        stack.add(root);
        List<Node> all = new ArrayList<>();

        int blockScalarIndent = -1;
        int listItemIndent = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = indentOf(line);
            String trimmed = line.trim();
            if (blockScalarIndent >= 0) {
                if (indent > blockScalarIndent) {
                    continue; // text of a | or > scalar, not structure
                }
                blockScalarIndent = -1;
            }
            if (trimmed.startsWith("#") || trimmed.equals("---") || trimmed.equals("...")) {
                continue;
            }
            if (listItemIndent >= 0) {
                if (indent > listItemIndent) {
                    continue; // continuation of a list item
                }
                listItemIndent = -1;
            }
            if (trimmed.startsWith("- ") || trimmed.equals("-")) {
                listItemIndent = indent;
                continue;
            }
            Matcher matcher = KEY_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String key = matcher.group(2) != null ? matcher.group(2).replace("\\\"", "\"")
                    : matcher.group(3) != null ? matcher.group(3).replace("''", "'")
                    : matcher.group(4).trim();
            while (stack.get(stack.size() - 1).indent >= indent) {
                stack.remove(stack.size() - 1);
            }
            Node parent = stack.get(stack.size() - 1);
            Node node = new Node(key, i, indent, parent);
            parent.children.add(node);
            stack.add(node);
            all.add(node);
            String value = matcher.group(5);
            node.emptyValue = value == null || value.isBlank() || value.trim().startsWith("#");
            if (value != null && BLOCK_SCALAR.matcher(value.trim()).matches()) {
                blockScalarIndent = indent;
            }
        }
        for (Node node : all) {
            node.end = computeEnd(lines, node);
        }
        return root;
    }

    private static int computeEnd(List<String> lines, Node node) {
        int lastContent = node.keyLine;
        for (int i = node.keyLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = indentOf(line);
            String trimmed = line.trim();
            boolean sameIndentListItem = indent == node.indent && (trimmed.startsWith("- ") || trimmed.equals("-"));
            if (indent > node.indent || sameIndentListItem) {
                lastContent = i;
                continue;
            }
            break;
        }
        return lastContent + 1;
    }

    static Node find(Node root, List<String> path) {
        Node current = root;
        for (String key : path) {
            current = current.child(key);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    static int indentOf(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    // Shifts every non-blank line by delta spaces (negative = outdent, never
    // past column 0).
    static List<String> reindent(List<String> block, int delta) {
        if (delta == 0) {
            return new ArrayList<>(block);
        }
        List<String> result = new ArrayList<>(block.size());
        String pad = delta > 0 ? " ".repeat(delta) : "";
        for (String line : block) {
            if (line.isBlank()) {
                result.add("");
            } else if (delta > 0) {
                result.add(pad + line);
            } else {
                result.add(line.substring(Math.min(-delta, indentOf(line))));
            }
        }
        return result;
    }

    // A key as it must be written to stay readable as the same key.
    static String writeKey(String key) {
        boolean plain = !key.isEmpty() && key.matches("[A-Za-z0-9_][A-Za-z0-9_.\\- ]*") && !key.endsWith(" ");
        return plain ? key : "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}

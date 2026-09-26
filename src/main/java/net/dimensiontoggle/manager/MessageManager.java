package net.dimensiontoggle.manager;

import net.dimensiontoggle.DimensionToggle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageManager {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset")
    );

    // Parsed Components are immutable, so identical input can share one.
    // The open editor menus re-render every button once per second (names +
    // lore, dozens of lines per viewer) from mostly unchanged text; countdown
    // lines do change every second, hence a bounded LRU instead of a plain map.
    private static final int PARSE_CACHE_SIZE = 1024;

    private final DimensionToggle plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, Component> parseCache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
            return size() > PARSE_CACHE_SIZE;
        }
    };

    public MessageManager(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public Component parse(String raw) {
        if (raw == null) {
            return Component.empty();
        }
        synchronized (parseCache) {
            Component cached = parseCache.get(raw);
            if (cached != null) {
                return cached;
            }
        }
        Component parsed = miniMessage.deserialize(toMiniMessageFormat(raw));
        synchronized (parseCache) {
            parseCache.put(raw, parsed);
        }
        return parsed;
    }

    // Keys are the final text, so edited messages never hit stale entries;
    // this only drops what the old texts were holding on to.
    public void clearCache() {
        synchronized (parseCache) {
            parseCache.clear();
        }
    }

    private String toMiniMessageFormat(String input) {

        Matcher hexMatcher = HEX_PATTERN.matcher(input);
        StringBuilder afterHex = new StringBuilder();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(afterHex, Matcher.quoteReplacement("<#" + hexMatcher.group(1) + ">"));
        }
        hexMatcher.appendTail(afterHex);
        String withoutHex = afterHex.toString();

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < withoutHex.length(); i++) {
            char c = withoutHex.charAt(i);
            if (c == '&' && i + 1 < withoutHex.length()) {
                String tag = LEGACY_TAGS.get(Character.toLowerCase(withoutHex.charAt(i + 1)));
                if (tag != null) {
                    result.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            result.append(c);
        }
        return result.toString();
    }

    private String prefix() {
        return get("prefix", "");
    }

    public String get(String path, String def) {
        FileConfiguration messages = plugin.getConfigManager().getMessages();
        return messages.getString(path, def);
    }

    public String get(String path) {
        return get(path, "&c[Fehlende Nachricht: " + path + "]");
    }

    public String getDimensionName(String key) {
        return get("names." + key, key);
    }

    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (placeholders == null) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        String raw = prefix() + get(path);
        raw = replacePlaceholders(raw, placeholders);
        sender.sendMessage(parse(raw));
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, null);
    }

    // Same idea as the broadcast-path bulk helpers elsewhere: resolve and parse
    // the message once, then reuse the Component for every recipient instead of
    // repeating the placeholder substitution + MiniMessage parse per player.
    public void sendToAll(Collection<? extends Player> targets, String path, Map<String, String> placeholders) {
        if (targets.isEmpty()) {
            return;
        }
        String raw = prefix() + get(path);
        raw = replacePlaceholders(raw, placeholders);
        Component message = parse(raw);
        for (Player player : targets) {
            player.sendMessage(message);
        }
    }

    public String getFormatted(String path, Map<String, String> placeholders) {
        return replacePlaceholders(get(path), placeholders);
    }

    public String formatDuration(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        String hourUnit = get("unit-hours", "h");
        String minuteUnit = get("unit-minutes", "m");
        String secondUnit = get("unit-seconds", "s");

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append(' ').append(hourUnit).append(' ');
        }
        if (minutes > 0) {
            sb.append(minutes).append(' ').append(minuteUnit).append(' ');
        }
        if (seconds > 0 || sb.isEmpty()) {
            sb.append(seconds).append(' ').append(secondUnit);
        }
        return sb.toString().trim();
    }

    public String getPrefix() {
        return prefix();
    }

    public Component parseWithPlaceholders(String raw, Map<String, String> placeholders) {
        return parse(replacePlaceholders(raw, placeholders));
    }
}

package net.dimensiontoggle.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dimensiontoggle.DimensionToggle;
import net.dimensiontoggle.manager.MaintenanceManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;

// Asks an admin for an interval in the chat (the editor closes meanwhile):
// "15m", "2h", "1h30m" or a plain number of minutes; 0/off switches the
// feature off, "cancel" aborts. The answer never reaches the public chat.
// After an answer, a cancel or the timeout the menu the admin came from is
// opened again.
public class ChatInput implements Listener {

    public static final long TIMEOUT_TICKS = 30 * 20L;
    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 24 * 60;
    public static final int CANCEL = -1;
    public static final int INVALID = -2;

    private static final Set<String> CANCEL_WORDS = Set.of("cancel", "abbrechen", "stop", "exit");
    private static final Set<String> OFF_WORDS = Set.of("0", "off", "aus");

    // what: already resolved label (e.g. "Item cleaner"); current: the value now.
    public record Request(String what, String current, IntConsumer onMinutes, Runnable afterwards) {
    }

    private record Pending(Request request, BukkitTask timeout) {
    }

    private final DimensionToggle plugin;
    // Read from the async chat thread, changed on the main thread.
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInput(DimensionToggle plugin) {
        this.plugin = plugin;
    }

    public boolean isWaiting(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    public void ask(Player player, Request request) {
        UUID id = player.getUniqueId();
        clear(id);
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending current = pending.get(id);
            if (current != null && current.request() == request) {
                pending.remove(id);
                plugin.getMessageManager().send(player, "chat-input-timeout");
                plugin.getSoundManager().playGui(player, "error");
                reopen(player, request);
            }
        }, TIMEOUT_TICKS);
        pending.put(id, new Pending(request, timeout));
        plugin.getMessageManager().send(player, "chat-input-prompt",
                Map.of("what", request.what(), "current", request.current()));
        plugin.getMessageManager().send(player, "chat-input-hint");
    }

    // LOWEST: cancel before chat formatters/other plugins pick the message up.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending current = pending.get(player.getUniqueId());
        if (current == null) {
            return;
        }
        event.setCancelled(true);
        String typed = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> answer(player, current, typed));
    }

    void answer(Player player, Pending expected, String typed) {
        UUID id = player.getUniqueId();
        if (pending.get(id) != expected || !player.isOnline()) {
            return;   // timed out or replaced in the meantime
        }
        Request request = expected.request();
        int minutes = parseMinutes(typed);
        if (minutes == INVALID) {
            // Keep waiting - a typo shouldn't throw the admin out.
            plugin.getMessageManager().send(player, "chat-input-invalid");
            plugin.getSoundManager().playGui(player, "error");
            return;
        }
        clear(id);
        if (minutes == CANCEL) {
            plugin.getMessageManager().send(player, "chat-input-cancelled");
        } else {
            request.onMinutes().accept(minutes);
            String value = minutes == 0
                    ? plugin.getMessageManager().get("chat-input-off-word", "off")
                    : plugin.getMessageManager().formatDuration(minutes * 60);
            plugin.getMessageManager().send(player, "chat-input-set", Map.of("what", request.what(), "value", value));
            plugin.getSoundManager().playGui(player, minutes == 0 ? "toggle-off" : "toggle-on");
        }
        reopen(player, request);
    }

    private void reopen(Player player, Request request) {
        if (player.isOnline() && request.afterwards() != null) {
            request.afterwards().run();
        }
    }

    private void clear(UUID id) {
        Pending old = pending.remove(id);
        if (old != null) {
            old.timeout().cancel();
        }
    }

    // Minutes (1 - 1440), 0 for "off", CANCEL or INVALID.
    public static int parseMinutes(String input) {
        if (input == null) {
            return INVALID;
        }
        String text = input.trim().toLowerCase(Locale.ROOT);
        if (CANCEL_WORDS.contains(text)) {
            return CANCEL;
        }
        if (OFF_WORDS.contains(text)) {
            return 0;
        }
        Integer seconds = MaintenanceManager.parseDurationToSeconds(text.replace(" ", ""));
        if (seconds == null) {
            return INVALID;
        }
        int minutes = seconds / 60;
        return minutes < MIN_MINUTES || minutes > MAX_MINUTES || seconds % 60 != 0 ? INVALID : minutes;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    public void clearAll() {
        for (UUID id : Set.copyOf(pending.keySet())) {
            clear(id);
        }
    }
}

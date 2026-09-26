package net.dimensiontoggle.io;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

// One background thread for every file write the plugin makes (data.yml,
// config.yml value edits, the audit log). A single thread instead of
// Bukkit's async pool on purpose: writes to the same file can never overlap,
// and log lines land in the order the actions happened. Nothing submitted
// here may touch the Bukkit API - callers snapshot what they need first.
public final class IoExecutor {

    private final Logger logger;
    private final ExecutorService executor;

    public IoExecutor(Logger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "DimensionToggle-IO");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void execute(Runnable task) {
        Runnable guarded = () -> {
            try {
                task.run();
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "Background file write failed", t);
            }
        };
        try {
            executor.execute(guarded);
        } catch (RejectedExecutionException ex) {
            // Already shut down (plugin disabling) - still get it on disk.
            guarded.run();
        }
    }

    // Blocks until everything submitted so far has finished. Used before
    // re-reading files that may still have a queued write pending.
    public void drain(long timeoutMillis) {
        try {
            Future<?> marker = executor.submit(() -> { });
            marker.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ex) {
            // Shut down: nothing can still be pending.
        } catch (Exception ex) {
            logger.warning("Timed out waiting for pending file writes: " + ex.getMessage());
        }
    }

    public void shutdown(long timeoutMillis) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                logger.warning("Some pending file writes did not finish before shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

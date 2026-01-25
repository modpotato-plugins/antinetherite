package top.modpotato.restoration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import top.modpotato.Main;
import com.tcoded.folialib.wrapper.task.WrappedTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages restoration sessions and sends periodic progress updates
 */
public class RestorationProgressTracker {
    private final Main plugin;
    private final Map<UUID, RestorationSession> activeSessions;
    private final Map<UUID, Long> lastTimeUpdate; // sessionId -> last time-based update timestamp
    private final Map<UUID, Integer> lastPercentUpdate; // sessionId -> last percent milestone
    private final Set<UUID> globalOptOut; // Players who opted out of ALL feedback
    private WrappedTask task = null;

    // Configuration
    private static final long TIME_UPDATE_INTERVAL_MS = 60000; // 60 seconds
    private static final int HIGH_THRESHOLD = 1000; // >= 1000 locations -> 1% updates
    private static final int HIGH_THRESHOLD_PERCENT = 1; // 1%
    private static final int LOW_THRESHOLD_PERCENT = 10; // 10%

    /**
     * Creates a new restoration progress tracker
     * 
     * @param plugin The plugin instance
     */
    public RestorationProgressTracker(Main plugin) {
        this.plugin = plugin;
        this.activeSessions = new ConcurrentHashMap<>();
        this.lastTimeUpdate = new ConcurrentHashMap<>();
        this.lastPercentUpdate = new ConcurrentHashMap<>();
        this.globalOptOut = ConcurrentHashMap.newKeySet();
    }

    /**
     * Starts the progress tracking timer
     */
    public void start() {
        if (task != null) {
            return; // Already running
        }

        // Use FoliaLib to run a global timer task (runs every second = 20 ticks)
        task = plugin.getFoliaLib().getImpl().runTimer(this::checkAndSendUpdates, 20L, 20L);
    }

    /**
     * Stops the progress tracking timer
     */
    public void stop() {
        if (task == null) {
            return; // Not running
        }

        task.cancel();
        task = null;
    }

    /**
     * Registers a new restoration session
     * 
     * @param session The session to register
     */
    public void registerSession(RestorationSession session) {
        activeSessions.put(session.getSessionId(), session);
        lastTimeUpdate.put(session.getSessionId(), session.getStartTime());
        lastPercentUpdate.put(session.getSessionId(), 0);
    }

    /**
     * Marks a session as completed and sends final message
     * 
     * @param sessionId      The session ID
     * @param actualRestored The actual number of blocks restored (may differ from
     *                       completed if some were skipped)
     */
    public void completeSession(UUID sessionId, int actualRestored) {
        RestorationSession session = activeSessions.get(sessionId);
        if (session == null) {
            return;
        }

        session.markCompleted();

        long durationMs = session.getElapsedTimeMs();
        String duration = formatDuration(durationMs);

        Component message = Component.text("Restoration complete! Restored ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(actualRestored).color(NamedTextColor.GOLD))
                .append(Component.text(" Ancient Debris blocks in ").color(NamedTextColor.GREEN))
                .append(Component.text(duration).color(NamedTextColor.GOLD))
                .append(Component.text(".").color(NamedTextColor.GREEN));

        sendToSubscribers(session, message);

        // Clean up
        activeSessions.remove(sessionId);
        lastTimeUpdate.remove(sessionId);
        lastPercentUpdate.remove(sessionId);
    }

    /**
     * Opts a player in or out of global restoration feedback
     * 
     * @param playerUUID The player UUID
     * @param optOut     true to opt out, false to opt in
     */
    public void setGlobalOptOut(UUID playerUUID, boolean optOut) {
        if (optOut) {
            globalOptOut.add(playerUUID);
        } else {
            globalOptOut.remove(playerUUID);
        }
    }

    /**
     * Checks if a player is opted out globally
     * 
     * @param playerUUID The player UUID
     * @return true if opted out, false otherwise
     */
    public boolean isGlobalOptOut(UUID playerUUID) {
        return globalOptOut.contains(playerUUID);
    }

    /**
     * Checks all active sessions and sends updates as needed
     */
    private void checkAndSendUpdates() {
        long currentTime = System.currentTimeMillis();

        for (RestorationSession session : activeSessions.values()) {
            if (session.isCompleted()) {
                continue;
            }

            UUID sessionId = session.getSessionId();

            // Check time-based update (every 60 seconds)
            Long lastTime = lastTimeUpdate.get(sessionId);
            if (lastTime != null && (currentTime - lastTime) >= TIME_UPDATE_INTERVAL_MS) {
                sendProgressUpdate(session);
                lastTimeUpdate.put(sessionId, currentTime);
            }

            // Check percentage-based update
            double currentPercent = session.getCompletionPercentage();
            int lastPercent = lastPercentUpdate.getOrDefault(sessionId, 0);

            int threshold = session.getTotalLocations() >= HIGH_THRESHOLD ? HIGH_THRESHOLD_PERCENT
                    : LOW_THRESHOLD_PERCENT;
            int currentMilestone = ((int) currentPercent / threshold) * threshold;

            if (currentMilestone > lastPercent && currentMilestone <= 100) {
                sendProgressUpdate(session);
                lastPercentUpdate.put(sessionId, currentMilestone);
            }
        }
    }

    /**
     * Sends a progress update for a session
     * 
     * @param session The session
     */
    private void sendProgressUpdate(RestorationSession session) {
        int completed = session.getCompletedLocations();
        int total = session.getTotalLocations();
        double percent = session.getCompletionPercentage();
        long elapsedMs = session.getElapsedTimeMs();
        String elapsed = formatDuration(elapsedMs);

        Component message = Component.text("Restoration progress: ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(completed).color(NamedTextColor.GOLD))
                .append(Component.text("/").color(NamedTextColor.YELLOW))
                .append(Component.text(total).color(NamedTextColor.GOLD))
                .append(Component.text(String.format(" (%.1f%%) - ", percent)).color(NamedTextColor.YELLOW))
                .append(Component.text(elapsed).color(NamedTextColor.GOLD))
                .append(Component.text(" elapsed").color(NamedTextColor.YELLOW));

        sendToSubscribers(session, message);
    }

    /**
     * Sends a message to all subscribers of a session (except those who opted out
     * globally)
     * 
     * @param session The session
     * @param message The message to send
     */
    private void sendToSubscribers(RestorationSession session, Component message) {
        for (UUID subscriberUUID : session.getSubscribers()) {
            // Skip if opted out globally
            if (globalOptOut.contains(subscriberUUID)) {
                continue;
            }

            Player player = Bukkit.getPlayer(subscriberUUID);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }

        // Also send to console if initiator is console
        if (session.getInitiatorUUID() == null) {
            Bukkit.getConsoleSender().sendMessage(message);
        }
    }

    /**
     * Formats a duration in milliseconds to a human-readable string
     * 
     * @param durationMs The duration in milliseconds
     * @return The formatted duration
     */
    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
}

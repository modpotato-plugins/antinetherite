package top.modpotato.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import top.modpotato.Main;
import top.modpotato.config.Config;
import top.modpotato.util.NetheriteDetector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;

import com.tcoded.folialib.wrapper.task.WrappedTask;

/**
 * Scheduler for removing Netherite items from player inventories
 */
public class NetheriteRemover {
    private final Main plugin;
    private final NetheriteDetector netheriteDetector;
    private final Logger logger;
    private final Config config;

    // Task references
    private WrappedTask globalTask;
    private final Map<UUID, WrappedTask> playerTasks;

    /**
     * Creates a new NetheriteRemover
     * 
     * @param plugin            The plugin instance
     * @param isFolia           Whether the server is running on Folia (unused now
     *                          as FoliaLib handles it)
     * @param netheriteDetector The Netherite detector
     * @param config            The plugin configuration
     */
    public NetheriteRemover(Main plugin, boolean isFolia, NetheriteDetector netheriteDetector, Config config) {
        this.plugin = plugin;
        this.netheriteDetector = netheriteDetector;
        this.logger = plugin.getLogger();
        this.config = config;
        this.playerTasks = new ConcurrentHashMap<>();
    }

    /**
     * Starts the Netherite removal task
     * 
     * @param delay The delay between checks in ticks
     */
    public void start(int delay) {
        stop(); // Stop any existing tasks

        // On Folia, we use per-player tasks for better performance and thread safety
        // On Bukkit, we use a single global task
        if (plugin.getFoliaLib().isFolia()) {
            startFoliaTasks(delay);
        } else {
            startBukkitTask(delay);
        }
    }

    /**
     * Stops the Netherite removal task
     */
    public void stop() {
        if (globalTask != null) {
            globalTask.cancel();
            globalTask = null;
        }

        for (WrappedTask task : playerTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        playerTasks.clear();
    }

    /**
     * Starts the Netherite removal task using Bukkit scheduler logic via FoliaLib
     * 
     * @param delay The delay between checks in ticks
     */
    private void startBukkitTask(int delay) {
        globalTask = plugin.getFoliaLib().getImpl().runTimer(() -> {
            AtomicInteger removedCount = new AtomicInteger(0);

            for (Player player : Bukkit.getOnlinePlayers()) {
                checkPlayerInventory(player, removedCount);
            }

            if (removedCount.get() > 0 && config.isLogInventoryRemovals()) {
                logger.info("Removed " + removedCount.get() + " Netherite items from player inventories");
            }
        }, delay, delay);
    }

    /**
     * Starts the Netherite removal task using Folia scheduler logic via FoliaLib
     * 
     * @param delay The delay between checks in ticks
     */
    private void startFoliaTasks(int delay) {
        // Schedule a task for each online player
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayerTask(player, delay);
        }

        // Schedule a global task to handle new players and cleanup
        globalTask = plugin.getFoliaLib().getImpl().runTimer(() -> {
            // Clean up tasks for offline players
            playerTasks.entrySet().removeIf(entry -> {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) {
                    entry.getValue().cancel();
                    return true;
                }
                return false;
            });

            // Schedule tasks for new players
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!playerTasks.containsKey(player.getUniqueId())) {
                    schedulePlayerTask(player, delay);
                }
            }
        }, 1L, (long) delay);
    }

    /**
     * Schedules a task for a specific player
     * 
     * @param player The player to schedule for
     * @param delay  The delay between checks in ticks
     */
    private void schedulePlayerTask(Player player, int delay) {
        UUID playerId = player.getUniqueId();

        // Cancel existing task if present
        WrappedTask existingTask = playerTasks.get(playerId);
        if (existingTask != null) {
            existingTask.cancel();
        }

        WrappedTask task = plugin.getFoliaLib().getImpl().runAtEntityTimer(player, () -> {
            AtomicInteger removedCount = new AtomicInteger(0);
            checkPlayerInventory(player, removedCount);

            if (removedCount.get() > 0 && config.isLogInventoryRemovals()) {
                logger.info(
                        "Removed " + removedCount.get() + " Netherite items from " + player.getName() + "'s inventory");
            }

            // If the player is offline, cancel this task and remove from map
            if (!player.isOnline()) {
                WrappedTask t = playerTasks.remove(playerId);
                if (t != null) {
                    t.cancel();
                }
            }
        }, 1L, (long) delay);

        playerTasks.put(playerId, task);
    }

    /**
     * Checks a player's inventory for Netherite items and removes them
     * 
     * @param player       The player to check
     * @param removedCount Counter for removed items
     */
    private void checkPlayerInventory(Player player, AtomicInteger removedCount) {
        // Skip players with permission
        if (player.hasPermission("antinetherite.bypass")) {
            return;
        }

        // Skip players in creative or spectator mode if configured to do so
        if (config.isIgnoreCreativeSpectator() &&
                (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
            return;
        }

        if (config.isEnableDestructiveActions()) {
            // Destructive mode: Remove items from inventory
            removeNetheriteItems(player, removedCount);
        } else {
            // Non-destructive mode: Notify player but don't remove items
            int count = countNetheriteItems(player);
            if (count > 0) {
                // Always notify for non-destructive actions since we're not actually removing
                // items
                player.sendMessage(Component
                        .text("You have " + count
                                + " Netherite items in your inventory that are not allowed on this server.")
                        .color(NamedTextColor.RED));
                removedCount.addAndGet(count);
            }
        }
    }

    /**
     * Removes Netherite items from a player's inventory
     * 
     * @param player       The player to check
     * @param removedCount Counter for removed items
     */
    private void removeNetheriteItems(Player player, AtomicInteger removedCount) {
        int itemsRemoved = 0;

        // Check main inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && netheriteDetector.isNetheriteItem(item)) {
                player.getInventory().setItem(i, null);
                removedCount.incrementAndGet();
                itemsRemoved++;
            }
        }

        // Check armor slots
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet != null && netheriteDetector.isNetheriteItem(helmet)) {
            player.getInventory().setHelmet(null);
            removedCount.incrementAndGet();
            itemsRemoved++;
        }

        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && netheriteDetector.isNetheriteItem(chestplate)) {
            player.getInventory().setChestplate(null);
            removedCount.incrementAndGet();
            itemsRemoved++;
        }

        ItemStack leggings = player.getInventory().getLeggings();
        if (leggings != null && netheriteDetector.isNetheriteItem(leggings)) {
            player.getInventory().setLeggings(null);
            removedCount.incrementAndGet();
            itemsRemoved++;
        }

        ItemStack boots = player.getInventory().getBoots();
        if (boots != null && netheriteDetector.isNetheriteItem(boots)) {
            player.getInventory().setBoots(null);
            removedCount.incrementAndGet();
            itemsRemoved++;
        }

        // Check offhand
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && netheriteDetector.isNetheriteItem(offhand)) {
            player.getInventory().setItemInOffHand(null);
            removedCount.incrementAndGet();
            itemsRemoved++;
        }

        // Always notify for destructive actions
        if (itemsRemoved > 0) {
            player.sendMessage(Component.text("Removed " + itemsRemoved + " Netherite items from your inventory.")
                    .color(NamedTextColor.RED));
        }
    }

    /**
     * Counts Netherite items in a player's inventory
     * 
     * @param player The player to check
     * @return The number of Netherite items found
     */
    private int countNetheriteItems(Player player) {
        int count = 0;

        // Check main inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && netheriteDetector.isNetheriteItem(item)) {
                count++;
            }
        }

        // Check armor slots
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && netheriteDetector.isNetheriteItem(item)) {
                count++;
            }
        }

        // Check offhand
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && netheriteDetector.isNetheriteItem(offhand)) {
            count++;
        }

        return count;
    }
}
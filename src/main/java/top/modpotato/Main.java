package top.modpotato;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.HandlerList;

import top.modpotato.listeners.AttackListener;
import top.modpotato.listeners.CraftListener;
import top.modpotato.listeners.DropListener;
import top.modpotato.listeners.EquipListener;
import top.modpotato.listeners.InventoryMoveListener;
import top.modpotato.listeners.MiningListener;
import top.modpotato.listeners.PickupListener;
import top.modpotato.listeners.ContainerTransferListener;
import top.modpotato.commands.AntiNetheriteCommand;
import top.modpotato.config.Config;
import top.modpotato.restoration.RestorationProgressTracker;
import top.modpotato.scheduler.NetheriteRemover;
import top.modpotato.util.DebrisStorage;
import top.modpotato.util.NetheriteDetector;
import com.tcoded.folialib.FoliaLib;

/**
 * Main plugin class for AntiNetherite
 */
public class Main extends JavaPlugin {
    private Config config;
    private NetheriteRemover netheriteRemover;
    private NetheriteDetector netheriteDetector;
    private DebrisStorage debrisStorage;
    private RestorationProgressTracker restorationProgressTracker;

    private CraftListener craftListener;
    private EquipListener equipListener;
    private AttackListener attackListener;
    private PickupListener pickupListener;
    private DropListener dropListener;
    private InventoryMoveListener inventoryMoveListener;
    private MiningListener miningListener;
    private ContainerTransferListener containerTransferListener;
    private boolean isFolia;
    private FoliaLib foliaLib;

    // Track if the plugin is shutting down to prevent unnecessary operations
    private boolean isShuttingDown = false;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();

            // Initialize config
            config = new Config(this);

            // Initialize Netherite detector
            netheriteDetector = new NetheriteDetector(config);

            // Initialize debris storage
            debrisStorage = new DebrisStorage(this, config);

            // Initialize restoration progress tracker
            restorationProgressTracker = new RestorationProgressTracker(this);

            // Initialize FoliaLib
            foliaLib = new FoliaLib(this);

            restorationProgressTracker.start();

            // Check if running on Folia
            isFolia = checkFolia();
            getLogger().info("Running on " + (isFolia ? "Folia" : "Bukkit") + " server");

            // Initialize netherite remover
            netheriteRemover = new NetheriteRemover(this, isFolia, netheriteDetector, config);

            // Start tasks based on config
            if (config.isClearNetherite()) {
                netheriteRemover.start(config.getDelayTicks());
            }

            // Register listeners
            registerListeners();

            // Register command
            getCommand("antinetherite").setExecutor(new AntiNetheriteCommand(this));

            getLogger().info("AntiNetherite has been enabled!");
        } catch (Exception e) {
            getLogger().severe("Error enabling AntiNetherite: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        isShuttingDown = true;

        try {
            // Stop restoration progress tracker
            if (restorationProgressTracker != null) {
                restorationProgressTracker.stop();
            }

            // Stop tasks
            if (netheriteRemover != null) {
                netheriteRemover.stop();
            }

            // Restore Ancient Debris if explicitly configured to do so
            if (miningListener != null && config.isRestoreDebrisOnDisable()) {
                getLogger().info("Restoring Ancient Debris blocks...");
                int count = miningListener.restoreAllDebris();
                getLogger().info("Restored " + count + " Ancient Debris blocks");
            }

            // Unregister listeners
            unregisterListeners();

            // Save debris storage
            if (debrisStorage != null) {
                debrisStorage.saveStorage();
            }

            getLogger().info("AntiNetherite has been disabled!");
        } catch (Exception e) {
            getLogger().severe("Error disabling AntiNetherite: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Registers event listeners based on configuration
     */
    private void registerListeners() {
        try {
            if (config.isCancelCraft()) {
                craftListener = new CraftListener(netheriteDetector, config);
                getServer().getPluginManager().registerEvents(craftListener, this);
            }

            if (config.isCancelEquip()) {
                equipListener = new EquipListener(netheriteDetector, config);
                getServer().getPluginManager().registerEvents(equipListener, this);
            }

            if (config.isCancelAttack()) {
                attackListener = new AttackListener(netheriteDetector, config);
                getServer().getPluginManager().registerEvents(attackListener, this);
            }

            if (config.isCancelPickup()) {
                pickupListener = new PickupListener(netheriteDetector, config);
                getServer().getPluginManager().registerEvents(pickupListener, this);
            }

            dropListener = new DropListener(config.isRemoveDropped(), netheriteDetector, config);
            getServer().getPluginManager().registerEvents(dropListener, this);

            if (config.isCancelInventoryMove()) {
                inventoryMoveListener = new InventoryMoveListener(netheriteDetector, config);
                getServer().getPluginManager().registerEvents(inventoryMoveListener, this);
            }

            if (config.isCancelContainerTransfer()) {
                containerTransferListener = new ContainerTransferListener(netheriteDetector, config);
                getServer().getPluginManager().registerEvents(containerTransferListener, this);
            }

            // Register mining listener if either ancient debris replacement option is
            // enabled
            if (config.isReplaceWhenMined() || config.isReplaceOnChunkLoad()) {
                miningListener = new MiningListener(debrisStorage,
                        config.isReplaceWhenMined(),
                        config.isReplaceOnChunkLoad(),
                        config.isOnlyReplaceGeneratedChunks(),
                        config);
                getServer().getPluginManager().registerEvents(miningListener, this);
            }
        } catch (Exception e) {
            getLogger().severe("Error registering listeners: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Unregisters all event listeners
     */
    private void unregisterListeners() {
        try {
            if (craftListener != null) {
                HandlerList.unregisterAll(craftListener);
                craftListener = null;
            }

            if (equipListener != null) {
                HandlerList.unregisterAll(equipListener);
                equipListener = null;
            }

            if (attackListener != null) {
                HandlerList.unregisterAll(attackListener);
                attackListener = null;
            }

            if (pickupListener != null) {
                HandlerList.unregisterAll(pickupListener);
                pickupListener = null;
            }

            if (dropListener != null) {
                HandlerList.unregisterAll(dropListener);
                dropListener = null;
            }

            if (inventoryMoveListener != null) {
                HandlerList.unregisterAll(inventoryMoveListener);
                inventoryMoveListener = null;
            }

            if (containerTransferListener != null) {
                HandlerList.unregisterAll(containerTransferListener);
                containerTransferListener = null;
            }

            if (miningListener != null) {
                HandlerList.unregisterAll(miningListener);
                miningListener = null;
            }
        } catch (Exception e) {
            getLogger().severe("Error unregistering listeners: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Checks if the server is running on Folia
     * 
     * @return true if running on Folia, false otherwise
     */
    private boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Updates a specific configuration value and applies changes
     * 
     * @param path  The configuration path
     * @param value The new value
     */
    public void updateConfig(String path, Object value) {
        config.update(path, value);

        // Reload Netherite detector
        if (netheriteDetector != null) {
            netheriteDetector.reloadNetheriteItems();
        }

        // Re-register listeners and tasks based on new config
        unregisterListeners();

        if (netheriteRemover != null) {
            netheriteRemover.stop();
        }

        if (config.isClearNetherite()) {
            netheriteRemover.start(config.getDelayTicks());
        }

        registerListeners();
    }

    /**
     * Gets the current configuration value
     * 
     * @param path The configuration path
     * @return The configuration value
     */
    public Object getConfigValue(String path) {
        return config.getValue(path);
    }

    /**
     * Reloads the plugin configuration
     */
    public void reloadPluginConfig() {
        if (isShuttingDown) {
            return;
        }

        try {
            boolean oldReplaceWhenMined = config.isReplaceWhenMined();
            boolean oldReplaceOnChunkLoad = config.isReplaceOnChunkLoad();

            // Reload config
            config.reload();

            // Reload Netherite detector
            netheriteDetector.reloadNetheriteItems();

            // Stop tasks
            if (netheriteRemover != null) {
                netheriteRemover.stop();
            }

            // Restore Ancient Debris if explicitly configured to do so
            if (miningListener != null &&
                    (oldReplaceWhenMined || oldReplaceOnChunkLoad) &&
                    (!config.isReplaceWhenMined() && !config.isReplaceOnChunkLoad()) &&
                    config.isRestoreDebrisOnConfigChange()) {
                getLogger().info("Restoring Ancient Debris blocks due to config change...");
                int count = miningListener.restoreAllDebris();
                getLogger().info("Restored " + count + " Ancient Debris blocks");
            }

            // Unregister listeners
            unregisterListeners();

            // Register listeners again
            registerListeners();

            // Start tasks based on config
            if (config.isClearNetherite()) {
                netheriteRemover.start(config.getDelayTicks());
            }

            getLogger().info("AntiNetherite configuration reloaded.");
        } catch (Exception e) {
            getLogger().severe("Error reloading configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets the Netherite detector
     * 
     * @return The Netherite detector
     */
    public NetheriteDetector getNetheriteDetector() {
        return netheriteDetector;
    }

    /**
     * Gets the debris storage
     * 
     * @return The debris storage
     */
    public DebrisStorage getDebrisStorage() {
        return debrisStorage;
    }

    /**
     * Gets the restoration progress tracker
     * 
     * @return The restoration progress tracker
     */
    public RestorationProgressTracker getRestorationProgressTracker() {
        return restorationProgressTracker;
    }

    /**
     * Checks if the plugin is shutting down
     * 
     * @return true if the plugin is shutting down, false otherwise
     */
    public boolean isShuttingDown() {
        return isShuttingDown;
    }

    /**
     * Gets the FoliaLib instance
     * 
     * @return The FoliaLib instance
     */
    public FoliaLib getFoliaLib() {
        return foliaLib;
    }
}
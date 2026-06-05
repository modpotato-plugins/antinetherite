package top.modpotato.config;

import org.bukkit.configuration.file.FileConfiguration;
import top.modpotato.Main;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration manager for AntiNetherite plugin
 */
public class Config {
    private static final List<String> DEFAULT_NETHERITE_ITEMS = List.of(
            "NETHERITE_SWORD",
            "NETHERITE_PICKAXE",
            "NETHERITE_AXE",
            "NETHERITE_SHOVEL",
            "NETHERITE_HOE",
            "NETHERITE_SPEAR",
            "NETHERITE_HELMET",
            "NETHERITE_CHESTPLATE",
            "NETHERITE_LEGGINGS",
            "NETHERITE_BOOTS",
            "NETHERITE_HORSE_ARMOR",
            "NETHERITE_NAUTILUS_ARMOR",
            "NETHERITE_BLOCK",
            "NETHERITE_INGOT",
            "NETHERITE_SCRAP",
            "NETHERITE_UPGRADE_SMITHING_TEMPLATE",
            "ANCIENT_DEBRIS"
    );

    private static final List<String> DEFAULT_LOOT_TABLE_ITEMS = List.of(
            "NETHERITE_SWORD",
            "NETHERITE_PICKAXE",
            "NETHERITE_AXE",
            "NETHERITE_SHOVEL",
            "NETHERITE_HOE",
            "NETHERITE_SPEAR",
            "NETHERITE_HELMET",
            "NETHERITE_CHESTPLATE",
            "NETHERITE_LEGGINGS",
            "NETHERITE_BOOTS",
            "NETHERITE_HORSE_ARMOR",
            "NETHERITE_NAUTILUS_ARMOR",
            "NETHERITE_BLOCK",
            "NETHERITE_UPGRADE_SMITHING_TEMPLATE"
    );

    private static final List<String> DEFAULT_LOOT_TABLE_PRESERVED_ITEMS = List.of(
            "ANCIENT_DEBRIS",
            "NETHERITE_SCRAP",
            "NETHERITE_INGOT"
    );

    private final Main plugin;
    
    // Configuration values
    private int delay;
    private int multiplier;
    private boolean clearNetherite;
    private boolean cancelCraft;
    private boolean cancelEquip;
    private boolean cancelAttack;
    private boolean cancelPickup;
    private boolean removeDropped;
    private boolean cancelInventoryMove;
    private boolean cancelContainerTransfer;

    // Loot table settings
    private boolean removeLootTableItems;
    private boolean lootTablesInheritDetectionItems;
    private List<String> lootTableItemsList;
    private List<String> lootTablePreservedItemsList;
    
    // Global settings
    private boolean enableDestructiveActions;
    
    // Netherite detection settings
    private boolean useNameMatching;
    private List<String> netheriteItemsList;
    
    // Ancient Debris settings
    private boolean replaceWhenMined;
    private boolean replaceOnChunkLoad;
    private boolean onlyReplaceGeneratedChunks;
    private boolean ensureChunksLoaded;
    private boolean saveReplacedLocations;
    
    // Performance settings
    private boolean restoreDebrisOnDisable;
    private boolean restoreDebrisOnConfigChange;
    private int maxReplacementsPerChunk;
    
    // Advanced settings
    private int maxLocationsPerWorld;
    private int commandCooldownSeconds;
    private boolean logDebrisReplacements;
    private boolean logInventoryRemovals;
    private boolean ignoreCreativeSpectator;
    private boolean notifyPlayers;
    
    /**
     * Creates a new Config instance
     * @param plugin The plugin instance
     */
    public Config(Main plugin) {
        this.plugin = plugin;
        reload();
    }
    
    /**
     * Reloads the configuration from disk
     */
    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        boolean shouldSaveConfig = false;
        
        // Load global settings
        enableDestructiveActions = config.getBoolean("anti-netherite.global.enable-destructive-actions", false);
        
        // Load timing settings
        delay = config.getInt("anti-netherite.timing.delay", 1);
        multiplier = config.getInt("anti-netherite.timing.multiplier", 20);
        
        // Load inventory protection settings
        clearNetherite = config.getBoolean("anti-netherite.inventory.clear", false);
        cancelInventoryMove = config.getBoolean("anti-netherite.inventory.cancel-move", true);
        cancelContainerTransfer = config.getBoolean("anti-netherite.inventory.cancel-container-transfer", false);
        
        // Load item interaction settings
        cancelCraft = config.getBoolean("anti-netherite.interaction.cancel-craft", true);
        cancelEquip = config.getBoolean("anti-netherite.interaction.cancel-equip", true);
        cancelAttack = config.getBoolean("anti-netherite.interaction.cancel-attack", true);
        
        // Load item handling settings
        cancelPickup = config.getBoolean("anti-netherite.item-handling.cancel-pickup", true);
        removeDropped = config.getBoolean("anti-netherite.item-handling.remove-dropped", false);

        // Load loot table settings
        shouldSaveConfig |= ensureDefault(config, "anti-netherite.loot-tables.remove-blocked-items", true);
        shouldSaveConfig |= ensureDefault(config, "anti-netherite.loot-tables.inherit-detection-items", true);
        shouldSaveConfig |= ensureDefault(config, "anti-netherite.loot-tables.items", new ArrayList<>(DEFAULT_LOOT_TABLE_ITEMS));
        shouldSaveConfig |= ensureDefault(config, "anti-netherite.loot-tables.preserve-items", new ArrayList<>(DEFAULT_LOOT_TABLE_PRESERVED_ITEMS));
        removeLootTableItems = config.getBoolean("anti-netherite.loot-tables.remove-blocked-items", true);
        lootTablesInheritDetectionItems = config.getBoolean("anti-netherite.loot-tables.inherit-detection-items", true);
        lootTableItemsList = config.getStringList("anti-netherite.loot-tables.items");
        lootTablePreservedItemsList = config.getStringList("anti-netherite.loot-tables.preserve-items");
        
        // Load Ancient Debris settings
        replaceWhenMined = config.getBoolean("anti-netherite.ancient-debris.replace-when-mined", true);
        replaceOnChunkLoad = config.getBoolean("anti-netherite.ancient-debris.replace-on-chunk-load", false);
        onlyReplaceGeneratedChunks = config.getBoolean("anti-netherite.ancient-debris.only-replace-generated-chunks", true);
        ensureChunksLoaded = config.getBoolean("anti-netherite.ancient-debris.ensure-chunks-loaded", false);
        saveReplacedLocations = config.getBoolean("anti-netherite.ancient-debris.save-replaced-locations", false);
        
        // Load performance settings
        restoreDebrisOnDisable = config.getBoolean("anti-netherite.performance.restore-debris-on-disable", false);
        restoreDebrisOnConfigChange = config.getBoolean("anti-netherite.performance.restore-debris-on-config-change", false);
        maxReplacementsPerChunk = config.getInt("anti-netherite.performance.max-replacements-per-chunk", 50);
        
        // Load advanced settings
        maxLocationsPerWorld = config.getInt("anti-netherite.advanced.max-locations-per-world", 10000);
        commandCooldownSeconds = config.getInt("anti-netherite.advanced.command-cooldown-seconds", 1);
        logDebrisReplacements = config.getBoolean("anti-netherite.advanced.log-debris-replacements", true);
        logInventoryRemovals = config.getBoolean("anti-netherite.advanced.log-inventory-removals", true);
        ignoreCreativeSpectator = config.getBoolean("anti-netherite.advanced.ignore-creative-spectator", true);
        notifyPlayers = config.getBoolean("anti-netherite.advanced.notify-players", true);
        
        // Load Netherite detection settings
        useNameMatching = config.getBoolean("anti-netherite.detection.use-name-matching", true);
        netheriteItemsList = config.getStringList("anti-netherite.detection.items");
        
        // If the list is empty, add default Netherite items
        if (netheriteItemsList.isEmpty()) {
            netheriteItemsList = new ArrayList<>(DEFAULT_NETHERITE_ITEMS);
            
            // Save the default list to config
            config.set("anti-netherite.detection.items", netheriteItemsList);
            shouldSaveConfig = true;
        }

        if (shouldSaveConfig) {
            plugin.saveConfig();
        }
    }

    private boolean ensureDefault(FileConfiguration config, String path, Object defaultValue) {
        if (config.contains(path)) {
            return false;
        }

        config.set(path, defaultValue);
        return true;
    }
    
    /**
     * Updates a configuration value
     * @param path The configuration path
     * @param value The new value
     */
    public void update(String path, Object value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
        reload();
    }
    
    /**
     * Gets a configuration value
     * @param path The configuration path
     * @return The configuration value
     */
    public Object getValue(String path) {
        return plugin.getConfig().get(path);
    }
    
    /**
     * Gets the delay between checks
     * @return The delay in ticks
     */
    public int getDelayTicks() {
        return delay * multiplier;
    }
    
    /**
     * Gets whether destructive actions are enabled
     * @return true if destructive actions are enabled, false otherwise
     */
    public boolean isEnableDestructiveActions() {
        return enableDestructiveActions;
    }
    
    // Getters
    public int getDelay() {
        return delay;
    }
    
    public int getMultiplier() {
        return multiplier;
    }
    
    public boolean isClearNetherite() {
        return clearNetherite;
    }
    
    public boolean isCancelCraft() {
        return cancelCraft;
    }
    
    public boolean isCancelEquip() {
        return cancelEquip;
    }
    
    public boolean isCancelAttack() {
        return cancelAttack;
    }
    
    public boolean isCancelPickup() {
        return cancelPickup;
    }
    
    public boolean isRemoveDropped() {
        return removeDropped;
    }
    
    public boolean isCancelInventoryMove() {
        return cancelInventoryMove;
    }

    /**
     * Gets whether to cancel automated container (hopper/minecart) transfers of Netherite items
     * @return true if cancelling container transfers, false otherwise
     */
    public boolean isCancelContainerTransfer() {
        return cancelContainerTransfer;
    }

    /**
     * Gets whether generated loot table contents should be filtered for blocked items
     * @return true if blocked loot table items should be removed, false otherwise
     */
    public boolean isRemoveLootTableItems() {
        return removeLootTableItems;
    }

    /**
     * Gets whether loot table filtering should include the main detection item list
     * @return true if loot table filtering inherits detection items, false otherwise
     */
    public boolean isLootTablesInheritDetectionItems() {
        return lootTablesInheritDetectionItems;
    }

    /**
     * Gets the loot-table-specific blocked item names
     * @return The loot-table-specific blocked item names
     */
    public List<String> getLootTableItemsList() {
        return lootTableItemsList;
    }

    /**
     * Gets item names that should be preserved when filtering generated loot
     * @return The loot table preservation item names
     */
    public List<String> getLootTablePreservedItemsList() {
        return lootTablePreservedItemsList;
    }
    
    /**
     * Gets whether to use name matching for Netherite item detection
     * @return true if using name matching, false otherwise
     */
    public boolean isUseNameMatching() {
        return useNameMatching;
    }
    
    /**
     * Gets the list of Netherite item names
     * @return The list of Netherite item names
     */
    public List<String> getNetheriteItemsList() {
        return netheriteItemsList;
    }
    
    /**
     * Gets whether to replace Ancient Debris when mined
     * @return true if replacing Ancient Debris when mined, false otherwise
     */
    public boolean isReplaceWhenMined() {
        return replaceWhenMined;
    }
    
    /**
     * Gets whether to replace Ancient Debris when chunks are loaded
     * @return true if replacing Ancient Debris on chunk load, false otherwise
     */
    public boolean isReplaceOnChunkLoad() {
        return replaceOnChunkLoad;
    }
    
    /**
     * Gets whether to only replace Ancient Debris in generated chunks
     * @return true if only replacing in generated chunks, false otherwise
     */
    public boolean isOnlyReplaceGeneratedChunks() {
        return onlyReplaceGeneratedChunks;
    }
    
    /**
     * Gets whether to restore Ancient Debris when the plugin is disabled
     * @return true if restoring Ancient Debris on disable, false otherwise
     */
    public boolean isRestoreDebrisOnDisable() {
        return restoreDebrisOnDisable;
    }
    
    /**
     * Gets whether to restore Ancient Debris when the configuration changes
     * @return true if restoring Ancient Debris on config change, false otherwise
     */
    public boolean isRestoreDebrisOnConfigChange() {
        return restoreDebrisOnConfigChange;
    }
    
    /**
     * Gets the maximum number of Ancient Debris replacements per chunk
     * @return The maximum number of replacements per chunk
     */
    public int getMaxReplacementsPerChunk() {
        return maxReplacementsPerChunk;
    }
    
    /**
     * Gets the maximum number of locations to store per world
     * @return The maximum number of locations per world
     */
    public int getMaxLocationsPerWorld() {
        return maxLocationsPerWorld;
    }
    
    /**
     * Gets the command cooldown in seconds
     * @return The command cooldown in seconds
     */
    public int getCommandCooldownSeconds() {
        return commandCooldownSeconds;
    }
    
    /**
     * Gets whether to log debris replacements
     * @return true if logging debris replacements, false otherwise
     */
    public boolean isLogDebrisReplacements() {
        return logDebrisReplacements;
    }
    
    /**
     * Gets whether to log inventory removals
     * @return true if logging inventory removals, false otherwise
     */
    public boolean isLogInventoryRemovals() {
        return logInventoryRemovals;
    }
    
    /**
     * Gets whether to ensure chunks are loaded when replacing Ancient Debris
     * @return true if ensuring chunks are loaded, false otherwise
     */
    public boolean isEnsureChunksLoaded() {
        return ensureChunksLoaded;
    }
    
    /**
     * Gets whether to save the locations of replaced Ancient Debris
     * @return true if saving replaced locations, false otherwise
     */
    public boolean isSaveReplacedLocations() {
        return saveReplacedLocations;
    }
    
    /**
     * Gets whether to ignore players in creative or spectator mode
     * @return true if ignoring creative and spectator players, false otherwise
     */
    public boolean isIgnoreCreativeSpectator() {
        return ignoreCreativeSpectator;
    }
    
    /**
     * Gets whether to notify players when their actions are blocked
     * @return true if notifying players, false otherwise
     */
    public boolean isNotifyPlayers() {
        return notifyPlayers;
    }
    
    /**
     * @deprecated Use {@link #isReplaceWhenMined()} instead
     * Gets whether to replace Ancient Debris when mined
     * @return true if replacing Ancient Debris, false otherwise
     */
    @Deprecated
    public boolean isReplaceAncientDebris() {
        return replaceWhenMined;
    }
}

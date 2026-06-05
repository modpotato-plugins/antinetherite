package top.modpotato.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import top.modpotato.config.Config;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Utility class for detecting Netherite items
 * Acts as a centralized source of truth for what constitutes a Netherite item
 */
public class NetheriteDetector {
    private final Config config;
    private final Set<String> netheriteItemNames = new HashSet<>();
    private final Set<Material> netheriteItemMaterials = new HashSet<>();
    private final Set<String> lootTableItemNames = new HashSet<>();
    private final Set<Material> lootTableItemMaterials = new HashSet<>();
    private final Set<String> lootTablePreservedItemNames = new HashSet<>();
    private final Set<Material> lootTablePreservedItemMaterials = new HashSet<>();
    
    /**
     * Creates a new NetheriteDetector
     * @param config The plugin configuration
     */
    public NetheriteDetector(Config config) {
        this.config = config;
        reloadNetheriteItems();
    }
    
    /**
     * Reloads the list of Netherite items from the configuration
     */
    public void reloadNetheriteItems() {
        netheriteItemNames.clear();
        netheriteItemMaterials.clear();
        lootTableItemNames.clear();
        lootTableItemMaterials.clear();
        lootTablePreservedItemNames.clear();
        lootTablePreservedItemMaterials.clear();
        
        // Add custom item names from config
        addConfiguredItems(config.getNetheriteItemsList(), netheriteItemNames, netheriteItemMaterials);
        
        // Add default Netherite materials if using name matching
        if (config.isUseNameMatching()) {
            for (Material material : Material.values()) {
                if (material.name().contains("NETHERITE")) {
                    netheriteItemMaterials.add(material);
                    netheriteItemNames.add(material.name());
                }
            }
        }

        if (config.isLootTablesInheritDetectionItems()) {
            lootTableItemNames.addAll(netheriteItemNames);
            lootTableItemMaterials.addAll(netheriteItemMaterials);
        }

        addConfiguredItems(config.getLootTableItemsList(), lootTableItemNames, lootTableItemMaterials);
        addConfiguredItems(config.getLootTablePreservedItemsList(), lootTablePreservedItemNames, lootTablePreservedItemMaterials);

        // Apply loot-table-specific name matching independently of detection inheritance
        if (config.isLootTablesUseNameMatching()) {
            for (Material material : Material.values()) {
                if (material.name().contains("NETHERITE")) {
                    lootTableItemMaterials.add(material);
                    lootTableItemNames.add(material.name());
                }
            }
        }
    }
    
    /**
     * Checks if an item is a Netherite item
     * @param item The item to check
     * @return true if the item is a Netherite item, false otherwise
     */
    public boolean isNetheriteItem(ItemStack item) {
        if (item == null) {
            return false;
        }

        return matchesConfiguredItem(item, netheriteItemMaterials, netheriteItemNames, config.isUseNameMatching());
    }

    /**
     * Checks if an item should be removed from generated loot table contents
     * @param item The item to check
     * @return true if the item should be removed from generated loot, false otherwise
     */
    public boolean isBlockedLootTableItem(ItemStack item) {
        if (item == null) {
            return false;
        }

        if (matchesConfiguredItem(item, lootTablePreservedItemMaterials, lootTablePreservedItemNames, false)) {
            return false;
        }

        return matchesConfiguredItem(item, lootTableItemMaterials, lootTableItemNames, config.isLootTablesUseNameMatching());
    }

    private void addConfiguredItems(List<String> configuredItems, Set<String> names, Set<Material> materials) {
        if (configuredItems == null) {
            return;
        }

        for (String itemName : configuredItems) {
            String normalizedName = normalizeItemName(itemName);
            if (normalizedName == null) {
                continue;
            }

            names.add(normalizedName);
            try {
                materials.add(Material.valueOf(normalizedName));
            } catch (IllegalArgumentException ignored) {
                // Material may not exist on this server version yet.
            }
        }
    }

    private String normalizeItemName(String itemName) {
        if (itemName == null) {
            return null;
        }

        String normalizedName = itemName.trim().toUpperCase(Locale.ROOT);
        return normalizedName.isEmpty() ? null : normalizedName;
    }

    private boolean matchesConfiguredItem(ItemStack item, Set<Material> materials, Set<String> names, boolean useNameMatching) {
        if (materials.contains(item.getType())) {
            return true;
        }

        if (!useNameMatching) {
            return false;
        }

        String itemTypeName = item.getType().name();
        for (String itemName : names) {
            if (itemTypeName.contains(itemName)) {
                return true;
            }
        }

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            Component displayName = item.getItemMeta().displayName();
            String plainText = PlainTextComponentSerializer.plainText().serialize(displayName);
            String normalizedDisplayName = plainText.toUpperCase(Locale.ROOT);
            for (String itemName : names) {
                if (normalizedDisplayName.contains(itemName)) {
                    return true;
                }
            }
        }

        return false;
    }
    
    /**
     * Gets the set of Netherite item names
     * @return The set of Netherite item names
     */
    public Set<String> getNetheriteItemNames() {
        return new HashSet<>(netheriteItemNames);
    }
    
    /**
     * Gets the set of Netherite item materials
     * @return The set of Netherite item materials
     */
    public Set<Material> getNetheriteItemMaterials() {
        return new HashSet<>(netheriteItemMaterials);
    }

    /**
     * Gets the set of loot-table-blocked item names
     * @return The set of loot-table-blocked item names
     */
    public Set<String> getLootTableItemNames() {
        return new HashSet<>(lootTableItemNames);
    }

    /**
     * Gets the set of loot-table-preserved item names
     * @return The set of loot-table-preserved item names
     */
    public Set<String> getLootTablePreservedItemNames() {
        return new HashSet<>(lootTablePreservedItemNames);
    }
}

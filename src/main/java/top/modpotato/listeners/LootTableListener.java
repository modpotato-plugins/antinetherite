package top.modpotato.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

import top.modpotato.config.Config;
import top.modpotato.util.NetheriteDetector;

/**
 * Removes blocked Netherite items from generated loot table contents.
 */
public class LootTableListener implements Listener {
    private final NetheriteDetector netheriteDetector;
    private final Config config;

    public LootTableListener(NetheriteDetector netheriteDetector, Config config) {
        this.netheriteDetector = netheriteDetector;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (!config.isRemoveLootTableItems()) {
            return;
        }

        event.getLoot().removeIf(this::shouldRemoveFromLoot);
    }

    private boolean shouldRemoveFromLoot(ItemStack item) {
        return item != null && netheriteDetector.isBlockedLootTableItem(item);
    }
}

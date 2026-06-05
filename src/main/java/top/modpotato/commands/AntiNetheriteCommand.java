package top.modpotato.commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import top.modpotato.Main;
import top.modpotato.restoration.RestorationSession;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Command handler for the AntiNetherite plugin
 */
public class AntiNetheriteCommand implements CommandExecutor, TabCompleter {
    private final Main plugin;
    
    // Map of user-friendly setting names to actual config paths
    private final Map<String, String> SETTINGS_MAP = new HashMap<>();
    
    // Track when the last restore command was used per sender to prevent spam
    private final Map<String, Long> lastRestoreTimes = new HashMap<>();

    public AntiNetheriteCommand(Main plugin) {
        this.plugin = plugin;
        plugin.getCommand("antinetherite").setTabCompleter(this);
        
        // Initialize settings map with user-friendly names and actual config paths
        // Inventory settings
        SETTINGS_MAP.put("clear", "anti-netherite.inventory.clear");
        SETTINGS_MAP.put("cancel-inventory-move", "anti-netherite.inventory.cancel-move");
        SETTINGS_MAP.put("cancel-container-transfer", "anti-netherite.inventory.cancel-container-transfer");
        
        // Interaction settings
        SETTINGS_MAP.put("cancel-craft", "anti-netherite.interaction.cancel-craft");
        SETTINGS_MAP.put("cancel-equip", "anti-netherite.interaction.cancel-equip");
        SETTINGS_MAP.put("cancel-attack", "anti-netherite.interaction.cancel-attack");
        
        // Item handling settings
        SETTINGS_MAP.put("cancel-pickup", "anti-netherite.item-handling.cancel-pickup");
        SETTINGS_MAP.put("remove-dropped", "anti-netherite.item-handling.remove-dropped");

        // Loot table settings
        SETTINGS_MAP.put("remove-loot-table-items", "anti-netherite.loot-tables.remove-blocked-items");
        SETTINGS_MAP.put("loot-table-inherit-detection-items", "anti-netherite.loot-tables.inherit-detection-items");
        
        // Ancient debris settings
        SETTINGS_MAP.put("replace-when-mined", "anti-netherite.ancient-debris.replace-when-mined");
        SETTINGS_MAP.put("replace-on-chunk-load", "anti-netherite.ancient-debris.replace-on-chunk-load");
        SETTINGS_MAP.put("only-replace-generated-chunks", "anti-netherite.ancient-debris.only-replace-generated-chunks");
        SETTINGS_MAP.put("ensure-chunks-loaded", "anti-netherite.ancient-debris.ensure-chunks-loaded");
        SETTINGS_MAP.put("save-replaced-locations", "anti-netherite.ancient-debris.save-replaced-locations");

        // Advanced settings
        SETTINGS_MAP.put("ignore-creative-spectator", "anti-netherite.advanced.ignore-creative-spectator");
        SETTINGS_MAP.put("notify-players", "anti-netherite.advanced.notify-players");
        
        // Detection settings
        SETTINGS_MAP.put("detection.use-name-matching", "anti-netherite.detection.use-name-matching");
        
        // Timing settings
        SETTINGS_MAP.put("delay", "anti-netherite.timing.delay");
        SETTINGS_MAP.put("multiplier", "anti-netherite.timing.multiplier");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("antinetherite.manage")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadPluginConfig();
                sender.sendMessage(Component.text("AntiNetherite configuration reloaded.").color(NamedTextColor.GREEN));
                return true;
            case "restore-feedback":
                return handleRestoreFeedback(sender, args);
            case "restore-debris":
                // Get the configured cooldown in milliseconds
                int cooldownSeconds = plugin.getConfig().getInt("anti-netherite.advanced.command-cooldown-seconds", 1);
                long cooldownMs = cooldownSeconds * 1000L;
                
                // Check for cooldown per sender to prevent command spam
                String senderKey = sender.getName();
                long currentTime = System.currentTimeMillis();
                Long lastTime = lastRestoreTimes.get(senderKey);
                
                if (lastTime != null && (currentTime - lastTime) < cooldownMs) {
                    long remainingSeconds = (cooldownMs - (currentTime - lastTime)) / 1000;
                    sender.sendMessage(Component.text("Please wait " + remainingSeconds + " seconds before using this command again.").color(NamedTextColor.RED));
                    return true;
                }
                
                lastRestoreTimes.put(senderKey, currentTime);
                
                // Check if replacement features are enabled in config
                boolean replaceWhenMined = (boolean) plugin.getConfigValue("anti-netherite.ancient-debris.replace-when-mined");
                boolean replaceOnChunkLoad = (boolean) plugin.getConfigValue("anti-netherite.ancient-debris.replace-on-chunk-load");
                
                // If both replacement features are disabled, check if there's anything to restore
                if (!replaceWhenMined && !replaceOnChunkLoad) {
                    int totalLocations = plugin.getDebrisStorage().getTotalLocationsCount();
                    if (totalLocations == 0) {
                        sender.sendMessage(Component.text("Ancient Debris replacement is disabled and there are no stored locations to restore.").color(NamedTextColor.YELLOW));
                        return true;
                    } else {
                        sender.sendMessage(Component.text("Ancient Debris replacement is disabled but there are still " + totalLocations + " stored locations. Proceeding with restoration...").color(NamedTextColor.YELLOW));
                    }
                }
                
                if (args.length > 1) {
                    // World-specific restore
                    String worldName = args[1];
                    World world = Bukkit.getWorld(worldName);
                    
                    if (world == null) {
                        sender.sendMessage(Component.text("World not found: " + worldName).color(NamedTextColor.RED));
                        return true;
                    }
                    
                    // Check if there are any locations to restore in this world
                    int worldLocations = plugin.getDebrisStorage().getWorldLocationsCount(world);
                    if (worldLocations == 0) {
                        sender.sendMessage(Component.text("No Ancient Debris locations to restore in world " + worldName + ".").color(NamedTextColor.YELLOW));
                        return true;
                    }
                    
                    // Schedule restoration and get session
                    RestorationSession session = plugin.getDebrisStorage().scheduleRestoreInWorld(sender, world);
                    
                    if (session == null) {
                        sender.sendMessage(Component.text("No Ancient Debris locations to restore in world " + worldName + ".").color(NamedTextColor.YELLOW));
                        return true;
                    }
                    
                    // Register session with progress tracker
                    plugin.getRestorationProgressTracker().registerSession(session);
                    
                    // Send immediate scheduling summary
                    sender.sendMessage(Component.text("Scheduled restoration for ")
                        .color(NamedTextColor.GREEN)
                        .append(Component.text(session.getScheduledChunks()).color(NamedTextColor.GOLD))
                        .append(Component.text(" chunks, ").color(NamedTextColor.GREEN))
                        .append(Component.text(session.getTotalLocations()).color(NamedTextColor.GOLD))
                        .append(Component.text(" locations in world ").color(NamedTextColor.GREEN))
                        .append(Component.text(worldName).color(NamedTextColor.GOLD))
                        .append(Component.text(".").color(NamedTextColor.GREEN)));
                } else {
                    // Global restore
                    // Check if there are any locations to restore
                    int totalLocations = plugin.getDebrisStorage().getTotalLocationsCount();
                    if (totalLocations == 0) {
                        sender.sendMessage(Component.text("No Ancient Debris locations to restore.").color(NamedTextColor.YELLOW));
                        return true;
                    }
                    
                    // Schedule restoration and get session
                    RestorationSession session = plugin.getDebrisStorage().scheduleRestoreAll(sender);
                    
                    if (session == null) {
                        sender.sendMessage(Component.text("No Ancient Debris locations to restore.").color(NamedTextColor.YELLOW));
                        return true;
                    }
                    
                    // Register session with progress tracker
                    plugin.getRestorationProgressTracker().registerSession(session);
                    
                    // Send immediate scheduling summary
                    sender.sendMessage(Component.text("Scheduled restoration for ")
                        .color(NamedTextColor.GREEN)
                        .append(Component.text(session.getScheduledChunks()).color(NamedTextColor.GOLD))
                        .append(Component.text(" chunks, ").color(NamedTextColor.GREEN))
                        .append(Component.text(session.getTotalLocations()).color(NamedTextColor.GOLD))
                        .append(Component.text(" locations.").color(NamedTextColor.GREEN)));
                }
                return true;
            case "debris-info":
                // Show information about stored Ancient Debris locations
                int totalLocations = plugin.getDebrisStorage().getTotalLocationsCount();
                
                if (totalLocations == 0) {
                    sender.sendMessage(Component.text("No Ancient Debris locations are currently stored.").color(NamedTextColor.YELLOW));
                    return true;
                }
                
                sender.sendMessage(Component.text("Ancient Debris Information:").color(NamedTextColor.GREEN));
                sender.sendMessage(Component.text("Total stored locations: " + totalLocations).color(NamedTextColor.WHITE));
                
                // Show per-world counts
                for (World world : Bukkit.getWorlds()) {
                    int worldCount = plugin.getDebrisStorage().getWorldLocationsCount(world);
                    if (worldCount > 0) {
                        sender.sendMessage(Component.text("- " + world.getName() + ": " + worldCount + " locations").color(NamedTextColor.WHITE));
                    }
                }
                
                // Show config status
                boolean isReplaceWhenMined = (boolean) plugin.getConfigValue("anti-netherite.ancient-debris.replace-when-mined");
                boolean isReplaceOnChunkLoad = (boolean) plugin.getConfigValue("anti-netherite.ancient-debris.replace-on-chunk-load");
                
                sender.sendMessage(Component.text("Current config:").color(NamedTextColor.GREEN));
                sender.sendMessage(Component.text("- Replace when mined: " + (isReplaceWhenMined ? "Enabled" : "Disabled")).color(
                    isReplaceWhenMined ? NamedTextColor.RED : NamedTextColor.GREEN));
                sender.sendMessage(Component.text("- Replace on chunk load: " + (isReplaceOnChunkLoad ? "Enabled" : "Disabled")).color(
                    isReplaceOnChunkLoad ? NamedTextColor.RED : NamedTextColor.GREEN));
                
                return true;
            case "get":
                return handleGetCommand(sender, args);
            case "set":
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /antinetherite set <setting> <value>").color(NamedTextColor.RED));
                    return true;
                }
                
                String settingName = args[1].toLowerCase();
                
                // Special handling for list settings add/remove
                String listPath = getListConfigPath(settingName);
                if (listPath != null) {
                    if (args.length < 4) {
                        sender.sendMessage(Component.text("Usage: /antinetherite set " + settingName + " <add|remove> <item>").color(NamedTextColor.RED));
                        return true;
                    }
                    
                    String action = args[2].toLowerCase();
                    String item = args[3].toUpperCase(Locale.ROOT);
                    
                    List<String> items = getStringList(listPath);
                    
                    if (action.equals("add")) {
                        if (!items.contains(item)) {
                            items.add(item);
                            plugin.updateConfig(listPath, items);
                            sender.sendMessage(Component.text("Added " + item + " to " + settingName + ".").color(NamedTextColor.GREEN));
                        } else {
                            sender.sendMessage(Component.text(item + " is already in " + settingName + ".").color(NamedTextColor.YELLOW));
                        }
                    } else if (action.equals("remove")) {
                        if (items.contains(item)) {
                            items.remove(item);
                            plugin.updateConfig(listPath, items);
                            sender.sendMessage(Component.text("Removed " + item + " from " + settingName + ".").color(NamedTextColor.GREEN));
                        } else {
                            sender.sendMessage(Component.text(item + " is not in " + settingName + ".").color(NamedTextColor.YELLOW));
                        }
                    } else {
                        sender.sendMessage(Component.text("Invalid action: " + action + ". Use 'add' or 'remove'.").color(NamedTextColor.RED));
                    }
                    return true;
                }
                
                // Get the actual config path from the user-friendly name
                String settingPath = getConfigPath(settingName);
                if (settingPath == null) {
                    sender.sendMessage(Component.text("Unknown setting: " + settingName).color(NamedTextColor.RED));
                    return true;
                }
                
                String settingValue = args[2];
                
                // Get the current value to determine the type
                Object currentValue = plugin.getConfigValue(settingPath);
                
                if (currentValue == null) {
                    sender.sendMessage(Component.text("Setting not found: " + settingName).color(NamedTextColor.RED));
                    return true;
                }
                
                // Type-aware setting
                try {
                    if (currentValue instanceof Boolean) {
                        // Boolean setting
                        if (!settingValue.equalsIgnoreCase("true") && !settingValue.equalsIgnoreCase("false")) {
                            sender.sendMessage(Component.text("Value must be true or false.").color(NamedTextColor.RED));
                            return true;
                        }
                        plugin.updateConfig(settingPath, Boolean.parseBoolean(settingValue));
                    } else if (currentValue instanceof Integer) {
                        // Integer setting
                        int intValue = Integer.parseInt(settingValue);
                        int minimumValue = allowsUnlimitedValue(settingPath) ? -1 : 1;
                        if (intValue < minimumValue) {
                            sender.sendMessage(Component.text("Value must be at least " + minimumValue + ".").color(NamedTextColor.RED));
                            return true;
                        }
                        plugin.updateConfig(settingPath, intValue);
                    } else if (currentValue instanceof List) {
                        // List setting (should not happen here - detection.items is handled above)
                        sender.sendMessage(Component.text("This setting is a list and cannot be set directly.").color(NamedTextColor.RED));
                        return true;
                    } else {
                        // Unknown type, try string
                        plugin.updateConfig(settingPath, settingValue);
                    }
                    
                    sender.sendMessage(Component.text("Set " + settingName + " to " + settingValue).color(NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid number: " + settingValue).color(NamedTextColor.RED));
                    return true;
                }
                
                return true;
            default:
                showHelp(sender);
                return true;
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("AntiNetherite Commands:").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/antinetherite reload - Reload the configuration").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/antinetherite restore-debris [world] - Restore all replaced Ancient Debris").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - Optional world parameter to restore only in a specific world").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  - Only restores blocks that are still Netherrack").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  - Shows progress updates with blended time and percentage-based reporting").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/antinetherite restore-feedback <on|off> - Toggle restoration progress feedback").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/antinetherite debris-info - Show information about stored Ancient Debris locations").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - Displays counts per world and current config status").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/antinetherite get <setting> - Get a configuration value").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/antinetherite set <setting> <value> - Set a configuration value").color(NamedTextColor.YELLOW));
        
        sender.sendMessage(Component.text("Available settings:").color(NamedTextColor.GOLD));
        
        sender.sendMessage(Component.text("Inventory settings:").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  clear, cancel-inventory-move, cancel-container-transfer").color(NamedTextColor.GRAY));
        
        sender.sendMessage(Component.text("Interaction settings:").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  cancel-craft, cancel-equip, cancel-attack").color(NamedTextColor.GRAY));
        
        sender.sendMessage(Component.text("Item handling settings:").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  cancel-pickup, remove-dropped").color(NamedTextColor.GRAY));

        sender.sendMessage(Component.text("Loot table settings:").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  loot-tables.remove-blocked-items, loot-tables.inherit-detection-items").color(NamedTextColor.GRAY));
        
        sender.sendMessage(Component.text("Ancient debris settings:").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  replace-when-mined, replace-on-chunk-load").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  only-replace-generated-chunks, ensure-chunks-loaded, save-replaced-locations").color(NamedTextColor.GRAY));

        sender.sendMessage(Component.text("Advanced settings:").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  ignore-creative-spectator, notify-players").color(NamedTextColor.GRAY));
        
        sender.sendMessage(Component.text("Detection settings:").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  detection.use-name-matching").color(NamedTextColor.GRAY));
        
        sender.sendMessage(Component.text("Timing settings:").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  delay, multiplier").color(NamedTextColor.GRAY));
        
        sender.sendMessage(Component.text("Special commands:").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/antinetherite get detection.items - List all Netherite items").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/antinetherite set detection.items add <item> - Add an item to the list").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/antinetherite set detection.items remove <item> - Remove an item from the list").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/antinetherite get loot-tables.items - List loot table blocked items").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/antinetherite set loot-tables.items add <item> - Add an item to the loot table block list").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/antinetherite set loot-tables.preserve-items add <item> - Preserve an item in generated loot").color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/antinetherite set loot-tables.preserve-items remove <item> - Stop preserving an item in generated loot").color(NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("reload");
            completions.add("restore-debris");
            completions.add("restore-feedback");
            completions.add("debris-info");
            completions.add("get");
            completions.add("set");
            return filterCompletions(completions, args[0]);
        }
        
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("restore-feedback")) {
                completions.add("on");
                completions.add("off");
                return filterCompletions(completions, args[1]);
            }
            
            if (args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("set")) {
                // Add global settings
                completions.add("global.enable-destructive-actions");
                
                // Add inventory settings
                completions.add("inventory.clear");
                completions.add("inventory.cancel-move");
                completions.add("inventory.cancel-container-transfer");
                
                // Add interaction settings
                completions.add("interaction.cancel-craft");
                completions.add("interaction.cancel-equip");
                completions.add("interaction.cancel-attack");
                
                // Add item handling settings
                completions.add("item-handling.cancel-pickup");
                completions.add("item-handling.remove-dropped");

                // Add loot table settings
                completions.add("loot-tables.remove-blocked-items");
                completions.add("loot-tables.inherit-detection-items");
                completions.add("loot-tables.items");
                completions.add("loot-tables.preserve-items");
                
                // Add ancient debris settings
                completions.add("ancient-debris.replace-when-mined");
                completions.add("ancient-debris.replace-on-chunk-load");
                completions.add("ancient-debris.only-replace-generated-chunks");
                completions.add("ancient-debris.ensure-chunks-loaded");
                completions.add("ancient-debris.save-replaced-locations");
                
                // Add performance settings
                completions.add("performance.restore-debris-on-disable");
                completions.add("performance.restore-debris-on-config-change");
                completions.add("performance.max-replacements-per-chunk");
                
                // Add advanced settings
                completions.add("advanced.max-locations-per-world");
                completions.add("advanced.command-cooldown-seconds");
                completions.add("advanced.log-debris-replacements");
                completions.add("advanced.log-inventory-removals");
                completions.add("advanced.ignore-creative-spectator");
                completions.add("advanced.notify-players");
                
                // Add detection settings
                completions.add("detection.use-name-matching");
                completions.add("detection.items");
                
                // Add timing settings
                completions.add("timing.delay");
                completions.add("timing.multiplier");
                
                return filterCompletions(completions, args[1]);
            }
            
            if (args[0].equalsIgnoreCase("restore-debris")) {
                for (World world : Bukkit.getWorlds()) {
                    completions.add(world.getName());
                }
                return filterCompletions(completions, args[1]);
            }
        }
        
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set")) {
                String setting = args[1].toLowerCase();
                
                // Special handling for list settings
                if (getListConfigPath(setting) != null) {
                    completions.add("add");
                    completions.add("remove");
                    return filterCompletions(completions, args[2]);
                }
                
                // For boolean settings
                if (setting.contains("cancel") || 
                    setting.contains("remove") || 
                    setting.contains("replace") || 
                    setting.contains("ensure") || 
                    setting.contains("restore") || 
                    setting.contains("log") || 
                    setting.contains("use-name") ||
                    setting.contains("inherit") ||
                    setting.contains("ignore") ||
                    setting.contains("notify") ||
                    setting.contains("save") ||
                    setting.contains("enable-destructive") ||
                    setting.contains("clear")) {
                    completions.add("true");
                    completions.add("false");
                    return filterCompletions(completions, args[2]);
                }
                
                // For integer settings
                if (setting.contains("delay") || 
                    setting.contains("multiplier") || 
                    setting.contains("max-")) {
                    // Suggest some reasonable values
                    if (setting.contains("delay")) {
                        completions.add("1");
                        completions.add("5");
                        completions.add("10");
                    } else if (setting.contains("multiplier")) {
                        completions.add("10");
                        completions.add("20");
                        completions.add("40");
                    } else if (setting.contains("max-replacements")) {
                        completions.add("25");
                        completions.add("50");
                        completions.add("100");
                    } else if (setting.contains("max-locations")) {
                        completions.add("5000");
                        completions.add("10000");
                        completions.add("20000");
                    } else if (setting.contains("cooldown")) {
                        completions.add("1");
                        completions.add("5");
                        completions.add("10");
                    }
                    return filterCompletions(completions, args[2]);
                }
            }
        }
        
        return completions;
    }

    /**
     * Handles the /antinetherite restore-feedback command
     * @param sender The command sender
     * @param args The command arguments
     * @return true if the command was handled, false otherwise
     */
    private boolean handleRestoreFeedback(CommandSender sender, String[] args) {
        // Only players can opt in/out of feedback
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /antinetherite restore-feedback <on|off>").color(NamedTextColor.RED));
            return true;
        }
        
        String action = args[1].toLowerCase();
        
        if (action.equals("on")) {
            plugin.getRestorationProgressTracker().setGlobalOptOut(player.getUniqueId(), false);
            sender.sendMessage(Component.text("Restoration progress feedback enabled.").color(NamedTextColor.GREEN));
        } else if (action.equals("off")) {
            plugin.getRestorationProgressTracker().setGlobalOptOut(player.getUniqueId(), true);
            sender.sendMessage(Component.text("Restoration progress feedback disabled.").color(NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Usage: /antinetherite restore-feedback <on|off>").color(NamedTextColor.RED));
        }
        
        return true;
    }

    /**
     * Handles the /antinetherite get command
     * @param sender The command sender
     * @param args The command arguments
     * @return true if the command was handled, false otherwise
     */
    private boolean handleGetCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /antinetherite get <setting>").color(NamedTextColor.RED));
            return true;
        }
        
        String setting = args[1].toLowerCase();
        
        // Handle special case for list settings
        String listPath = getListConfigPath(setting);
        if (listPath != null) {
            List<String> items = getStringList(listPath);
            sender.sendMessage(Component.text(setting + ":").color(NamedTextColor.GREEN));
            for (String item : items) {
                sender.sendMessage(Component.text("- " + item).color(NamedTextColor.YELLOW));
            }
            return true;
        }
        
        // Map setting to config path
        String configPath = getConfigPath(setting);
        if (configPath == null) {
            sender.sendMessage(Component.text("Unknown setting: " + setting).color(NamedTextColor.RED));
            return true;
        }
        
        // Get the value
        Object value = plugin.getConfigValue(configPath);
        if (value == null) {
            sender.sendMessage(Component.text("Setting not found: " + setting).color(NamedTextColor.RED));
            return true;
        }
        
        sender.sendMessage(Component.text(setting + " = " + value).color(NamedTextColor.GREEN));
        return true;
    }
    
    /**
     * Maps a setting name to a config path
     * @param setting The setting name
     * @return The config path, or null if not found
     */
    private String getConfigPath(String setting) {
        switch (setting) {
            // Global settings
            case "global.enable-destructive-actions":
                return "anti-netherite.global.enable-destructive-actions";
                
            // Inventory settings
            case "inventory.clear":
            case "clear":
                return "anti-netherite.inventory.clear";
            case "inventory.cancel-move":
            case "cancel-inventory-move":
                return "anti-netherite.inventory.cancel-move";
            case "inventory.cancel-container-transfer":
            case "cancel-container-transfer":
                return "anti-netherite.inventory.cancel-container-transfer";
                
            // Interaction settings
            case "interaction.cancel-craft":
            case "cancel-craft":
                return "anti-netherite.interaction.cancel-craft";
            case "interaction.cancel-equip":
            case "cancel-equip":
                return "anti-netherite.interaction.cancel-equip";
            case "interaction.cancel-attack":
            case "cancel-attack":
                return "anti-netherite.interaction.cancel-attack";
                
            // Item handling settings
            case "item-handling.cancel-pickup":
            case "cancel-pickup":
                return "anti-netherite.item-handling.cancel-pickup";
            case "item-handling.remove-dropped":
            case "remove-dropped":
                return "anti-netherite.item-handling.remove-dropped";

            // Loot table settings
            case "loot-tables.remove-blocked-items":
            case "remove-loot-table-items":
                return "anti-netherite.loot-tables.remove-blocked-items";
            case "loot-tables.inherit-detection-items":
            case "loot-table-inherit-detection-items":
                return "anti-netherite.loot-tables.inherit-detection-items";
                
            // Ancient debris settings
            case "ancient-debris.replace-when-mined":
            case "replace-when-mined":
                return "anti-netherite.ancient-debris.replace-when-mined";
            case "ancient-debris.replace-on-chunk-load":
            case "replace-on-chunk-load":
                return "anti-netherite.ancient-debris.replace-on-chunk-load";
            case "ancient-debris.only-replace-generated-chunks":
            case "only-replace-generated-chunks":
                return "anti-netherite.ancient-debris.only-replace-generated-chunks";
            case "ancient-debris.ensure-chunks-loaded":
            case "ensure-chunks-loaded":
                return "anti-netherite.ancient-debris.ensure-chunks-loaded";
            case "ancient-debris.save-replaced-locations":
            case "save-replaced-locations":
                return "anti-netherite.ancient-debris.save-replaced-locations";
                
            // Performance settings
            case "performance.restore-debris-on-disable":
            case "restore-debris-on-disable":
                return "anti-netherite.performance.restore-debris-on-disable";
            case "performance.restore-debris-on-config-change":
            case "restore-debris-on-config-change":
                return "anti-netherite.performance.restore-debris-on-config-change";
            case "performance.max-replacements-per-chunk":
            case "max-replacements-per-chunk":
                return "anti-netherite.performance.max-replacements-per-chunk";
                
            // Advanced settings
            case "advanced.max-locations-per-world":
            case "max-locations-per-world":
                return "anti-netherite.advanced.max-locations-per-world";
            case "advanced.command-cooldown-seconds":
            case "command-cooldown-seconds":
                return "anti-netherite.advanced.command-cooldown-seconds";
            case "advanced.log-debris-replacements":
            case "log-debris-replacements":
                return "anti-netherite.advanced.log-debris-replacements";
            case "advanced.log-inventory-removals":
            case "log-inventory-removals":
                return "anti-netherite.advanced.log-inventory-removals";
            case "advanced.ignore-creative-spectator":
            case "ignore-creative-spectator":
                return "anti-netherite.advanced.ignore-creative-spectator";
            case "advanced.notify-players":
            case "notify-players":
                return "anti-netherite.advanced.notify-players";
                
            // Detection settings
            case "detection.use-name-matching":
            case "use-name-matching":
                return "anti-netherite.detection.use-name-matching";
                
            // Timing settings
            case "timing.delay":
            case "delay":
                return "anti-netherite.timing.delay";
            case "timing.multiplier":
            case "multiplier":
                return "anti-netherite.timing.multiplier";
                
            default:
                return null;
        }
    }

    /**
     * Filters tab completions based on the input
     * @param completions The list of completions
     * @param input The input to filter by
     * @return The filtered list of completions
     */
    private List<String> filterCompletions(List<String> completions, String input) {
        List<String> filtered = new ArrayList<>();
        for (String completion : completions) {
            if (completion.toLowerCase().startsWith(input.toLowerCase())) {
                filtered.add(completion);
            }
        }
        return filtered;
    }

    private boolean allowsUnlimitedValue(String settingPath) {
        return settingPath.equals("anti-netherite.performance.max-replacements-per-chunk")
                || settingPath.equals("anti-netherite.advanced.max-locations-per-world");
    }

    private String getListConfigPath(String setting) {
        switch (setting) {
            case "detection.items":
                return "anti-netherite.detection.items";
            case "loot-tables.items":
                return "anti-netherite.loot-tables.items";
            case "loot-tables.preserve-items":
                return "anti-netherite.loot-tables.preserve-items";
            default:
                return null;
        }
    }

    /**
     * Safely retrieves a string list from the config, ensuring type safety.
     * Filters out any non-string entries and logs warnings if the data type is unexpected.
     * @return a mutable List<String> of config entries (never null)
     */
    private List<String> getStringList(String path) {
        Object value = plugin.getConfigValue(path);
        List<String> result = new ArrayList<>();
        if (value instanceof List<?>) {
            for (Object o : (List<?>) value) {
                if (o instanceof String s) {
                    result.add(s);
                } else if (o != null) {
                    plugin.getLogger().warning("Non-string entry in " + path + ": " + o);
                }
            }
        } else if (value != null) {
            plugin.getLogger().warning(path + " config value is not a list: " + value.getClass().getName());
        }
        return result;
    }
}

package cyansraiding.cyanssimpleraiding;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import static org.bukkit.Bukkit.getLogger;

@SuppressWarnings("ALL")
public class BlockHealthListener implements Listener {
    private final Map<String, Double> blockHealth = new HashMap<>();
    private final Map<String, UUID> blockOwners = new HashMap<>();
    private final Set<UUID> adminOverrides = new HashSet<>();
    private final Map<String, Long> blockBreakTimes = new HashMap<>();
    private final Map<UUID, ItemStack[]> blockContentsBefore = new HashMap<>();
    private final Map<String, UUID> lastPlayerToLowerHealth = new HashMap<>();
    private final Map<String, UUID> lastPlayerToOpen = new HashMap<>();
    private final Map<UUID, List<UUID>> ownerTrustRelationships = new HashMap<>();
    private File dataFolder;
    private String locationKey(Location location) {
        return Objects.requireNonNull(location.getWorld()).getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        // Use a new ArrayList to avoid ConcurrentModificationException
        List<Block> blocksToRemove = new ArrayList<>();
        for (Block block : event.blockList()) {
            String locKey = locationKey(block.getLocation());
            if (block.getState() instanceof Container && blockHealth.containsKey(locKey)) {
                blocksToRemove.add(block);
            }
        }
        // Remove all identified chest blocks from the event's block list
        event.blockList().removeAll(blocksToRemove);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        List<Block> blocksToRemove = new ArrayList<>();
        for (Block block : event.blockList()) {
            String locKey = locationKey(block.getLocation());
            if (block.getState() instanceof Container && blockHealth.containsKey(locKey)) {
                blocksToRemove.add(block);
            }
        }
        event.blockList().removeAll(blocksToRemove);
    }

    public void setDataFolder(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void loadBlockData() {
        if (dataFolder == null) return; // Make sure dataFolder has been set

        File dataFile = new File(dataFolder, "blockData.yml");
        if (!dataFile.exists()) {
            getLogger().info("[CyansSimpleRaiding] Block data file does not exist. Skipping...");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        // Existing blocks loading logic
        ConfigurationSection blocksSection = config.getConfigurationSection("blocks");
        if (blocksSection != null) {
            // Clear previous data
            blockOwners.clear();
            blockHealth.clear();
            lastPlayerToLowerHealth.clear();
            lastPlayerToOpen.clear();
            ownerTrustRelationships.clear();

            for (String key : blocksSection.getKeys(false)) {
                ConfigurationSection blockSection = blocksSection.getConfigurationSection(key);
                if (blockSection == null) continue;
                // Owner
                String ownerStr = blockSection.getString("owner");
                if (ownerStr != null) {
                    UUID ownerUUID = UUID.fromString(ownerStr);
                    blockOwners.put(key, ownerUUID);
                }

                // Health
                double health = blockSection.getDouble("health", -1.0); // Use a default value that indicates "not set"
                if (health != -1.0) {
                    blockHealth.put(key, health);
                }

                // Last player to lower health
                String lastToLowerHealthStr = blockSection.getString("lastToLowerHealth");
                if (lastToLowerHealthStr != null) {
                    UUID lastToLowerHealthUUID = UUID.fromString(lastToLowerHealthStr);
                    lastPlayerToLowerHealth.put(key, lastToLowerHealthUUID);
                }

                // Last player to open
                String lastToOpenStr = blockSection.getString("lastToOpen");
                if (lastToOpenStr != null) {
                    UUID lastToOpenUUID = UUID.fromString(lastToOpenStr);
                    lastPlayerToOpen.put(key, lastToOpenUUID);
                }
            }
        } else {
            getLogger().info("[CyansSimpleRaiding] No blocks data to read. Skipping...");
        }

        // New logic for loading owner trust relationships
        ConfigurationSection ownersSection = config.getConfigurationSection("ownerTrustRelationships");
        if (ownersSection != null) {
            ownerTrustRelationships.clear(); // Clear any previously loaded relationships

            for (String ownerKey : ownersSection.getKeys(false)) {
                List<String> trustedPlayersStr = ownersSection.getStringList(ownerKey);
                if (!trustedPlayersStr.isEmpty()) {
                    List<UUID> trustedPlayersUUID = new ArrayList<>();
                    for (String uuidStr : trustedPlayersStr) {
                        trustedPlayersUUID.add(UUID.fromString(uuidStr));
                    }
                    ownerTrustRelationships.put(UUID.fromString(ownerKey), trustedPlayersUUID);
                }
            }
        } else {
            getLogger().info("[CyansSimpleRaiding] No owner trust relationships data to read. Skipping...");
        }

        getLogger().info("[CyansSimpleRaiding] Successfully loaded all data.");
    }

    private Location parseLocationKey(String locKey) {
        String[] parts = locKey.split(",");
        if (parts.length != 4) {
            getLogger().warning("[CyansSimpleRaiding] Invalid location format: " + locKey);
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            getLogger().warning("[CyansSimpleRaiding] World not found: " + parts[0]);
            return null;
        }
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            getLogger().warning("[CyansSimpleRaiding] Number format exception for location: " + locKey);
            return null;
        }
    }

    public void saveBlockData() {
        if (dataFolder == null) return; // Make sure dataFolder has been set

        File dataFile = new File(dataFolder, "blockData.yml");
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, UUID> entry : blockOwners.entrySet()) {
            String locKey = entry.getKey();
            Location loc = parseLocationKey(locKey);
            if (loc != null) {
                Block block = loc.getBlock();
                if (block.getState() instanceof Container) {
                    String basePath = "blocks." + locKey + ".";
                    config.set(basePath + "owner", entry.getValue().toString());

                    Double health = blockHealth.get(locKey);
                    if (health != null) {
                        config.set(basePath + "health", health);
                    }

                    // Save the last player to lower health
                    UUID lastLower = lastPlayerToLowerHealth.get(entry.getKey());
                    if (lastLower != null) {
                        config.set(basePath + "lastToLowerHealth", lastLower.toString());
                    }

                    // Save the last player to open
                    UUID lastOpen = lastPlayerToOpen.get(entry.getKey());
                    if (lastOpen != null) {
                        config.set(basePath + "lastToOpen", lastOpen.toString());
                    }
                }
            }
        }

        if (!ownerTrustRelationships.isEmpty()) {
            for (Map.Entry<UUID, List<UUID>> entry : ownerTrustRelationships.entrySet()) {
                List<String> trustedPlayerStrings = entry.getValue().stream()
                        .map(UUID::toString)
                        .collect(Collectors.toList());
                config.set("ownerTrustRelationships." + entry.getKey().toString(), trustedPlayerStrings);
            }
        }
        try {
            config.save(dataFile);
            getLogger().info("[CyansSimpleRaiding] Successfully saved block data of containers.");
        } catch (IOException e) {
            // Replace printStackTrace with logging
            getLogger().severe("[CyansSimpleRaiding] Could not save block data to blockData.yml!");
            getLogger().severe(e.toString()); // Log the exception message
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();

        if (block.getState() instanceof Chest) {
            Chest chest = (Chest) block.getState();
            Chest adjacentChest = findAdjacentChest(chest);

            String blockLocKey = locationKey(block.getLocation());
            UUID playerUUID = event.getPlayer().getUniqueId();
            blockHealth.put(blockLocKey, 100.0); // Default health for a single chest
            blockOwners.put(blockLocKey, playerUUID);

            if (adjacentChest != null) {
                String adjacentLocKey = locationKey(adjacentChest.getLocation());
                UUID adjacentChestOwner = blockOwners.get(adjacentLocKey);

                // Correctly get the list of trusted players for the OWNER of the adjacent chest
                List<UUID> trustedPlayers = ownerTrustRelationships.getOrDefault(adjacentChestOwner, new ArrayList<>());

                if (!playerUUID.equals(adjacentChestOwner) && !trustedPlayers.contains(playerUUID)) {
                    // Player is not the owner and is not a trusted player, cancel the event
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("[§9§lCSR§r§f] Only trusted players can combine chests.");
                } else {
                    // Either the player is the owner or a trusted player, allow the chest to combine
                    blockHealth.put(adjacentLocKey, 100.0);
                    return;
                }
            }
        }

        if (block.getState() instanceof Container) {
            Container container = (Container) block.getState();
            String blockLocKey = locationKey(container.getLocation());

            blockHealth.put(blockLocKey, 100.0);
            blockOwners.put(blockLocKey, event.getPlayer().getUniqueId());
        }
    }

    private Chest findAdjacentChest(Chest chest) {
        BlockFace[] faces = new BlockFace[]{ BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST };
        for (BlockFace face : faces) {
            Block relative = chest.getBlock().getRelative(face);
            if (relative.getState() instanceof Chest) {
                return (Chest) relative.getState();
            }
        }
        return null;
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        InventoryHolder holder = event.getInventory().getHolder();

        // Early return if the holder is neither a Container nor a DoubleChest.
        if (!(holder instanceof Container || holder instanceof DoubleChest)) return;

        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        ItemStack[] contents = event.getInventory().getContents().clone();
        blockContentsBefore.put(event.getPlayer().getUniqueId(), contents);

        String locKey = null;

            // Handle DoubleChest specifically
            if (holder instanceof DoubleChest) {
                Inventory inventory = event.getInventory();
                DoubleChest doubleChest = (DoubleChest) holder;
                if (isBlockOnCooldown(locKey, playerUUID)) {
                    event.setCancelled(true);
                    return; // Stop processing if the block is on cooldown
                }
                Chest leftChest = (Chest) doubleChest.getLeftSide();
                assert leftChest != null;
                locKey = locationKey(leftChest.getLocation());
                processContainerAccess(event, locKey, playerUUID);
                return;
            }
            // Single chest or other container types
            Container container = (Container) holder;
            locKey = locationKey(container.getLocation());
        if (isBlockOnCooldown(locKey, playerUUID)) {
            event.setCancelled(true);
            return; // Stop processing if the block is on cooldown
        }
            processContainerAccess(event, locKey, playerUUID);
    }

    private void processContainerAccess(InventoryOpenEvent event, String locKey, UUID playerUUID) {
        if (adminOverrides.contains(playerUUID)) {
            return; // Admin override allows immediate access
        }

        UUID ownerId = blockOwners.get(locKey);
        if (ownerId == null) {
            return;
        }
        if (ownerId != null) {
            List<UUID> trustedPlayers = ownerTrustRelationships.getOrDefault(ownerId, new ArrayList<>());
            if (ownerId.equals(playerUUID) || trustedPlayers.contains(playerUUID)) {
                lastPlayerToOpen.put(locKey, playerUUID);
                return;
            }
        }
        // If we reach here, the player is neither the owner nor trusted.
        event.setCancelled(true);
        event.getPlayer().sendMessage("[§9§lCSR§r§f] You do not have permission to open this container.");
    }

    public boolean isBlockOnCooldown(String locKey, UUID playerUUID) {
        Long breakTime = blockBreakTimes.get(locKey);
        if (breakTime == null) {
            return false; // No cooldown recorded, chest can be opened
        }
        long currentTime = System.currentTimeMillis();
        long timeSinceBreak = currentTime - breakTime;
        long cooldownDuration = 40000; // 40 seconds cooldown
        if (timeSinceBreak < cooldownDuration) {
            Player player = Bukkit.getPlayer(playerUUID); // Retrieve the player from UUID
            if (player != null) {
                long timeLeft = (cooldownDuration - timeSinceBreak) / 1000; // Convert to seconds
                player.sendMessage("[§9§lCSR§r§f] This container is being§c raided!§r " + timeLeft + " seconds left.");
            }
            return true;
        } else {
            blockBreakTimes.remove(locKey); // Cooldown expired, remove entry
            return false;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Container || holder instanceof DoubleChest)) return;

        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        ItemStack[] contentsBefore = blockContentsBefore.getOrDefault(playerUUID, new ItemStack[0]);
        ItemStack[] contentsAfter = event.getInventory().getContents();

        boolean hasChanged = !Arrays.deepEquals(contentsBefore, contentsAfter); // Correctly comparing deep contents

        if (hasChanged) {
            String locKey;
            Inventory inventory = event.getInventory();
            if (holder instanceof DoubleChest) {
                    DoubleChest doubleChest = (DoubleChest) holder;
                    Chest leftChest = (Chest) doubleChest.getLeftSide();
                    Chest rightChest = (Chest) doubleChest.getRightSide();
                    // Now choose one side (left or right) to represent the double chest's location
                assert leftChest != null;
                locKey = locationKey(leftChest.getLocation());
                    // Ensure your locationKey method and update logic handles this correctly
                updateBlockHealthAndNotifyPlayer(inventory, player, locKey);

                blockContentsBefore.remove(playerUUID); // Clearing the record for this player
                return;
            }
            locKey = locationKey(((Container) holder).getLocation());
            updateBlockHealthAndNotifyPlayer(inventory, player, locKey);
        }

        blockContentsBefore.remove(playerUUID); // Clearing the record for this player
    }

    private void updateBlockHealthAndNotifyPlayer(Inventory inventory, Player player, String locKey) {
        InventoryHolder holder = inventory.getHolder();
        // Directly calculate the total health based on current contents.
        double newTotalHealth = 100.0 + calculateHealthBasedOnContents(inventory); // Starts fresh from base health

        if (holder instanceof DoubleChest) {
            DoubleChest doubleChest = (DoubleChest) holder;
            Chest leftChest = (Chest) doubleChest.getLeftSide();
            Chest rightChest = (Chest) doubleChest.getRightSide();

            assert leftChest != null;
            String leftLocKey = locationKey(leftChest.getLocation());
            assert rightChest != null;
            String rightLocKey = locationKey(rightChest.getLocation());

            blockHealth.put(leftLocKey, newTotalHealth);
            blockHealth.put(rightLocKey, newTotalHealth);
            player.sendMessage(String.format("[§9§lCSR§r§f] Double chest health updated: §a%.2f%%.", newTotalHealth));
        } else {
            blockHealth.put(locKey, newTotalHealth);
            player.sendMessage(String.format("[§9§lCSR§r§f] Container health updated: §a%.2f%%.", newTotalHealth));
        }
    }

    private double calculateHealthBasedOnContents(Inventory inventory) {
        double healthIncrease = 0.0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null) {
                healthIncrease += getIncreaseModifier(item.getType()) * item.getAmount();
            }
        }
        return healthIncrease;
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        InventoryHolder sourceHolder = event.getSource().getHolder();
        InventoryHolder destinationHolder = event.getDestination().getHolder();

        if (sourceHolder instanceof Container && destinationHolder instanceof Hopper) {
            // Item being pulled from a container into a hopper
            handleHopperInteraction((Container) sourceHolder, (Hopper) destinationHolder, event);
        } else if (sourceHolder instanceof Hopper && destinationHolder instanceof Container) {
            // Item being pushed from a hopper into a container
            handleHopperInteraction((Container) destinationHolder, (Hopper) sourceHolder, event);
        }
    }

    private void handleHopperInteraction(Container container, Hopper hopper, InventoryMoveItemEvent event) {
        Block containerBlock = container.getBlock();
        Block hopperBlock = hopper.getBlock();
        String containerLocKey = locationKey(containerBlock.getLocation());
        String hopperLocKey = locationKey(hopperBlock.getLocation());

        UUID containerOwner = blockOwners.get(containerLocKey);
        UUID hopperOwner = blockOwners.get(hopperLocKey);

        // If either the container or the hopper doesn't have an owner, or their owners are different, cancel the event
        if (containerOwner == null || !containerOwner.equals(hopperOwner)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        String locKey = locationKey(block.getLocation());
        UUID ownerId = blockOwners.get(locKey);

        if (block.getType() == Material.CHEST) {
            Chest chest = (Chest) block.getState();
            Chest adjacentChest = findAdjacentChest(chest);

            if (adminOverrides.contains(player.getUniqueId())) {
                return;
            }

            if (ownerId == null) return; // No owner, no action needed

            List<UUID> trustedPlayers = ownerTrustRelationships.getOrDefault(ownerId, new ArrayList<>());

            if (ownerId.equals(player.getUniqueId()) || trustedPlayers.contains(player.getUniqueId())) {
                removeBlockProtectionData(locKey);
                return;
            }

            double currentHealth = blockHealth.getOrDefault(locKey, 100.0);
            double newHealth = currentHealth - 20; // Standard health reduction

            if (adjacentChest != null) {
                // Adjust for double chest, assuming you want to halve the decrement for each
                newHealth = currentHealth - 20; // Adjust so total reduction is spread across both parts
            }

            if (newHealth <= 0) {
                // Break Block
                blockHealth.remove(locKey);

                if (adjacentChest != null) {
                    // Handle the adjacent part of the double chest similarly
                    dropItemsAndBlock(adjacentChest.getLocation(), adjacentChest.getInventory());
                    blockHealth.remove(locationKey(adjacentChest.getLocation()));
                    adjacentChest.getBlock().setType(Material.AIR);
                }
                removeBlockProtectionData(locKey);
            } else {
                // Update Health and Block
                blockHealth.put(locKey, newHealth);
                if (adjacentChest != null) {
                    String adjacentLocKey = locationKey(adjacentChest.getLocation());
                    blockHealth.put(adjacentLocKey, newHealth);
                }
                lastPlayerToLowerHealth.put(locKey, player.getUniqueId());

                blockBreakTimes.put(locKey, System.currentTimeMillis());
                handleBlockHealthReduction(locKey, player, currentHealth);

                event.setCancelled(true); // Prevent default breaking
            }
            return;
        }

        if (block.getState() instanceof Container) {
            if (adminOverrides.contains(player.getUniqueId())) {
                // Your existing logic for handling admin overrides
                return;
            }

            if (ownerId == null) {
                return; // Exit to avoid NullPointerException
            }

            List<UUID> trustedPlayers = ownerTrustRelationships.getOrDefault(ownerId, new ArrayList<>());

            if (ownerId.equals(player.getUniqueId()) || trustedPlayers.contains(player.getUniqueId())) {
                removeBlockProtectionData(locKey);
                return;
            }
            double currentHealth = blockHealth.getOrDefault(locKey, 100.0);
            double newHealth = currentHealth - 20;

            if (blockHealth.containsKey(locKey)) {

                if (newHealth > 0) {
                    blockHealth.put(locKey, newHealth);
                    lastPlayerToLowerHealth.put(locKey, player.getUniqueId());
                    blockBreakTimes.put(locKey, System.currentTimeMillis());
                    handleBlockHealthReduction(locKey, player, currentHealth);

                    event.setCancelled(true);
                } else {
                    removeBlockProtectionData(locKey);
                }
            }
        }
    }

    private void dropItemsAndBlock(Location location, Inventory inventory) {
        World world = location.getWorld();
        if (world == null) return; // Just in case

        // Drop items from the inventory first
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                world.dropItemNaturally(location, item);
            }
        }
        inventory.clear(); // Clear inventory after dropping items

        // Now, drop the chest block itself
        dropChestBlock(location, world);
    }

    private void dropChestBlock(Location location, World world) {
        // Assuming you're working with generic chests. Adjust if you're using trapped chests, etc.
        world.dropItemNaturally(location, new ItemStack(Material.CHEST, 1));
    }

    private void handleBlockHealthReduction(String locKey, Player player, double currentHealth) {
        double newHealth = currentHealth - 20; // Adjust as needed

        if (newHealth > 0) {
            blockHealth.put(locKey, newHealth);
            // Update other related mappings as necessary
            lastPlayerToLowerHealth.put(locKey, player.getUniqueId());
            blockBreakTimes.put(locKey, System.currentTimeMillis());
            player.sendMessage(String.format("[§9§lCSR§r§f] Container now has §a%.2f%%§r health left.", newHealth));
        } else {
            removeBlockProtectionData(locKey);
        }
    }

    private void removeBlockProtectionData(String locKey) {
        blockHealth.remove(locKey);
        blockOwners.remove(locKey);
        lastPlayerToLowerHealth.remove(locKey);
        lastPlayerToOpen.remove(locKey);
        blockBreakTimes.remove(locKey);
    }

    private double getIncreaseModifier(Material material) {
        switch (material) {
            //Netherite
            case NETHERITE_HELMET:
            case NETHERITE_CHESTPLATE:
            case NETHERITE_LEGGINGS:
            case NETHERITE_BOOTS:
            case NETHERITE_BLOCK:
                return 25.0;
            case NETHERITE_SWORD:
            case NETHERITE_AXE:
            case NETHERITE_SHOVEL:
            case NETHERITE_PICKAXE:
            case NETHERITE_HOE:
                //Diamond Block
            case DIAMOND_BLOCK:
                return 20.0;
            //Netherite
            case NETHERITE_INGOT:
                return 15.0;
            case NETHERITE_SCRAP:
                return 12.0;
            //Diamond
            case DIAMOND_HELMET:
            case DIAMOND_CHESTPLATE:
            case DIAMOND_LEGGINGS:
            case DIAMOND_SWORD:
            case DIAMOND_AXE:
            case DIAMOND_SHOVEL:
            case DIAMOND_PICKAXE:
                return 15.0;
            case DIAMOND_BOOTS:
            case DIAMOND_HOE:
                return 12.0;
            case DIAMOND:
                //Copper Block
            case COPPER_BLOCK:
                return 10.0;
            //Iron
            case IRON_HELMET:
            case IRON_CHESTPLATE:
            case IRON_LEGGINGS:
            case IRON_PICKAXE:
            case IRON_AXE:
            case IRON_SHOVEL:
            case IRON_SWORD:
            case IRON_BLOCK:
                return 10.0;
            case IRON_HOE:
            case IRON_BOOTS:
                return 8.0;
            case IRON_INGOT:
            case RAW_IRON:
                //Copper
            case COPPER_ORE:
            case RAW_COPPER:
                return 3.0;
            //Gold
            case GOLDEN_AXE: //Fastest axe to break chests
                return 8.0;
            case GOLDEN_HELMET:
            case GOLDEN_CHESTPLATE:
            case GOLDEN_LEGGINGS:
            case GOLDEN_BOOTS:
            case GOLDEN_SWORD:
            case GOLDEN_PICKAXE:
            case GOLDEN_HOE:
            case GOLDEN_SHOVEL:
                return 5.0;
            case GOLD_INGOT:
                return 2.0;
            case LAPIS_BLOCK:
                return 2.0;
            case LAPIS_LAZULI:
                return 1.0;
            //Other Resources
            case COBBLESTONE:
            case DIRT:
                return 0.01;
            //Misc
            case ELYTRA:
                return 25.0;
            case SHULKER_BOX:
            case ENCHANTED_GOLDEN_APPLE:
                return 15.0;
            case EMERALD:
                return 5.0;
            case GOLDEN_APPLE:
                return 6.0;
            case ENDER_EYE:
            case BOOK:
                return 4.0;
            case LEATHER:
                return 1.0;
            default:
                return 0.0;
        }
    }

    @EventHandler
    public void onPlayerInteractWithChest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != Material.CHEST) return;

        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Container)) return;

        event.setCancelled(true);

        String locKey = locationKey(block.getLocation());
        Double health = blockHealth.getOrDefault(locKey, 100.0);
        UUID ownerId = blockOwners.get(locKey);
        UUID lastDamageCauserId = lastPlayerToLowerHealth.get(locKey);
        UUID lastOpenId = lastPlayerToOpen.get(locKey);

        String ownerName = ownerId != null ? Bukkit.getOfflinePlayer(ownerId).getName() : "Unknown";
        String lastDamageCauserName = lastDamageCauserId != null ? Bukkit.getOfflinePlayer(lastDamageCauserId).getName() : "None";
        String lastOpenName = lastOpenId != null ? Bukkit.getOfflinePlayer(lastOpenId).getName() : "None";

        player.sendMessage("[§9§lCSR§r§f] §lContainer Info:");
        player.sendMessage("§lHealth:§r§a " + health +"%");
        player.sendMessage("§lOwner:§r " + ownerName);
        player.sendMessage("§lLast Player to Break:§r " + lastDamageCauserName);
        player.sendMessage("§lLast Player to Open:§r " + lastOpenName);
    }

    public boolean addPlayerToGlobalTrustList(Player owner, UUID trustedPlayerUUID) {
        UUID ownerId = owner.getUniqueId();
        List<UUID> trustedPlayers = ownerTrustRelationships.getOrDefault(ownerId, new ArrayList<>());

        if (!trustedPlayers.contains(trustedPlayerUUID)) {
            trustedPlayers.add(trustedPlayerUUID);
            ownerTrustRelationships.put(ownerId, trustedPlayers);
            return true; // Indicate that the player was successfully added
        }
        return false; // Player was already trusted
    }

    public boolean removePlayerFromGlobalTrustList(Player owner, UUID untrustedPlayerUUID) {
        UUID ownerId = owner.getUniqueId();
        List<UUID> trustedPlayers = ownerTrustRelationships.getOrDefault(ownerId, new ArrayList<>());

        if (trustedPlayers.contains(untrustedPlayerUUID)) {
            trustedPlayers.remove(untrustedPlayerUUID);
            ownerTrustRelationships.put(ownerId, trustedPlayers);
            return true; // Indicate that the player was successfully removed
        }
        return false; // Player was not trusted
    }

    public void toggleCsrAdmin(Player player) {
        UUID playerUuid = player.getUniqueId();
        if (adminOverrides.contains(playerUuid)) {
            adminOverrides.remove(playerUuid);
            player.sendMessage("[§9§lCSR§r§c§lA§r§f] Admin mode disabled.");
        } else {
            adminOverrides.add(playerUuid);
            player.sendMessage("[§9§lCSR§r§c§lA§r§f] Admin mode enabled.");
        }
    }
}